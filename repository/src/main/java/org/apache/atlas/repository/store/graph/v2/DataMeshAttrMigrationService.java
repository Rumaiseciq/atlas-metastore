package org.apache.atlas.repository.store.graph.v2;

import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasRelatedObjectId;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.apache.atlas.repository.Constants.*;
import static org.apache.atlas.repository.store.graph.v2.preprocessor.PreProcessorUtils.*;

public class DataMeshAttrMigrationService {

    private static final Logger LOG = LoggerFactory.getLogger(DataMeshQNMigrationService.class);

    private final EntityGraphRetriever entityRetriever;


    private String productGuid;
    private final TransactionInterceptHelper   transactionInterceptHelper;

    public DataMeshAttrMigrationService(EntityGraphRetriever entityRetriever, String productGuid, TransactionInterceptHelper transactionInterceptHelper) {
        this.entityRetriever = entityRetriever;
        this.transactionInterceptHelper = transactionInterceptHelper;
        this.productGuid = productGuid;
    }

    public void migrateProduct() throws Exception {
        try {
            AtlasVertex productVertex = entityRetriever.getEntityVertex(this.productGuid);
            migrateAttr(productVertex);
            commitChanges();
        } catch (Exception e) {
            LOG.error("Migration failed for entity", e);
            throw e;
        }
    }

    private void migrateAttr(AtlasVertex vertex) throws AtlasBaseException {
        AtlasEntity productEntity = entityRetriever.toAtlasEntity(vertex);
        List<Object> outputPorts = (List<Object>) productEntity.getRelationshipAttribute(OUTPUT_PORT_ATTR);
        List<String> outputPortGuids = getAssetGuids(outputPorts);
        List<String> outputPortGuidsAttr = vertex.getMultiValuedProperty(OUTPUT_PORT_GUIDS_ATTR, String.class);

        List<Object> inputPorts = (List<Object>) productEntity.getRelationshipAttribute(INPUT_PORT_ATTR);
        List<String> inputPortGuids = getAssetGuids(inputPorts);
        List<String> inputPortGuidsAttr = vertex.getMultiValuedProperty(INPUT_PORT_GUIDS_ATTR, String.class);

        if(!CollectionUtils.isEqualCollection(outputPortGuids, outputPortGuidsAttr)) {
           LOG.info("Migrating outputPort guid attribute: {} for Product: {}", OUTPUT_PORT_GUIDS_ATTR, vertex.getProperty(QUALIFIED_NAME, String.class));
           addInternalAttr(vertex, OUTPUT_PORT_GUIDS_ATTR, outputPortGuids);
        }

        if(!CollectionUtils.isEqualCollection(inputPortGuids, inputPortGuidsAttr)) {
            LOG.info("Migrating inputPort guid attribute: {} for Product: {}", INPUT_PORT_GUIDS_ATTR, vertex.getProperty(QUALIFIED_NAME, String.class));
            addInternalAttr(vertex, INPUT_PORT_GUIDS_ATTR, inputPortGuids);
        }
    }

    public void commitChanges() throws AtlasBaseException {
        try {
            transactionInterceptHelper.intercept();
            LOG.info("Committed a entity to the graph");
        } catch (Exception e){
            LOG.error("Failed to commit asset: ", e);
            throw e;
        }
    }

    private List<String> getAssetGuids(List<Object> elements){
        List<String> guids = new ArrayList<>();
        for(Object element : elements){
            AtlasRelatedObjectId relatedObjectId = (AtlasRelatedObjectId) element;
            guids.add(relatedObjectId.getGuid());
        }
        return guids;
    }

    private void addInternalAttr(AtlasVertex productVertex, String internalAttr, List<String> currentGuids){
        if (CollectionUtils.isNotEmpty(currentGuids)) {
            currentGuids.forEach(guid -> AtlasGraphUtilsV2.addEncodedProperty(productVertex, internalAttr , guid));
        }
    }
}