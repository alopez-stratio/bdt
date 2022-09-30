/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.utils;

import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.GetMappingsResponse;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ElasticSearchUtils extends RestClient.FailureListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchUtil.class);

    private String es_host;

    private int es_native_port;

    private RestHighLevelClient client;

    private Settings settings;

    /**
     * Default constructor.
     */
    public ElasticSearchUtils() {
        this.es_host = System.getProperty("ES_NODE", "127.0.0.1");
        this.es_native_port = Integer.valueOf(System.getProperty("ES_NATIVE_PORT", "9200"));
    }

    public Settings getSettings() {
        return this.settings;
    }

    /**
     * Set settings about ES connector.
     *
     * @param settings : LinkedHashMap with all the settings about ES connection
     */
    public void setSettings(LinkedHashMap<String, Object> settings) {
        Settings.Builder builder = Settings.builder();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().toString());
        }
        this.settings = builder.build();
    }

    public void setHost(String host) {
        this.es_host = host;
    }

    public void setNativePort(Integer port) {
        this.es_native_port = port;
    }

    /**
     * Connect to ES.
     */

    public void connect(String keyStorePath, String  keyStorePassword, String  trustorePath, String trustorePassword) throws SSLException {
        HttpHost httpHost = new HttpHost(this.es_host, this.es_native_port, "https");
        SSLContext sslContext = initializeSSLContext(keyStorePath, keyStorePassword, trustorePath, trustorePassword);
        this.client = new RestHighLevelClient(RestClient.builder(httpHost).setFailureListener(this).setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLContext(sslContext)));

    }

    public void connect() {

        HttpHost httpHost = new HttpHost(this.es_host, this.es_native_port, "http");
        this.client = new RestHighLevelClient(RestClient.builder(httpHost).setFailureListener(this));
    }


    /**
     * Get ES client(Connected previously).
     *
     * @return es client
     */
    public RestHighLevelClient getClient() {
        return this.client;
    }

    /**
     * Create an ES Index.
     *
     * @param indexName
     * @return true if the index has been created and false if the index has not been created.
     * @throws ElasticsearchException
     */
    public boolean createSingleIndex(String indexName) {

        return createSingleIndex(indexName, Settings.builder());
    }


    public boolean createSingleIndex(String indexName, Settings.Builder settings) {
        CreateIndexRequest indexRequest = new CreateIndexRequest(indexName).settings(settings);
        try {
            this.client.indices().create(indexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error creating index: " + indexName);

        }
        return indexExists(indexName);
    }

    /**
     * Drop an ES Index
     *
     * @param indexName
     * @return true if the index exists
     * @throws ElasticsearchException
     */
    public boolean dropSingleIndex(String indexName) throws ElasticsearchException {

        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        try {
            this.client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error dropping index: " + indexName);
        }

        return indexExists(indexName);
    }

    public boolean dropAllIndexes() {

        boolean result = true;


        GetMappingsRequest request = new GetMappingsRequest();
        GetMappingsResponse response;

        try {
            response  = client.indices().getMapping(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error getting indices names");

        }

        Map<String, MappingMetaData> mappings = response.mappings();

        for (String indexName : mappings.keySet()) {
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
            try {
                this.client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new ElasticsearchException("Error deleting index: " + indexName);
            }
            result = indexExists(indexName);
        }
        return result;
    }

    /**
     * Check if an index exists in ES
     *
     * @param indexName
     * @return true if the index exists or false if the index does not exits.
     */
    public boolean indexExists(String indexName) {
        try {
            GetIndexRequest request = new GetIndexRequest(indexName);

            return client.indices().exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error checking if index " + indexName + " exists");
        }
    }

    /**
     * Get Index Setting
     *
     * @param indexName
     * @param settingName
     * @return string with index setting
     */

    public String getElasticsearchIndexSetting(String indexName, String settingName) {

        try {
            GetSettingsRequest request = new GetSettingsRequest().indices(indexName).names(settingName).includeDefaults(true);
            GetSettingsResponse settingsResponse = client.indices().getSettings(request, RequestOptions.DEFAULT);

            return settingsResponse.getSetting(indexName, settingName);
        } catch (IOException e) {
            throw new ElasticsearchException("Error getting setting " + settingName + "from index " + indexName);
        }
    }

    /**
     * Get Index Setting
     *
     * @param indexName
     * @return string with index replicas
     */
    public String getNumberOfReplicasFromIndex(String indexName) {
        return getElasticsearchIndexSetting(indexName, "index.number_of_replicas");
    }

    /**
     * Get Index Setting
     *
     * @param indexName
     * @return string with index shards
     */
    public String getNumberOfShardsFromIndex(String indexName) {
        return getElasticsearchIndexSetting(indexName, "index.number_of_shards");
    }

    /**
     * Simulate a SELET * FROM index.mapping WHERE (One simple filter)
     *
     * @param indexName
     * @param columnName
     * @param value
     * @param filterType  [equals, gt, gte, lt, lte]
     * @return ArrayList with all the rows(One element of the ArrayList is a JSON document)
     * @throws Exception
     */
    public List<JSONObject> searchSimpleFilterElasticsearchQuery(String indexName, String columnName, Object value, String filterType) throws Exception {
        List<JSONObject> resultsJSON = new ArrayList<JSONObject>();
        QueryBuilder query;
        switch (filterType) {
            case "equals":
                query = QueryBuilders.termQuery(columnName, value);
                break;
            case "gt":
                query = QueryBuilders.rangeQuery(columnName).gt(value);
                break;
            case "gte":
                query = QueryBuilders.rangeQuery(columnName).gte(value);
                break;
            case "lt":
                query = QueryBuilders.rangeQuery(columnName).lt(value);
                break;
            case "lte":
                query = QueryBuilders.rangeQuery(columnName).lte(value);
                break;
            default:
                throw new Exception("Filter not implemented in the library");
        }

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query).timeout(new TimeValue(60, TimeUnit.SECONDS));
        SearchRequest searchRequest = new SearchRequest().indices(indexName).source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] hits = searchResponse.getHits().getHits();
        for (SearchHit hit : hits) {



            resultsJSON.add(new JSONObject(hit.getSourceAsString()));
        }
        return resultsJSON;
    }

    /**
     * Indexes a document.
     *
     * @param indexName
     * @param id          unique identifier of the document
     * @param document
     * @throws Exception
     */
    public void indexDocument(String indexName, String id, String document) {
        IndexRequest request = new IndexRequest(indexName).id(id).source(document, XContentType.JSON);
        try {
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error indexing document");
        }
    }

    /**
     * Indexes a document.
     *
     * @param indexName
     * @param id          unique identifier of the document
     * @param document
     * @throws Exception
     */
    public void indexDocument(String indexName, String id, XContentBuilder document) {
        IndexRequest request = new IndexRequest(indexName).id(id).source(document);
        try {
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error indexing document");
        }
    }

    /**
     * Indexes a document.
     *
     * @param indexName
     * @param id          unique identifier of the document
     * @throws Exception
     */
    public boolean existsDocument(String indexName, String id) {
        GetRequest request = new GetRequest(indexName, id);

        request.fetchSourceContext(new FetchSourceContext(false));
        request.storedFields("_none_");

        try {
            return client.exists(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ElasticsearchException("Error indexing document");
        }
    }

    /**
     * Deletes a document by its id.
     *
     * @param indexName
     *
     * @param id
     */
    public void deleteDocument(String indexName, String id) {
        DeleteRequest deleteRequest = new DeleteRequest(indexName, id);
        try {

            client.delete(deleteRequest, RequestOptions.DEFAULT);

        } catch (IOException e) {
            throw new ElasticsearchException("Error deleting document");
        }
    }

    private static SSLContext initializeSSLContext(String keyStore, String keyStorePass, String truststore, String truststorePass) throws SSLException {
        try {

            Path keyStorePath = Paths.get(keyStore);
            Path truststorePath = Paths.get(truststore);
            LOGGER.info("Getting the keystore path which is {} also getting truststore path {}", keyStorePath, truststorePath);

            SSLContext sc = SSLContext.getInstance("TLSv1.2");

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream is = Files.newInputStream(keyStorePath)) {
                ks.load(is, keyStorePass.toCharArray());
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(ks, keyStorePass.toCharArray());


            KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream is = Files.newInputStream(truststorePath)) {
                ts.load(is, truststorePass.toCharArray());
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(ts);

            sc.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());
            return sc;
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyManagementException e) {
            throw new SSLException("Cannot initialize SSL Context ", e);
        }
    }
}
