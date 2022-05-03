package org.apache.atlas.repository.store.graph.v2.tasks;

import org.apache.atlas.discovery.EntityDiscoveryService;
import org.apache.atlas.model.tasks.AtlasTask;
import org.apache.atlas.repository.store.graph.v2.EntityGraphMapper;
import org.apache.atlas.repository.store.graph.v2.preprocessor.glossary.TermPreProcessor;
import org.apache.atlas.tasks.AbstractTask;
import org.apache.atlas.tasks.TaskFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Component
public class MeaningsTaskUpdateTaskFactory implements TaskFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MeaningsTaskUpdateTaskFactory.class);

    public static final String MEANINGS_TEXT_UPDATE = "MEANINGS_TEXT_UPDATE";

    private static final List<String> supportedTypes = new ArrayList<String>() {{
        add(MEANINGS_TEXT_UPDATE);
    }};

    protected final EntityDiscoveryService entityDiscovery;
    protected final EntityGraphMapper entityGraphMapper;
    protected final TermPreProcessor preprocessor;

    @Inject
    public MeaningsTaskUpdateTaskFactory( EntityDiscoveryService entityDiscovery, EntityGraphMapper entityGraphMapper, TermPreProcessor preprocessor) {
        this.entityDiscovery = entityDiscovery;
        this.entityGraphMapper = entityGraphMapper;
        this.preprocessor = preprocessor;
    }

    @Override
    public org.apache.atlas.tasks.AbstractTask create(AtlasTask atlasTask) {
        String taskType = atlasTask.getType();
        String taskGuid = atlasTask.getGuid();

        switch (taskType) {
            case MEANINGS_TEXT_UPDATE:
                return new MeaningsUpdateTasks.Update(atlasTask, entityDiscovery, entityGraphMapper, preprocessor);

            default:
                LOG.warn("Type: {} - {} not found!. The task will be ignored.", taskType, taskGuid);
                return null;
        }
    }


    @Override
    public List<String> getSupportedTypes() {
        return this.supportedTypes;
    }
}
