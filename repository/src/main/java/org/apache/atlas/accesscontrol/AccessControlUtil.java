package org.apache.atlas.accesscontrol;

import org.apache.atlas.RequestContext;
import org.apache.atlas.accesscontrol.persona.AtlasPersonaUtil;
import org.apache.atlas.discovery.EntityDiscoveryService;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.discovery.AtlasSearchResult;
import org.apache.atlas.model.discovery.IndexSearchParams;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.ranger.AtlasRangerService;
import org.apache.atlas.type.AtlasType;
import org.apache.atlas.util.NanoIdUtils;
import org.apache.atlas.utils.AtlasPerfMetrics;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicyResourceSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.atlas.AtlasErrorCode.ACCESS_CONTROL_MUTATIONS_NOT_ALLOWED;
import static org.apache.atlas.repository.Constants.ACCESS_CONTROL_ENTITY_TYPES;
import static org.apache.atlas.repository.Constants.ACCESS_CONTROL_RELATION_TYPE;
import static org.apache.atlas.repository.Constants.NAME;
import static org.apache.atlas.repository.Constants.POLICY_TYPE_DATA;
import static org.apache.atlas.repository.Constants.POLICY_TYPE_METADATA;
import static org.apache.atlas.repository.Constants.QUALIFIED_NAME;


public class AccessControlUtil {
    private static final Logger LOG = LoggerFactory.getLogger(AccessControlUtil.class);

    public static final String RESOURCE_PREFIX = "resource:";

    public static final String RANGER_POLICY_TYPE_ACCESS    = "0";
    public static final String RANGER_POLICY_TYPE_DATA_MASK = "1";

    public static final String ACCESS_ENTITY_CREATE = "entity-create";
    public static final String ACCESS_ENTITY_READ   = "entity-read";
    public static final String ACCESS_ADD_REL       = "add-relationship";
    public static final String ACCESS_UPDATE_REL    = "update-relationship";
    public static final String ACCESS_REMOVE_REL    = "remove-relationship";

    public static final String LINK_ASSET_ACTION = "link-assets";

    public static final String RANGER_MASK_REDACT    = "MASK_REDACT";
    public static final String RANGER_MASK_LAST_4    = "MASK_SHOW_LAST_4";
    public static final String RANGER_MASK_FIRST_4   = "MASK_SHOW_FIRST_4";
    public static final String RANGER_MASK_HASH      = "MASK_HASH";
    public static final String RANGER_MASK_NULL      = "MASK_NULL";
    public static final String RANGER_MASK_SHOW_YEAR = "MASK_DATE_SHOW_YEAR";
    public static final String RANGER_MASK_NONE      = "MASK_NONE";


    public static String getUUID() {
        return NanoIdUtils.randomNanoId(22);
    }

    public static String getName(AtlasEntity entity) {
        return (String) entity.getAttribute(NAME);
    }

    public static String getQualifiedName(AtlasEntity entity) {
        return (String) entity.getAttribute(QUALIFIED_NAME);
    }

    public static String getESAliasName(AtlasEntity entity) {
        String qualifiedName = getQualifiedName(entity);

        String[] parts = qualifiedName.split("/");

        return parts[parts.length - 1];
    }

    public static boolean getIsAllow(AtlasEntity entity) {
        return (boolean) entity.getAttribute("isAllowPolicy");
    }

    public static String getTenantId(AtlasEntity entity) {
        return (String) entity.getAttribute("tenantId");
    }

    public static boolean getIsEnabled(AtlasEntity entity) throws AtlasBaseException {
        return (boolean) entity.getAttribute("isAccessControlEnabled");
    }


    public static List<String> getAssets(AtlasEntity personaPolicyEntity) {
        return (List<String>) personaPolicyEntity.getAttribute("policyAssetQualifiedNames");
    }

    public static List<String> getPolicyGroups(AtlasEntity entity) {
        return (List<String>) entity.getAttribute("policyGroups");
    }

    public static List<String> getPolicyUsers(AtlasEntity entity) {
        return (List<String>) entity.getAttribute("policyUsers");
    }

    public static String getConnectionId(AtlasEntity personaPolicyEntity) {
        return (String) personaPolicyEntity.getAttribute("connectionGuid");
    }

    public static String getDisplayName(AtlasEntity entity) {
        return (String) entity.getAttribute("displayName");
    }

    public static String getDescription(AtlasEntity entity) {
        return (String) entity.getAttribute("description");
    }

    public static List<AtlasEntity> getPolicies(AtlasEntity.AtlasEntityWithExtInfo entityWithExtInfo) {
        List<AtlasObjectId> policies = (List<AtlasObjectId>) entityWithExtInfo.getEntity().getRelationshipAttribute("policies");

        return objectToEntityList(entityWithExtInfo, policies);
    }

    public static List<AtlasEntity> getMetadataPolicies(AtlasEntity.AtlasEntityWithExtInfo entityWithExtInfo) {
        List<AtlasEntity> policies = getPolicies(entityWithExtInfo);

        return policies.stream().filter(AtlasPersonaUtil::isMetadataPolicy).collect(Collectors.toList());
    }

    public static List<AtlasEntity> getDataPolicies(AtlasEntity.AtlasEntityWithExtInfo entityWithExtInfo) {
        List<AtlasEntity> policies = getPolicies(entityWithExtInfo);

        return policies.stream().filter(AtlasPersonaUtil::isDataPolicy).collect(Collectors.toList());
    }

    public static boolean isMetadataPolicy(AtlasEntity policyEntity) {
        return POLICY_TYPE_METADATA.equals(getPolicyType(policyEntity));
    }

    public static boolean isDataPolicy(AtlasEntity policyEntity) {
        return POLICY_TYPE_DATA.equals(getPolicyType(policyEntity));
    }

    public static boolean isDataMaskPolicy(AtlasEntity purposePolicy) {
        if (StringUtils.isNotEmpty(getDataPolicyMaskType(purposePolicy))) {
            return true;
        }
        return false;
    }

    public static String getPolicyType(AtlasEntity policyEntity) {
        return (String) policyEntity.getAttribute("accessControlPolicyType");
    }

    public static String getPolicyCategory(AtlasEntity policyEntity) {
        return (String) policyEntity.getAttribute("accessControlPolicyCategory");
    }

    public static List<AtlasEntity> objectToEntityList(AtlasEntity.AtlasEntityWithExtInfo entityWithExtInfo, List<AtlasObjectId> policies) {
        List<AtlasEntity> ret = new ArrayList<>();

        if (policies != null) {
            ret = policies.stream()
                    .map(x -> entityWithExtInfo.getReferredEntity(x.getGuid()))
                    .filter(x -> x.getStatus() == AtlasEntity.Status.ACTIVE)
                    .collect(Collectors.toList());
        }

        return ret;
    }

    public static List<String> getActions(AtlasEntity policyEntity) {
        return (List<String>) policyEntity.getAttribute("policyActions");
    }

    public static String getDataPolicyMaskType(AtlasEntity dataPolicy) {
        return (String) dataPolicy.getAttribute("dataMaskingOption");
    }

    public static void validateUniquenessByName(EntityDiscoveryService entityDiscoveryService, String name, String typeName) throws AtlasBaseException {
        IndexSearchParams indexSearchParams = new IndexSearchParams();
        Map<String, Object> dsl = mapOf("size", 1);

        List mustClauseList = new ArrayList();
        mustClauseList.add(mapOf("term", mapOf("__typeName.keyword", typeName)));
        mustClauseList.add(mapOf("term", mapOf("__state", "ACTIVE")));
        mustClauseList.add(mapOf("term", mapOf("name.keyword", name)));

        dsl.put("query", mapOf("bool", mapOf("must", mustClauseList)));

        indexSearchParams.setDsl(dsl);

        AtlasSearchResult atlasSearchResult = entityDiscoveryService.directIndexSearch(indexSearchParams);

        if (CollectionUtils.isNotEmpty(atlasSearchResult.getEntities())){
            throw new AtlasBaseException(String.format("Entity already exists, typeName:name, %s:%s", typeName, name));
        }
    }

    public static RangerPolicy fetchRangerPolicyByResources(AtlasRangerService atlasRangerService,
                                                            String serviceType,
                                                            String policyType,
                                                            RangerPolicy policy) throws AtlasBaseException {
        List<RangerPolicy> rangerPolicies = new ArrayList<>();

        Map<String, String> resourceForSearch = new HashMap<>();
        for (String resourceName : policy.getResources().keySet()) {

            RangerPolicy.RangerPolicyResource value = policy.getResources().get(resourceName);
            resourceForSearch.put(resourceName, value.getValues().get(0));
        }

        LOG.info("resourceForSearch {}", AtlasType.toJson(resourceForSearch));

        Map <String, String> params = new HashMap<>();
        int size = 25;
        int from = 0;

        params.put("policyType", policyType); //POLICY_TYPE_ACCESS
        params.put("page", "0");
        params.put("pageSize", String.valueOf(size));
        params.put("serviceType", serviceType);

        int fetched;
        do {
            params.put("startIndex", String.valueOf(from));

            List<RangerPolicy> rangerPoliciesPaginated = atlasRangerService.getPoliciesByResources(resourceForSearch, params);
            fetched = rangerPoliciesPaginated.size();
            rangerPolicies.addAll(rangerPoliciesPaginated);

            from += size;

        } while (fetched == size);

        if (CollectionUtils.isNotEmpty(rangerPolicies)) {
            //find exact match among the result list
            String provisionalPolicyResourcesSignature = new RangerPolicyResourceSignature(policy).getSignature();

            for (RangerPolicy resourceMatchedPolicy : rangerPolicies) {
                String resourceMatchedPolicyResourcesSignature = new RangerPolicyResourceSignature(resourceMatchedPolicy).getSignature();

                if (provisionalPolicyResourcesSignature.equals(resourceMatchedPolicyResourcesSignature) &&
                        Integer.valueOf(policyType).equals(resourceMatchedPolicy.getPolicyType()) &&
                        serviceType.equals(resourceMatchedPolicy.getServiceType())) {
                    return resourceMatchedPolicy;
                }
            }
        }

        return null;
    }

    public static List<RangerPolicy> fetchRangerPoliciesByLabel(AtlasRangerService atlasRangerService,
                                                                String serviceType,
                                                                String policyType,
                                                                String label) throws AtlasBaseException {
        List<RangerPolicy> ret = new ArrayList<>();

        Map <String, String> params = new HashMap<>();
        int size = 25;
        int from = 0;

        params.put("policyLabelsPartial", label);
        params.put("page", "0");
        params.put("pageSize", String.valueOf(size));

        if (StringUtils.isNotEmpty(serviceType)) {
            params.put("serviceType", serviceType);
        }

        if (StringUtils.isNotEmpty(policyType)) {
            params.put("policyType", policyType);
        }

        int fetched;
        do {
            params.put("startIndex", String.valueOf(from));

            List<RangerPolicy> rangerPolicies = atlasRangerService.getPoliciesByLabel(params);
            fetched = rangerPolicies.size();
            ret.addAll(rangerPolicies);

            from += size;

        } while (fetched == size);

        return ret;
    }

    public static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);

        return map;
    }

    public static void ensureNonAccessControlEntityType(List<String> types) throws AtlasBaseException {
        AtlasPerfMetrics.MetricRecorder metricRecorder = RequestContext.get().startMetricRecord("ensureNonAccessControlEntityType");
        long accessControlEntityCount = types.stream().filter(ACCESS_CONTROL_ENTITY_TYPES::contains).count();

        if (accessControlEntityCount > 0) {
            throw new AtlasBaseException(ACCESS_CONTROL_MUTATIONS_NOT_ALLOWED);
        }

        RequestContext.get().endMetricRecord(metricRecorder);
    }

    public static void ensureNonAccessControlRelType(String type) throws AtlasBaseException {
        AtlasPerfMetrics.MetricRecorder metricRecorder = RequestContext.get().startMetricRecord("ensureNonAccessControlRelType");
        if (ACCESS_CONTROL_RELATION_TYPE.equals(type)) {
            throw new AtlasBaseException(ACCESS_CONTROL_MUTATIONS_NOT_ALLOWED);
        }

        RequestContext.get().endMetricRecord(metricRecorder);
    }
}
