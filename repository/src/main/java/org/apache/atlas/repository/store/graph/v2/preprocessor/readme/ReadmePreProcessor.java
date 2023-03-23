package org.apache.atlas.repository.store.graph.v2.preprocessor.readme;

import org.apache.atlas.GraphTransactionInterceptor;
import org.apache.atlas.RequestContext;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.instance.*;
import org.apache.atlas.repository.graph.GraphHelper;
import org.apache.atlas.repository.graphdb.AtlasGraph;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.repository.store.graph.v1.RestoreHandlerV1;
import org.apache.atlas.repository.store.graph.v2.AtlasGraphUtilsV2;
import org.apache.atlas.repository.store.graph.v2.EntityGraphRetriever;
import org.apache.atlas.repository.store.graph.v2.EntityMutationContext;
import org.apache.atlas.repository.store.graph.v2.preprocessor.PreProcessor;
import org.apache.atlas.type.AtlasEntityType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.atlas.utils.AtlasPerfMetrics;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;

import static org.apache.atlas.AtlasErrorCode.BAD_REQUEST;
import static org.apache.atlas.AtlasErrorCode.README_FAILED;
import static org.apache.atlas.model.instance.AtlasEntity.Status.DELETED;
import static org.apache.atlas.repository.Constants.QUALIFIED_NAME;
import static org.apache.atlas.repository.Constants.STATE_PROPERTY_KEY;
import static org.apache.atlas.repository.Constants.ASSET;
import static org.apache.atlas.repository.Constants.NAME;

public class ReadmePreProcessor implements PreProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ReadmePreProcessor.class);

    private final AtlasTypeRegistry    typeRegistry;
    private final EntityGraphRetriever entityRetriever;
    private final AtlasGraph           graph;
    private final RestoreHandlerV1     restoreHandlerV1;

    public ReadmePreProcessor(AtlasTypeRegistry typeRegistry, EntityGraphRetriever entityRetriever, AtlasGraph graph, RestoreHandlerV1 restoreHandlerV1) {
        this.entityRetriever = entityRetriever;
        this.typeRegistry = typeRegistry;
        this.graph = graph;
        this.restoreHandlerV1 = restoreHandlerV1;
    }

    @Override
    public void processAttributes(AtlasStruct entityStruct, EntityMutationContext context,
                                  EntityMutations.EntityOperation operation) throws AtlasBaseException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("ReadmePreProcessor.processAttributes: pre processing {}, {}", entityStruct.getAttribute(QUALIFIED_NAME), operation);
        }

        AtlasEntity entity = (AtlasEntity) entityStruct;

        switch (operation) {
            case CREATE:
                processCreateReadme(entity, context);
                break;
            case UPDATE:
                processUpdateReadme(entity, context.getVertex(entity.getGuid()));
                break;
            default:
                throw new AtlasBaseException(BAD_REQUEST, "Invalid Request");
        }
    }

    private void processCreateReadme(AtlasEntity entity, EntityMutationContext context) throws AtlasBaseException {
        AtlasObjectId asset = (AtlasObjectId) entity.getRelationshipAttribute(ASSET);
        RequestContext requestContext = RequestContext.get();
        AtlasPerfMetrics.MetricRecorder metricRecorder = requestContext.startMetricRecord("processCreateReadme");
        try {
            if (asset != null) {
                String entityQualifiedName = createQualifiedName(asset);

                entity.setAttribute(QUALIFIED_NAME, entityQualifiedName);

                AtlasEntityType entityType = typeRegistry.getEntityTypeByName(entity.getTypeName());
                AtlasVertex vertex = AtlasGraphUtilsV2.findByUniqueAttributes(graph, entityType, entity.getAttributes());
                if (vertex != null) {
                    if (vertex.getProperty(STATE_PROPERTY_KEY, String.class).equals(DELETED.name())) {
                        GraphTransactionInterceptor.addToVertexStateCache(vertex.getId(), AtlasEntity.Status.ACTIVE);
                        restoreHandlerV1.restoreEntities(Collections.singletonList(vertex));
                    }
                    String internalGuidOfReadme = context.getDiscoveryContext().getReferencedGuids().get(asset.getGuid());
                    entity.setGuid(GraphHelper.getGuid(vertex));
                    context.updateEntityReferences(internalGuidOfReadme, entity, entityType, vertex);
                }
                requestContext.recordEntityUpdate(entityRetriever.toAtlasEntityHeader(vertex, entity.getAttributes().keySet()));
                requestContext.cacheDifferentialEntity(entity);
            } else {
                LOG.error("Asset is required for readme creation");
                throw new AtlasBaseException(README_FAILED, (String) entity.getAttribute(NAME));
            }
        } finally{
            requestContext.endMetricRecord(metricRecorder);
        }
    }
    private void processUpdateReadme(AtlasEntity entity, AtlasVertex vertex) throws AtlasBaseException {
        RequestContext requestContext = RequestContext.get();
        AtlasPerfMetrics.MetricRecorder metricRecorder = requestContext.startMetricRecord("processUpdateReadme");

        entity.setAttribute(QUALIFIED_NAME, vertex.getProperty(QUALIFIED_NAME, String.class));
        requestContext.recordEntityUpdate(entityRetriever.toAtlasEntityHeader(vertex, entity.getAttributes().keySet()));
        requestContext.cacheDifferentialEntity(entity);
        requestContext.endMetricRecord(metricRecorder);
    }

    private String createQualifiedName(AtlasObjectId atlasObjectId) throws AtlasBaseException {
        String guid = atlasObjectId.getGuid();
        if(StringUtils.isEmpty(guid))
            guid = AtlasGraphUtilsV2.getGuidByUniqueAttributes(graph, typeRegistry.getEntityTypeByName(atlasObjectId.getTypeName()), atlasObjectId.getUniqueAttributes());

        return guid + "/readme";
    }
}