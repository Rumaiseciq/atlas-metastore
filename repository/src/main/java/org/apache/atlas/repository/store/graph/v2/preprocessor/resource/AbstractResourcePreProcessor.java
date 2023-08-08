/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.store.graph.v2.preprocessor.resource;

import org.apache.atlas.RequestContext;
import org.apache.atlas.authorize.AtlasAuthorizationUtils;
import org.apache.atlas.authorize.AtlasEntityAccessRequest;
import org.apache.atlas.authorize.AtlasPrivilege;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.repository.graphdb.AtlasEdgeDirection;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.repository.store.graph.v2.EntityGraphRetriever;
import org.apache.atlas.repository.store.graph.v2.preprocessor.PreProcessor;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.atlas.utils.AtlasPerfMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import static org.apache.atlas.repository.Constants.ACTIVE_STATE_VALUE;
import static org.apache.atlas.repository.Constants.ASSET_RELATION_ATTR;
import static org.apache.atlas.repository.Constants.STATE_PROPERTY_KEY;

public abstract class AbstractResourcePreProcessor implements PreProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractResourcePreProcessor.class);


    private final AtlasTypeRegistry typeRegistry;
    private final EntityGraphRetriever entityRetriever;

    AbstractResourcePreProcessor(AtlasTypeRegistry typeRegistry,
                              EntityGraphRetriever entityRetriever) {
        this.typeRegistry = typeRegistry;
        this.entityRetriever = entityRetriever;
    }

    void authorizeResourceUpdate(AtlasEntity resourceEntity, AtlasVertex ResourceVertex, String edgeLabel) throws AtlasBaseException {
        AtlasPerfMetrics.MetricRecorder metricRecorder = RequestContext.get().startMetricRecord("authorizeResourceUpdate");

        try {
            AtlasEntityHeader entityHeaderToAuthorize = null;

            AtlasObjectId asset = getAssetRelationAttr(resourceEntity);
            if (asset != null) {
                //Found linked asset in payload
                AtlasVertex assetVertex = entityRetriever.getEntityVertex(asset);
                entityHeaderToAuthorize = entityRetriever.toAtlasEntityHeader(assetVertex);

            } else {
                //Check for linked asset in store
                Iterator atlasVertexIterator = ResourceVertex.query()
                        .direction(AtlasEdgeDirection.IN)
                        .label(edgeLabel)
                        .has(STATE_PROPERTY_KEY, ACTIVE_STATE_VALUE)
                        .vertices()
                        .iterator();

                if (atlasVertexIterator.hasNext()) {
                    //Found linked asset in store
                    AtlasVertex assetVertex = (AtlasVertex) atlasVertexIterator.next();
                    entityHeaderToAuthorize = entityRetriever.toAtlasEntityHeader(assetVertex);
                }
            }

            if (entityHeaderToAuthorize != null) {
                //First authorize entity update access
                AtlasAuthorizationUtils.verifyAccess(new AtlasEntityAccessRequest(typeRegistry, AtlasPrivilege.ENTITY_UPDATE, entityHeaderToAuthorize),
                        "update entity: ", entityHeaderToAuthorize.getTypeName());

                entityHeaderToAuthorize = new AtlasEntityHeader(resourceEntity);

                try {
                    AtlasAuthorizationUtils.verifyAccess(new AtlasEntityAccessRequest(typeRegistry, AtlasPrivilege.ENTITY_UPDATE, entityHeaderToAuthorize),
                            "update entity: ", entityHeaderToAuthorize.getTypeName());
                } catch (AtlasBaseException abe) {
                    //ignore as this is just for access logs purpose
                }
            }

            if (entityHeaderToAuthorize == null) {
                //No linked asset to the Resource, check for resource update permission
                entityHeaderToAuthorize = new AtlasEntityHeader(resourceEntity);

                AtlasAuthorizationUtils.verifyAccess(new AtlasEntityAccessRequest(typeRegistry, AtlasPrivilege.ENTITY_UPDATE, entityHeaderToAuthorize),
                        "update entity: ", entityHeaderToAuthorize.getTypeName());
            }

        } finally {
            RequestContext.get().endMetricRecord(metricRecorder);
        }
    }

    void authorizeResourceDelete(AtlasVertex resourceVertex) throws AtlasBaseException {
        AtlasPerfMetrics.MetricRecorder recorder = RequestContext.get().startMetricRecord("authorizeResourceDelete");

        try {
            AtlasEntityHeader entityHeaderToAuthorize = null;

            AtlasEntity resourceEntity = entityRetriever.toAtlasEntity(resourceVertex);

            AtlasObjectId asset = getAssetRelationAttr(resourceEntity);
            if (asset != null) {
                entityHeaderToAuthorize = entityRetriever.toAtlasEntityHeader(asset.getGuid());

                AtlasAuthorizationUtils.verifyAccess(new AtlasEntityAccessRequest(typeRegistry, AtlasPrivilege.ENTITY_UPDATE, entityHeaderToAuthorize),
                        "update entity: ", entityHeaderToAuthorize.getTypeName());

                entityHeaderToAuthorize = new AtlasEntityHeader(resourceEntity);

                try {
                    AtlasAuthorizationUtils.verifyAccess(new AtlasEntityAccessRequest(typeRegistry, AtlasPrivilege.ENTITY_DELETE, entityHeaderToAuthorize),
                            "delete entity: ", entityHeaderToAuthorize.getTypeName());
                } catch (AtlasBaseException abe) {
                    //ignore as this is just for access logs purpose
                }

            } else {
                //No linked asset to the Resource, check for resource delete permission
                entityHeaderToAuthorize = new AtlasEntityHeader(resourceEntity);

                AtlasAuthorizationUtils.verifyAccess(new AtlasEntityAccessRequest(typeRegistry, AtlasPrivilege.ENTITY_DELETE, entityHeaderToAuthorize),
                        "delete entity: ", entityHeaderToAuthorize.getTypeName());
            }

        } finally {
            RequestContext.get().endMetricRecord(recorder);
        }
    }

    private AtlasObjectId getAssetRelationAttr(AtlasEntity entity) {
        AtlasObjectId ret = null;

        if (entity.hasRelationshipAttribute(ASSET_RELATION_ATTR) &&
                entity.getRelationshipAttribute(ASSET_RELATION_ATTR) != null) {
            ret = (AtlasObjectId) entity.getRelationshipAttribute(ASSET_RELATION_ATTR);
        }

        return ret;
    }
}
