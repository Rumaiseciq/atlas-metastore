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
package org.apache.atlas.repository.graphdb.janus;

import org.apache.atlas.AtlasErrorCode;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.discovery.SearchParams;
import org.apache.atlas.repository.graphdb.AtlasIndexQuery;
import org.apache.atlas.repository.graphdb.AtlasVertex;
import org.apache.atlas.repository.graphdb.DirectIndexQueryResult;
import org.apache.atlas.type.AtlasType;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.janusgraph.util.encoding.LongEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.apache.atlas.AtlasErrorCode.INDEX_NOT_FOUND;


public class AtlasElasticsearchQuery implements AtlasIndexQuery<AtlasJanusVertex, AtlasJanusEdge> {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasElasticsearchQuery.class);

    private AtlasJanusGraph graph;
    private RestHighLevelClient esClient;
    private RestClient lowLevelRestClient;
    private String index;
    private SearchSourceBuilder sourceBuilder;
    private SearchResponse searchResponse;
    private SearchParams searchParams;
    private long vertexTotals = -1;

    public AtlasElasticsearchQuery(AtlasJanusGraph graph, RestHighLevelClient esClient, String index, SearchSourceBuilder sourceBuilder) {
        this(graph, index);
        this.esClient = esClient;
        this.sourceBuilder = sourceBuilder;
    }

    public AtlasElasticsearchQuery(AtlasJanusGraph graph, RestClient restClient, String index, SearchParams searchParams) {
        this(graph, index);
        this.lowLevelRestClient = restClient;
        this.searchParams = searchParams;
    }

    private AtlasElasticsearchQuery(AtlasJanusGraph graph, String index) {
        this.index = index;
        this.graph = graph;
        searchResponse = null;
    }

    public AtlasElasticsearchQuery(AtlasJanusGraph graph, String index, RestClient restClient) {
        this(graph, restClient, index, null);
    }

    public AtlasElasticsearchQuery(String index, RestClient restClient) {
        this.lowLevelRestClient = restClient;
        this.index = index;
    }

    private SearchRequest getSearchRequest(String index, SearchSourceBuilder sourceBuilder) {
        SearchRequest searchRequest = new SearchRequest(index);
        searchRequest.source(sourceBuilder);
        return searchRequest;
    }

    private Iterator<Result<AtlasJanusVertex, AtlasJanusEdge>> runQuery(SearchRequest searchRequest) {
        Iterator<Result<AtlasJanusVertex, AtlasJanusEdge>> result = null;

        try {
            RequestOptions requestOptions = RequestOptions.DEFAULT;
            RequestOptions.Builder builder = requestOptions.toBuilder();
            int bufferLimit = 2000 * 1024 * 1024;
            builder.setHttpAsyncResponseConsumerFactory(
                    new HttpAsyncResponseConsumerFactory
                            .HeapBufferedResponseConsumerFactory(bufferLimit));
            requestOptions = builder.build();
            searchResponse = esClient.search(searchRequest, requestOptions);
            Stream<Result<AtlasJanusVertex, AtlasJanusEdge>> resultStream = Arrays.stream(searchResponse.getHits().getHits())
                    .map(ResultImpl::new);
            result = resultStream.iterator();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private DirectIndexQueryResult runQueryWithLowLevelClient(SearchParams searchParams) throws AtlasBaseException {
        DirectIndexQueryResult result = null;

        try {

            String responseString =  performDirectIndexQuery(searchParams.getQuery(), false);
            if (LOG.isDebugEnabled()) {
                LOG.debug("runQueryWithLowLevelClient.response : {}", responseString);
            }
            
            result = getResultFromResponse(responseString);

        } catch (IOException e) {
            LOG.error("Failed to execute direct query on ES {}", e.getMessage());
            throw new AtlasBaseException(AtlasErrorCode.INDEX_SEARCH_FAILED, e.getMessage());
        }

        return result;
    }

    private Map<String, Object> runQueryWithLowLevelClient(String query) throws AtlasBaseException {
        Map<String, Object> ret = new HashMap<>();
        try {
            String responseString = performDirectIndexQuery(query, true);

            Map<String, LinkedHashMap> responseMap = AtlasType.fromJson(responseString, Map.class);
            Map<String, LinkedHashMap> hits_0 = AtlasType.fromJson(AtlasType.toJson(responseMap.get("hits")), Map.class);

            ret.put("total", hits_0.get("total").get("value"));

            List<LinkedHashMap> hits_1 = AtlasType.fromJson(AtlasType.toJson(hits_0.get("hits")), List.class);
            ret.put("data", hits_1);

            Map<String, Object> aggregationsMap = (Map<String, Object>) responseMap.get("aggregations");

            if (MapUtils.isNotEmpty(aggregationsMap)) {
                ret.put("aggregations", aggregationsMap);
            }

            return ret;

        } catch (IOException e) {
            LOG.error("Failed to execute direct query on ES {}", e.getMessage());
            throw new AtlasBaseException(AtlasErrorCode.INDEX_SEARCH_FAILED, e.getMessage());
        }
    }

    private String performDirectIndexQuery(String query, boolean source) throws AtlasBaseException, IOException {
        HttpEntity entity = new NStringEntity(query, ContentType.APPLICATION_JSON);
        String endPoint;

        if (source) {
            endPoint = index + "/_search";
        } else {
            endPoint = index + "/_search?_source=false";
        }

        Request request = new Request("GET", endPoint);
        request.setEntity(entity);

        Response response;
        try {
            response = lowLevelRestClient.performRequest(request);
        } catch (ResponseException rex) {
            if (rex.getResponse().getStatusLine().getStatusCode() == 404) {
                LOG.warn(String.format("ES index with name %s not found", index));
                throw new AtlasBaseException(INDEX_NOT_FOUND, index);
            } else {
                throw new AtlasBaseException(rex);
            }
        }

        return EntityUtils.toString(response.getEntity());
    }

    private DirectIndexQueryResult getResultFromResponse(String responseString) {
        DirectIndexQueryResult result = new DirectIndexQueryResult();

        Map<String, LinkedHashMap> responseMap = AtlasType.fromJson(responseString, Map.class);

        Map<String, LinkedHashMap> hits_0 = AtlasType.fromJson(AtlasType.toJson(responseMap.get("hits")), Map.class);
        this.vertexTotals = (Integer) hits_0.get("total").get("value");

        List<LinkedHashMap> hits_1 = AtlasType.fromJson(AtlasType.toJson(hits_0.get("hits")), List.class);

        Stream<Result<AtlasJanusVertex, AtlasJanusEdge>> resultStream = hits_1.stream().map(ResultImplDirect::new);
        result.setIterator(resultStream.iterator());

        Map<String, Object> aggregationsMap = (Map<String, Object>) responseMap.get("aggregations");

        if (MapUtils.isNotEmpty(aggregationsMap)) {
            result.setAggregationMap(aggregationsMap);
        }

        return result;
    }

    @Override
    public DirectIndexQueryResult<AtlasJanusVertex, AtlasJanusEdge> vertices(SearchParams searchParams) throws AtlasBaseException {
        return runQueryWithLowLevelClient(searchParams);
    }

    @Override
    public Map<String, Object> directIndexQuery(String query) throws AtlasBaseException {
        return runQueryWithLowLevelClient(query);
    }

    @Override
    public Iterator<Result<AtlasJanusVertex, AtlasJanusEdge>> vertices() {
        SearchRequest searchRequest = getSearchRequest(index, sourceBuilder);
        return runQuery(searchRequest);
    }

    @Override
    public Iterator<Result<AtlasJanusVertex, AtlasJanusEdge>> vertices(int offset, int limit, String sortBy, Order sortOrder) {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<Result<AtlasJanusVertex, AtlasJanusEdge>> vertices(int offset, int limit) {
        sourceBuilder.from(offset);
        sourceBuilder.size(limit);
        SearchRequest searchRequest = getSearchRequest(index, sourceBuilder);
        return runQuery(searchRequest);
    }

    @Override
    public Long vertexTotals() {
        if (searchResponse != null) {
            return searchResponse.getHits().getTotalHits().value;
        }
        return vertexTotals;
    }

    public final class ResultImpl implements AtlasIndexQuery.Result<AtlasJanusVertex, AtlasJanusEdge> {
        private SearchHit hit;

        public ResultImpl(SearchHit hit) {
            this.hit = hit;
        }

        @Override
        public AtlasVertex<AtlasJanusVertex, AtlasJanusEdge> getVertex() {
            long vertexId = LongEncoding.decode(hit.getId());
            return graph.getVertex(String.valueOf(vertexId));
        }

        @Override
        public double getScore() {
            return hit.getScore();
        }

        @Override
        public Set<String> getCollapseKeys() {
            return null;
        }

        @Override
        public DirectIndexQueryResult<AtlasJanusVertex, AtlasJanusEdge> getCollapseVertices(String key) {
            return null;
        }

        @Override
        public Map<String, List<String>> getHighLights() {
            return new HashMap<>();
        }
    }


    public final class ResultImplDirect implements AtlasIndexQuery.Result<AtlasJanusVertex, AtlasJanusEdge> {
        private LinkedHashMap<String, Object> hit;
        Map<String, LinkedHashMap> innerHitsMap;

        public ResultImplDirect(LinkedHashMap<String, Object> hit) {
            this.hit = hit;
            if (hit.get("inner_hits") != null) {
                innerHitsMap = AtlasType.fromJson(AtlasType.toJson(hit.get("inner_hits")), Map.class);
            }
        }

        @Override
        public AtlasVertex<AtlasJanusVertex, AtlasJanusEdge> getVertex() {
            long vertexId = LongEncoding.decode(String.valueOf(hit.get("_id")));
            return graph.getVertex(String.valueOf(vertexId));
        }

        @Override
        public Set<String> getCollapseKeys() {
            Set<String> collapseKeys = new HashSet<>();
            if (innerHitsMap != null) {
                collapseKeys = innerHitsMap.keySet();
            }
            return collapseKeys;
        }

        @Override
        public DirectIndexQueryResult getCollapseVertices(String key) {
            DirectIndexQueryResult result = new DirectIndexQueryResult();
            if (innerHitsMap != null) {
                Map<String, LinkedHashMap> responseMap = AtlasType.fromJson(AtlasType.toJson(innerHitsMap.get(key)), Map.class);
                Map<String, LinkedHashMap> hits_0 = AtlasType.fromJson(AtlasType.toJson(responseMap.get("hits")), Map.class);

                Integer approximateCount = (Integer) hits_0.get("total").get("value");
                result.setApproximateCount(approximateCount);

                List<LinkedHashMap> hits_1 = AtlasType.fromJson(AtlasType.toJson(hits_0.get("hits")), List.class);
                Stream<Result<AtlasJanusVertex, AtlasJanusEdge>> resultStream = hits_1.stream().map(ResultImplDirect::new);
                result.setIterator(resultStream.iterator());

                return result;
            }
            return null;
        }

        @Override
        public double getScore() {
            Object _score = hit.get("_score");
            if (_score == null){
                return -1;
            }
            return Double.parseDouble(String.valueOf(hit.get("_score")));
        }

        @Override
        public Map<String, List<String>> getHighLights() {
            Object highlight = this.hit.get("highlight");
            if(Objects.nonNull(highlight)) {
                return (Map<String, List<String>>) highlight;
            }
            return new HashMap<>();
        }
    }
}