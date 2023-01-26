package org.apache.atlas.model.lineage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.atlas.model.discovery.SearchParameters;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.PUBLIC_ONLY;

@JsonAutoDetect(getterVisibility = PUBLIC_ONLY, setterVisibility = PUBLIC_ONLY, fieldVisibility = NONE)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class LineageOnDemandRequest {
    private Map<String, LineageOnDemandConstraints> constraints;
    private List<SearchParameters.FilterCriteria>   traversalFilters;
    private Set<String>                             attributes;
    private Set<String>                             relationAttributes;
    private LineageOnDemandBaseParams               defaultParams;

    public LineageOnDemandRequest() {
        this.attributes = new HashSet<>();
        this.relationAttributes = new HashSet<>();
        this.defaultParams = new LineageOnDemandBaseParams();
    }

    public LineageOnDemandRequest(LineageOnDemandBaseParams defaultParams) {
        this.attributes = new HashSet<>();
        this.relationAttributes = new HashSet<>();
        this.defaultParams = defaultParams;
    }

    public LineageOnDemandRequest(Map<String, LineageOnDemandConstraints> constraints) {
        this.constraints = constraints;
        this.attributes = new HashSet<>();
        this.relationAttributes = new HashSet<>();
    }

    public LineageOnDemandRequest(Map<String, LineageOnDemandConstraints> constraints, List<SearchParameters.FilterCriteria> traversalFilters, Set<String> attributes, Set<String> relationAttributes) {
        this.defaultParams = new LineageOnDemandBaseParams();
    }

    public LineageOnDemandRequest(Map<String, LineageOnDemandConstraints> constraints, LineageOnDemandBaseParams defaultParams) {
        this.constraints = constraints;
        this.attributes = new HashSet<>();
        this.relationAttributes = new HashSet<>();
        this.defaultParams = defaultParams;
    }

    public LineageOnDemandRequest(Map<String, LineageOnDemandConstraints> constraints, List<SearchParameters.FilterCriteria> traversalFilters, LineageOnDemandBaseParams defaultParams) {
        this.constraints = constraints;
        this.attributes = new HashSet<>();
        this.relationAttributes = new HashSet<>();
        this.defaultParams = defaultParams;
    }

    public LineageOnDemandRequest(Map<String, LineageOnDemandConstraints> constraints, List<SearchParameters.FilterCriteria> traversalFilters,
                                  Set<String> attributes, Set<String> relationAttributes, LineageOnDemandBaseParams defaultParams) {
        this.constraints        = constraints;
        this.traversalFilters   = traversalFilters;
        this.attributes         = attributes;
        this.relationAttributes = relationAttributes;
        this.defaultParams      = defaultParams;
    }

    public Map<String, LineageOnDemandConstraints> getConstraints() {
        return constraints;
    }

    public void setConstraints(Map<String, LineageOnDemandConstraints> constraints) {
        this.constraints = constraints;
    }

    public List<SearchParameters.FilterCriteria> getTraversalFilters() {
        return traversalFilters;
    }

    public void setTraversalFilters(List<SearchParameters.FilterCriteria> traversalFilters) {
        this.traversalFilters = traversalFilters;
    }

    public Set<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Set<String> attributes) {
        this.attributes = attributes;
    }

    public Set<String> getRelationAttributes() {
        return relationAttributes;
    }

    public void setRelationAttributes(Set<String> relationAttributes) {
        this.relationAttributes = relationAttributes;
    }
    
    public LineageOnDemandBaseParams getDefaultParams() {
        return defaultParams;
    }

    public void setDefaultParams(LineageOnDemandBaseParams defaultParams) {
        this.defaultParams = defaultParams;
    }
}
