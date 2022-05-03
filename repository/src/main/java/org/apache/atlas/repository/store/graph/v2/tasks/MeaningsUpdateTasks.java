package org.apache.atlas.repository.store.graph.v2.tasks;

import org.apache.atlas.discovery.EntityDiscoveryService;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.tasks.AtlasTask;
import org.apache.atlas.repository.store.graph.v2.EntityGraphMapper;
import org.apache.atlas.repository.store.graph.v2.preprocessor.glossary.TermPreProcessor;

import java.util.Map;

public class MeaningsUpdateTasks {
    public static class Update extends MeaningsTask {
        public Update(AtlasTask task, EntityDiscoveryService entityDiscovery, EntityGraphMapper entityGraphMapper, TermPreProcessor preprocessor) {
            super(task, entityDiscovery, entityGraphMapper, preprocessor);
        }

        @Override
        protected void run(Map<String, Object> parameters) throws AtlasBaseException {
            String termEntityGuid = (String) parameters.get(PARAM_ENTITY_GUID);
            String termQualifiedname = (String) parameters.get(PARAM_ENTITY_QUALIFIED_NAME);
            String updateTerm = (String) parameters.get(PARAM_TERM_NAME);
            int offset = (int) parameters.get(PARAM_PAGINATION_OFFSET);

            preprocessor.updateMeaningsNamesInEntities(updateTerm, termQualifiedname, termEntityGuid, offset);

        }
    }
}
