package org.apache.atlas.web.rest;

import org.apache.atlas.annotation.Timed;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.repository.graph.GraphBackedSearchIndexer;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.atlas.web.util.Servlets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Path("admin/types")
@Singleton
@Service
@Consumes({Servlets.JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON})
@Produces({Servlets.JSON_MEDIA_TYPE, MediaType.APPLICATION_JSON})
public class TypeCacheRefreshREST {
    private static final Logger LOG = LoggerFactory.getLogger(TypeCacheRefreshREST.class);

    private final AtlasTypeDefStore typeDefStore;
    private final GraphBackedSearchIndexer graphBackedSearchIndexer;
    private final AtlasTypeRegistry typeRegistry;

    @Inject
    public TypeCacheRefreshREST(AtlasTypeDefStore typeDefStore, GraphBackedSearchIndexer graphBackedSearchIndexer, AtlasTypeRegistry typeRegistry) {
        this.typeDefStore = typeDefStore;
        this.graphBackedSearchIndexer = graphBackedSearchIndexer;
        this.typeRegistry = typeRegistry;
    }

    /**
     * API to refresh type-def cache.
     * @throws AtlasBaseException
     * @HTTP 204 if type def cache is refreshed successfully
     * @HTTP 500 if there is an error refreshing type def cache
     */
    @POST
    @Path("/refresh")
    @Timed
    public void refreshCache() throws AtlasBaseException {
        LOG.info("Initiating type-def cache refresh");

        //Reload in-memory cache of type-registry
        typeDefStore.init();

        /*ChangedTypeDefs changedTypeDefs = new ChangedTypeDefs();
        List<AtlasBaseTypeDef> updatedTypeDefs = new ArrayList<>();
        updatedTypeDefs.addAll(typeRegistry.getAllEnumDefs());
        updatedTypeDefs.addAll(typeRegistry.getAllBusinessMetadataDefs());
        updatedTypeDefs.addAll(typeRegistry.getAllClassificationDefs());
        updatedTypeDefs.addAll(typeRegistry.getAllStructDefs());
        updatedTypeDefs.addAll(typeRegistry.getAllRelationshipDefs());
        updatedTypeDefs.addAll(typeRegistry.getAllEntityDefs());
        changedTypeDefs.setUpdatedTypeDefs(updatedTypeDefs);

        LOG.info("total type-defs to being updated = {}",updatedTypeDefs.size());
        graphBackedSearchIndexer.onChange(changedTypeDefs);*/

        typeDefStore.notifyLoadCompletion();

        LOG.info("Completed type-def cache refresh");
    }
}
