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
package org.apache.atlas.persona;

import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.ranger.plugin.model.RangerPolicy;

import java.util.ArrayList;
import java.util.List;

import static org.apache.atlas.persona.AtlasPersonaUtil.getIsAllow;
import static org.apache.atlas.repository.Constants.PERSONA_GLOSSARY_POLICY_ENTITY_TYPE;
import static org.apache.atlas.repository.Constants.PERSONA_METADATA_POLICY_ENTITY_TYPE;

public class PersonaContext {


    private AtlasEntityWithExtInfo personaExtInfo;
    private AtlasEntity personaPolicy;
    private AtlasEntity existingPersonaPolicy;
    private AtlasEntity connection;
    private boolean isCreateNewPersona;
    private boolean isCreateNewPersonaPolicy;
    private boolean isUpdatePersonaPolicy;

    private boolean hasTermActions;
    private boolean hasLinkAssetsActions;
    private boolean hasEntityActions;
    private boolean hasEntityClassificationActions;
    private boolean hasEntityBMActions;
    private boolean hasEntityLabelActions;

    private boolean isAllowPolicy;
    private boolean updateIsAllow = false;

    private List<RangerPolicy> excessExistingRangerPolicies = new ArrayList<>();

    private boolean isMetadataPolicy = false;
    private boolean isGlossaryPolicy = false;


    public PersonaContext() {}

    public PersonaContext(AtlasEntityWithExtInfo personaExtInfo) {
        this.personaExtInfo = personaExtInfo;
    }

    public PersonaContext(AtlasEntityWithExtInfo personaExtInfo, AtlasEntity personaPolicy) {
        this.personaExtInfo = personaExtInfo;
        this.personaPolicy = personaPolicy;
    }

    public AtlasEntityWithExtInfo getPersonaExtInfo() {
        return personaExtInfo;
    }

    public AtlasEntity getPersona() {
        return personaExtInfo.getEntity();
    }

    public void setPersonaExtInfo(AtlasEntityWithExtInfo personaExtInfo) {
        this.personaExtInfo = personaExtInfo;
    }

    public AtlasEntity getPersonaPolicy() {
        return personaPolicy;
    }

    public void setPersonaPolicy(AtlasEntity personaPolicy) {
        this.personaPolicy = personaPolicy;
    }

    public boolean isCreateNewPersona() {
        return isCreateNewPersona;
    }

    public void setCreateNewPersona(boolean createNewPersona) {
        isCreateNewPersona = createNewPersona;
    }

    public boolean isCreateNewPersonaPolicy() {
        return isCreateNewPersonaPolicy;
    }

    public void setCreateNewPersonaPolicy(boolean createNewPersonaPolicy) {
        isCreateNewPersonaPolicy = createNewPersonaPolicy;
    }

    public boolean isUpdatePersonaPolicy() {
        return isUpdatePersonaPolicy;
    }

    public void setUpdatePersonaPolicy(boolean updatePersonaPolicy) {
        isUpdatePersonaPolicy = updatePersonaPolicy;
    }

    public boolean hasTermActions() {
        return hasTermActions;
    }

    public void setHasTermActions(boolean hasTermActions) {
        this.hasTermActions = hasTermActions;
    }

    public boolean hasLinkAssetsActions() {
        return hasLinkAssetsActions;
    }

    public void setHasLinkAssetsActions(boolean hasLinkAssetsActions) {
        this.hasLinkAssetsActions = hasLinkAssetsActions;
    }

    public boolean hasEntityActions() {
        return hasEntityActions;
    }

    public void setHasEntityActions(boolean hasEntityActions) {
        this.hasEntityActions = hasEntityActions;
    }

    public boolean hasEntityClassificationActions() {
        return hasEntityClassificationActions;
    }

    public void setHasEntityClassificationActions(boolean hasEntityClassificationActions) {
        this.hasEntityClassificationActions = hasEntityClassificationActions;
    }

    public boolean hasEntityBMActions() {
        return hasEntityBMActions;
    }

    public void setHasEntityBMActions(boolean hasEntityBMActions) {
        this.hasEntityBMActions = hasEntityBMActions;
    }

    public boolean hasEntityLabelActions() {
        return hasEntityLabelActions;
    }

    public void setHasEntityLabelActions(boolean hasEntityLabelActions) {
        this.hasEntityLabelActions = hasEntityLabelActions;
    }

    public AtlasEntity getExistingPersonaPolicy() {
        return existingPersonaPolicy;
    }

    public void setExistingPersonaPolicy(AtlasEntity existingPersonaPolicy) {
        this.existingPersonaPolicy = existingPersonaPolicy;
    }

    public List<RangerPolicy> getExcessExistingRangerPolicies() {
        return excessExistingRangerPolicies;
    }

    public void setExcessExistingRangerPolicies(List<RangerPolicy> excessExistingRangerPolicies) {
        this.excessExistingRangerPolicies = excessExistingRangerPolicies;
    }

    public void addExcessExistingRangerPolicy(RangerPolicy excessExistingRangerPolicy) {
        this.excessExistingRangerPolicies.add(excessExistingRangerPolicy);
    }

    public boolean isAllowPolicy() {
        return isAllowPolicy;
    }

    public void setAllowPolicy(boolean allowPolicy) {
        isAllowPolicy = allowPolicy;
    }

    public boolean isUpdateIsAllow() {
        return updateIsAllow;
    }

    public void setAllowPolicyUpdate() {
        if (existingPersonaPolicy != null) {
            updateIsAllow = getIsAllow(existingPersonaPolicy) != isAllowPolicy;
        }
    }

    public boolean isMetadataPolicy() {
        return isMetadataPolicy;
    }

    public void setMetadataPolicy(boolean metadataPolicy) {
        isMetadataPolicy = metadataPolicy;
    }

    public boolean isGlossaryPolicy() {
        return isGlossaryPolicy;
    }

    public void setGlossaryPolicy(boolean glossaryPolicy) {
        isGlossaryPolicy = glossaryPolicy;
    }

    public void setPolicyType(){
        if (getPersonaPolicy() != null) {
            String type = getPersonaPolicy().getTypeName();

            switch (type) {
                case PERSONA_METADATA_POLICY_ENTITY_TYPE: isMetadataPolicy = true; break;
                case PERSONA_GLOSSARY_POLICY_ENTITY_TYPE: isGlossaryPolicy = true; break;
            }
        }
    }

    public AtlasEntity getConnection() {
        return connection;
    }

    public void setConnection(AtlasEntity connection) {
        this.connection = connection;
    }
}
