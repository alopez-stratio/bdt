package com.stratio.qa.utils;

import org.apache.http.HttpHost;
import org.json.JSONObject;
import org.opensearch.OpenSearchException;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.opensearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.GetMappingsRequest;
import org.opensearch.client.indices.GetMappingsResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
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

public class OpenSearchUtils extends RestClient.FailureListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchUtils.class);

    private String es_host;

    private int es_native_port;

    private RestHighLevelClient client;

    private Settings settings;


    public OpenSearchUtils() {
        this.es_host = System.getProperty("ES_NODE", "127.0.0.1");
        this.es_native_port = Integer.parseInt(System.getProperty("ES_NATIVE_PORT", "9200"));

    }

    public void connect(String keyStorePath, String keyStorePassword, String trustorePath, String trustorePassword) throws SSLException {
        HttpHost httpHost = new HttpHost(this.es_host, this.es_native_port, "https");
        SSLContext sslContext = initializeSSLContext(keyStorePath, keyStorePassword, trustorePath, trustorePassword);
        RestClientBuilder builder = RestClient.builder(httpHost).setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLContext(sslContext));
        this.client = new RestHighLevelClient(builder);
    }


    public void connect() {
        HttpHost httpHost = new HttpHost(this.es_host, this.es_native_port, "http");
        this.client = new org.opensearch.client.RestHighLevelClient(org.opensearch.client.RestClient.builder(httpHost).setFailureListener(this));
    }

    /**
     * Create an ES Index.
     *
     * @param indexName
     * @return true if the index has been created and false if the index has not been created.
     * @throws OpenSearchException
     */
    public boolean createSingleIndex(String indexName) {

        return createSingleIndex(indexName, Settings.builder());
    }

    public boolean createSingleIndex(String indexName, Settings.Builder settings) {
        CreateIndexRequest indexRequest = new CreateIndexRequest(indexName).settings(settings);
        try {
            this.client.indices().create(indexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new OpenSearchException("Error creating index: " + indexName);

        }
        return indexExists(indexName);
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
            throw new OpenSearchException("Error checking if index " + indexName + " exists");
        }
    }


    /**
     * Drop an ES Index
     *
     * @param indexName
     * @return true if the index exists
     * @throws OpenSearchException
     */
    public boolean dropSingleIndex(String indexName) throws OpenSearchException {

        org.opensearch.action.admin.indices.delete.DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        try {
            this.client.indices().delete(deleteIndexRequest, org.opensearch.client.RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new OpenSearchException("Error dropping index: " + indexName);
        }

        return indexExists(indexName);
    }


    public boolean dropAllIndexes() {

        boolean result = true;


        org.opensearch.client.indices.GetMappingsRequest request = new GetMappingsRequest();
        GetMappingsResponse response;

        try {
            response = client.indices().getMapping(request, org.opensearch.client.RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new OpenSearchException("Error getting indices names");

        }

        Map<String, MappingMetadata> mappings = response.mappings();

        for (String indexName : mappings.keySet()) {
            org.opensearch.action.admin.indices.delete.DeleteIndexRequest deleteIndexRequest = new org.opensearch.action.admin.indices.delete.DeleteIndexRequest(indexName);
            try {
                this.client.indices().delete(deleteIndexRequest, org.opensearch.client.RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new OpenSearchException("Error deleting index: " + indexName);
            }
            result = indexExists(indexName);
        }
        return result;
    }


    /**
     * Get Index Setting
     *
     * @param indexName
     * @param settingName
     * @return string with index setting
     */
    public String getOpenSearchIndexSetting(String indexName, String settingName) {

        try {
            org.opensearch.action.admin.indices.settings.get.GetSettingsRequest request = new GetSettingsRequest().indices(indexName).names(settingName).includeDefaults(true);
            GetSettingsResponse settingsResponse = client.indices().getSettings(request, org.opensearch.client.RequestOptions.DEFAULT);

            return settingsResponse.getSetting(indexName, settingName);
        } catch (IOException e) {
            throw new OpenSearchException("Error getting setting " + settingName + "from index " + indexName);
        }
    }

    /**
     * Get Index Setting
     *
     * @param indexName
     * @return string with index replicas
     */
    public String getNumberOfReplicasFromIndex(String indexName) {
        return getOpenSearchIndexSetting(indexName, "index.number_of_replicas");
    }

    /**
     * Get Index Setting
     *
     * @param indexName
     * @return string with index shards
     */
    public String getNumberOfShardsFromIndex(String indexName) {
        return getOpenSearchIndexSetting(indexName, "index.number_of_shards");
    }

    /**
     * Simulate a SELET * FROM index.mapping WHERE (One simple filter)
     *
     * @param indexName
     * @param columnName
     * @param value
     * @param filterType [equals, gt, gte, lt, lte]
     * @return ArrayList with all the rows(One element of the ArrayList is a JSON document)
     * @throws Exception
     */
    public List<JSONObject> searchSimpleFilterOpensearchQuery(String indexName, String columnName, Object value, String filterType) throws Exception {
        List<JSONObject> resultsJSON = new ArrayList<JSONObject>();
        QueryBuilder query;
        switch (filterType) {
            case "equals":
                query = org.opensearch.index.query.QueryBuilders.termQuery(columnName, value);
                break;
            case "gt":
                query = org.opensearch.index.query.QueryBuilders.rangeQuery(columnName).gt(value);
                break;
            case "gte":
                query = org.opensearch.index.query.QueryBuilders.rangeQuery(columnName).gte(value);
                break;
            case "lt":
                query = org.opensearch.index.query.QueryBuilders.rangeQuery(columnName).lt(value);
                break;
            case "lte":
                query = org.opensearch.index.query.QueryBuilders.rangeQuery(columnName).lte(value);
                break;
            default:
                throw new Exception("Filter not implemented in the library");
        }

        org.opensearch.search.builder.SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query).timeout(new TimeValue(60, TimeUnit.SECONDS));
        org.opensearch.action.search.SearchRequest searchRequest = new SearchRequest().indices(indexName).source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, org.opensearch.client.RequestOptions.DEFAULT);
        org.opensearch.search.SearchHit[] hits = searchResponse.getHits().getHits();
        for (SearchHit hit : hits) {

            resultsJSON.add(new JSONObject(hit.getSourceAsString()));
        }
        return resultsJSON;
    }


    /**
     * Indexes a document.
     *
     * @param indexName
     * @param id        unique identifier of the document
     * @param document
     * @throws Exception
     */
    public void indexDocument(String indexName, String id, String document) {
        org.opensearch.action.index.IndexRequest request = new org.opensearch.action.index.IndexRequest(indexName).id(id).source(document, XContentType.JSON);
        try {
            client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new OpenSearchException(e.getMessage());
        }
    }


    /**
     * Indexes a document.
     *
     * @param indexName
     * @param id        unique identifier of the document
     * @param document
     * @throws Exception
     */
    public void indexDocument(String indexName, String id, XContentBuilder document) {
        org.opensearch.action.index.IndexRequest request = new org.opensearch.action.index.IndexRequest(indexName).id(id).source(document);
        try {
            client.index(request, org.opensearch.client.RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new OpenSearchException("Error indexing document");
        }
    }


    /**
     * Indexes a document.
     *
     * @param indexName
     * @param id        unique identifier of the document
     * @throws OpenSearchException
     */
    public boolean existsDocument(String indexName, String id) {
        org.opensearch.action.get.GetRequest request = new GetRequest(indexName, id);

        request.fetchSourceContext(new FetchSourceContext(false));
        request.storedFields("_none_");

        try {
            return client.exists(request, org.opensearch.client.RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new OpenSearchException("Error indexing document");
        }
    }

    /**
     * Deletes a document by its id.
     *
     * @param indexName
     * @param id
     */

    public void deleteDocument(String indexName, String id) {
        org.opensearch.action.delete.DeleteRequest deleteRequest = new DeleteRequest(indexName, id);
        try {

            client.delete(deleteRequest, RequestOptions.DEFAULT);

        } catch (IOException e) {
            throw new OpenSearchException(e.getMessage());
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
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException |
                 UnrecoverableKeyException | KeyManagementException e) {
            throw new SSLException("Cannot initialize SSL Context ", e);
        }
    }

    public void setNativePort(Integer port) {
        this.es_native_port = port;
    }

    public void setHost(String host) {
        this.es_host = host;
    }

    public RestHighLevelClient getClient() {
        return this.client;
    }

    public org.opensearch.common.settings.Settings getSettings() {
        return this.settings;
    }

    public void setSettings(LinkedHashMap<String, Object> settings) {
        org.opensearch.common.settings.Settings.Builder builder = org.opensearch.common.settings.Settings.builder();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().toString());
        }
        this.settings = builder.build();
    }


}
