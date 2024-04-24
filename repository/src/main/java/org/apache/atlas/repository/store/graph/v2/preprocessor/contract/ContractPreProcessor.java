package org.apache.atlas.repository.store.graph.v2.preprocessor.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.atlas.RequestContext;
import org.apache.atlas.discovery.EntityDiscoveryService;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.instance.AtlasStruct;
import org.apache.atlas.model.instance.EntityMutations;
import org.apache.atlas.repository.graphdb.AtlasGraph;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.repository.store.graph.AtlasEntityStore;
import org.apache.atlas.repository.store.graph.v2.*;
import org.apache.atlas.type.AtlasEntityType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import static org.apache.atlas.AtlasErrorCode.*;
import static org.apache.atlas.repository.Constants.ATTR_CERTIFICATE_STATUS;
import static org.apache.atlas.repository.Constants.QUALIFIED_NAME;
import static org.apache.atlas.repository.Constants.ATTR_CONTRACT;
import static org.apache.atlas.type.AtlasTypeUtil.getAtlasObjectId;

public class ContractPreProcessor extends AbstractContractPreProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ContractPreProcessor.class);
    public static final String ATTR_VERSION = "dataContractVersion";
    public static final String ATTR_ASSET_GUID = "dataContractAssetGuid";
    public static final String REL_ATTR_GOVERNED_ASSET = "dataContractAssetLatest";
    public static final String REL_ATTR_GOVERNED_ASSET_CERTIFIED = "dataContractAssetCertified";
    public static final String REL_ATTR_PREVIOUS_VERSION = "dataContractPreviousVersion";
    public static final String ASSET_ATTR_HAS_CONTRACT = "hasContract";
    public static final String ASSET_ATTR_DESCRIPTION = "description";

    public static final String CONTRACT_QUALIFIED_NAME_SUFFIX = "contract";
    public static final String VERSION_PREFIX = "version";
    public static final String CONTRACT_ATTR_STATUS = "status";
    private final AtlasEntityStore entityStore;
    private final boolean storeDifferentialAudits;
    private EntityDiscoveryService discovery;



    public ContractPreProcessor(AtlasGraph graph, AtlasTypeRegistry typeRegistry,
                                EntityGraphRetriever entityRetriever, AtlasEntityStore entityStore,
                                boolean storeDifferentialAudits, EntityDiscoveryService discovery) {

        super(graph, typeRegistry, entityRetriever);
        this.storeDifferentialAudits = storeDifferentialAudits;
        this.entityStore = entityStore;
        this.discovery = discovery;
    }

    @Override
    public void processAttributes(AtlasStruct entityStruct, EntityMutationContext context, EntityMutations.EntityOperation operation) throws AtlasBaseException {
        AtlasEntity entity = (AtlasEntity) entityStruct;
        switch (operation) {
            case CREATE:
                processCreateContract(entity, context);
                break;
            case UPDATE:
                // Updating an existing version of the contract
                processUpdateContract(entity, context);
        }

    }

    private void processUpdateContract(AtlasEntity entity, EntityMutationContext context) throws AtlasBaseException {
        String contractString = (String) entity.getAttribute(ATTR_CONTRACT);
        AtlasVertex vertex = context.getVertex(entity.getGuid());
        AtlasEntity existingContractEntity = entityRetriever.toAtlasEntity(vertex);
        // TODO: Check for qualifiedName to understand if a particular version is getting updated or duplicate contract in payload
        if (!isEqualContract(contractString, (String) existingContractEntity.getAttribute(ATTR_CONTRACT))) {
            // Update the same asset(entity)
            throw new AtlasBaseException(OPERATION_NOT_SUPPORTED, "Can't update a specific version of contract");
        }
        // Add cases for update in status field and certificateStatus
    }
    private void processCreateContract(AtlasEntity entity, EntityMutationContext context) throws AtlasBaseException {
        /*
          Low-level Design
               | Authorization
               | Deserialization of the JSON
               ---| Validation of spec
               | Validation of contract
               | Create Version
               | Create Draft
               ---| asset to contract sync
               | Create Publish
               ---| two-way sync of attribute
         */

        String contractQName = (String) entity.getAttribute(QUALIFIED_NAME);
        validateAttribute(!contractQName.endsWith(String.format("/%s", CONTRACT_QUALIFIED_NAME_SUFFIX)), "Invalid qualifiedName for the contract.");

        String contractString = (String) entity.getAttribute(ATTR_CONTRACT);
        DataContract contract = DataContract.deserialize(contractString);
        String datasetQName = contractQName.substring(0, contractQName.lastIndexOf('/'));
        contractQName = String.format("%s/%s/%s", datasetQName, contract.type.name(), CONTRACT_QUALIFIED_NAME_SUFFIX);
        AtlasEntityWithExtInfo associatedAsset = getAssociatedAsset(datasetQName, contract.type.name());

        authorizeContractCreateOrUpdate(entity, associatedAsset);

        contractAttributeSync(entity, contract);
        contractString = DataContract.serialize(contract);
        entity.setAttribute(ATTR_CONTRACT, contractString);

        ContractVersionUtils versionUtil = new ContractVersionUtils(contractQName, context, entityRetriever, typeRegistry, entityStore, graph, discovery);
        AtlasEntity currentVersionEntity = versionUtil.getCurrentVersion();
        int newVersionNumber =  1;
        if (currentVersionEntity != null) {
            // Contract already exist
            String qName = (String) currentVersionEntity.getAttribute(QUALIFIED_NAME);
            int currentVersionNumber = Integer.parseInt(qName.substring(qName.lastIndexOf("/V") + 2));
            List<String> attributes = getDiffAttributes(context, entity, currentVersionEntity);
            if (attributes.isEmpty()) {
                // No changes in the contract, Not creating new version
                removeCreatingVertex(context, entity);
                return;
            } else if (isEqualContract(contractString, (String) currentVersionEntity.getAttribute(ATTR_CONTRACT))) {
                // No change in contract, metadata changed
                updateExistingVersion(context, entity, currentVersionEntity);
                newVersionNumber = currentVersionNumber;
            } else {
                // contract changed (metadata might/not changed). Create new version.
                newVersionNumber =  currentVersionNumber + 1;

                // Attach previous version via rel
                entity.setRelationshipAttribute(REL_ATTR_PREVIOUS_VERSION, getAtlasObjectId(currentVersionEntity));
                AtlasVertex vertex = AtlasGraphUtilsV2.findByGuid(currentVersionEntity.getGuid());
                AtlasEntityType entityType = ensureEntityType(currentVersionEntity.getTypeName());
                context.addUpdated(currentVersionEntity.getGuid(), currentVersionEntity, entityType, vertex);

            }
        }
        entity.setAttribute(QUALIFIED_NAME, String.format("%s/V%s", contractQName, newVersionNumber));
        entity.setAttribute(ATTR_VERSION, newVersionNumber);
        entity.setAttribute(ATTR_ASSET_GUID, associatedAsset.getEntity().getGuid());
        entity.setRelationshipAttribute(REL_ATTR_GOVERNED_ASSET, getAtlasObjectId(associatedAsset.getEntity()));
        if (Objects.equals(entity.getAttribute(ATTR_CERTIFICATE_STATUS), DataContract.STATUS.VERIFIED.name()) ) {
            entity.setRelationshipAttribute(REL_ATTR_GOVERNED_ASSET_CERTIFIED, getAtlasObjectId(associatedAsset.getEntity()));
        }

        datasetAttributeSync(context, associatedAsset.getEntity(), contract, entity);

    }

    private List<String> getDiffAttributes(EntityMutationContext context, AtlasEntity entity, AtlasEntity latestExistingVersion) throws AtlasBaseException {
        AtlasEntityComparator entityComparator = new AtlasEntityComparator(typeRegistry, entityRetriever, context.getGuidAssignments(), true, true);
        AtlasEntityComparator.AtlasEntityDiffResult diffResult = entityComparator.getDiffResult(entity, latestExistingVersion, false);
        List<String> attributesSet = new ArrayList<>();

        if (diffResult.hasDifference()) {
            for (Map.Entry<String, Object> entry : diffResult.getDiffEntity().getAttributes().entrySet()) {
                if (!entry.getKey().equals(QUALIFIED_NAME)) {
                    attributesSet.add(entry.getKey());
                }
            }
        }
        return attributesSet;
    }

    private boolean isEqualContract(String firstNode, String secondNode) throws AtlasBaseException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode actualObj1 = mapper.readTree(firstNode);
            JsonNode actualObj2 = mapper.readTree(secondNode);
            //Ignore status field change
            ((ObjectNode) actualObj1).remove(CONTRACT_ATTR_STATUS);
            ((ObjectNode) actualObj2).remove(CONTRACT_ATTR_STATUS);

            return actualObj1.equals(actualObj2);
        } catch (JsonProcessingException e) {
            throw new AtlasBaseException(JSON_ERROR, e.getMessage());
        }

    }

    private void updateExistingVersion(EntityMutationContext context, AtlasEntity entity, AtlasEntity currentVersionEntity) throws AtlasBaseException {
        removeCreatingVertex(context, entity);
        entity.setAttribute(QUALIFIED_NAME, currentVersionEntity.getAttribute(QUALIFIED_NAME));
        entity.setGuid(currentVersionEntity.getGuid());

        AtlasVertex vertex = AtlasGraphUtilsV2.findByGuid(entity.getGuid());

        AtlasEntityType entityType = ensureEntityType(entity.getTypeName());

        context.addUpdated(entity.getGuid(), entity, entityType, vertex);

    }

    private void removeCreatingVertex(EntityMutationContext context, AtlasEntity entity) throws AtlasBaseException {
        context.getCreatedEntities().remove(entity);
        try {
            RequestContext.get().setSkipAuthorizationCheck(true);
            Set<String> guids = new HashSet<>();
            guids.add(entity.getGuid());
            entityStore.purgeByIds(guids);
        } finally {
            RequestContext.get().setSkipAuthorizationCheck(false);
        }

    }

    private void contractAttributeSync(AtlasEntity entity, DataContract contract) throws AtlasBaseException {
        // Sync certificateStatus
        if (!Objects.equals(entity.getAttribute(ATTR_CERTIFICATE_STATUS), contract.getStatus().name())) {
            /*
            CertificateStatus    |    Status      |     Result
               DRAFT                  VERIFIED       cert -> VERIFIED  >
               VERIFIED               DRAFT          stat -> VERIFIED  >
                 -                    DRAFT          cert -> DRAFT
                 -                    VERIFIED       cert -> VERIFIED  >
               DRAFT                    -            stat -> DRAFT
               VERIFIED                 -            stat -> VERIFIED  >

             */
            if (Objects.equals(entity.getAttribute(ATTR_CERTIFICATE_STATUS), DataContract.STATUS.VERIFIED.name())) {
                contract.setStatus(String.valueOf(DataContract.STATUS.VERIFIED));
            } else if (Objects.equals(contract.getStatus(), DataContract.STATUS.VERIFIED)) {
                entity.setAttribute(ATTR_CERTIFICATE_STATUS, DataContract.STATUS.VERIFIED.name());
            } else {
                entity.setAttribute(ATTR_CERTIFICATE_STATUS, DataContract.STATUS.DRAFT);
                contract.setStatus(String.valueOf(DataContract.STATUS.DRAFT));
            }

        }

    }

    private void datasetAttributeSync(EntityMutationContext context, AtlasEntity associatedAsset, DataContract contract, AtlasEntity contractAsset) throws AtlasBaseException {
        // Creating new empty AtlasEntity to update with selective attributes only
        AtlasEntity entity = new AtlasEntity(associatedAsset.getTypeName());
        entity.setGuid(associatedAsset.getGuid());
        entity.setAttribute(QUALIFIED_NAME, associatedAsset.getAttribute(QUALIFIED_NAME));
        if (associatedAsset.getAttribute(ASSET_ATTR_HAS_CONTRACT) == null || associatedAsset.getAttribute(ASSET_ATTR_HAS_CONTRACT).equals(false)) {
            entity.setAttribute(ASSET_ATTR_HAS_CONTRACT, true);
        }
        AtlasVertex vertex = AtlasGraphUtilsV2.findByGuid(entity.getGuid());
        AtlasEntityType entityType = ensureEntityType(entity.getTypeName());
        AtlasEntityComparator entityComparator = new AtlasEntityComparator(typeRegistry, entityRetriever, context.getGuidAssignments(), true, true);
        AtlasEntityComparator.AtlasEntityDiffResult diffResult   = entityComparator.getDiffResult(entity, vertex, !storeDifferentialAudits);
        RequestContext        reqContext           = RequestContext.get();
        context.addUpdated(entity.getGuid(), entity, entityType, vertex);

        if (diffResult.hasDifference()) {
            if (storeDifferentialAudits) {
                diffResult.getDiffEntity().setGuid(entity.getGuid());
                reqContext.cacheDifferentialEntity(diffResult.getDiffEntity());
            }
        }
    }

    private static void validateAttribute(boolean isInvalid, String errorMessage) throws AtlasBaseException {
        if (isInvalid)
            throw new AtlasBaseException(BAD_REQUEST, errorMessage);
    }
}
