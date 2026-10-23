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

package com.stratio.qa.specs;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.Row;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import io.cucumber.java.PendingException;
import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import org.asynchttpclient.Response;
import com.stratio.qa.assertions.DBObjectsAssert;
import com.stratio.qa.exceptions.DBException;
import com.stratio.qa.utils.CrossdataOAuth2Browser;
import com.stratio.qa.utils.CrossdataOAuth2Settings;
import com.stratio.qa.utils.JDBCConnection;
import com.stratio.qa.utils.SqlStatementSplitter;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.datatable.DataTable;
import org.assertj.core.api.Assertions;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.testng.Assert;
import org.testng.asserts.Assertion;

import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.stratio.qa.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Generic Database Specs.
 *
 * @see <a href="DatabaseSpec-annotations.html">Database Steps &amp; Matching Regex</a>
 */
public class DatabaseSpec extends BaseGSpec {

    public static final Integer ES_DEFAULT_NATIVE_PORT = 9300;

    public static final String ES_DEFAULT_CLUSTER_NAME = "elasticsearch";

    public static final int VALUE_SUBSTRING = 3;

    /**
     * Max number of query response rows detailed in a table comparison failure, to keep the message readable.
     */
    public static final int MAX_ROWS_IN_COMPARISON_FAILURE = 20;

    /**
     * Marker a SQL NULL is stored as, so it stays comparable against a datatable cell.
     */
    public static final String EMPTY_VALUE = "<EMPTY>";

    /**
     * Directory the Vault certificates are downloaded into.
     */
    public static final String CERTIFICATES_PATH = "target/test-classes/";

    public static final String POSTGRES_DEFAULT_PORT = "5432";

    public static final String POSTGRES_DEFAULT_USER = "postgres";

    /**
     * Vault certificate entry the Postgres steps download when the feature names none.
     */
    public static final String POSTGRES_DEFAULT_CERTIFICATE = "postgres";

    private Scenario scenario;

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public DatabaseSpec(CommonG spec) {
        this.commonspec = spec;

    }

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
        // Every scenario starts with basic authentication, whatever the previous one configured
        commonspec.setCrossdataOAuth2Settings(null);
        commonspec.setCrossdataOAuth2Browser(null);
        CrossdataOAuth2Settings.clearDriverSettings();
    }

    /**
     * Create a basic Index.
     *
     * @param index_name index name
     * @param table      the table where index will be created.
     * @param column     the column where index will be saved
     * @param keyspace   keyspace used
     * @throws Exception exception
     */
    @Given("^I create a Cassandra index named '(.+?)' in table '(.+?)' using magic_column '(.+?)' using keyspace '(.+?)'$")
    public void createBasicMapping(String index_name, String table, String column, String keyspace) throws Exception {
        String query = "CREATE INDEX " + index_name + " ON " + table + " (" + column + ");";
        commonspec.getCassandraClient().executeQuery(query);
    }

    /**
     * Create a Cassandra Keyspace.
     *
     * @param keyspace cassandra keyspace
     */
    @Given("^I create a Cassandra keyspace named '(.+)'$")
    public void createCassandraKeyspace(String keyspace) {
        commonspec.getCassandraClient().createKeyspace(keyspace);
    }

    /**
     * Connect to cluster.
     *
     * @param clusterType DB type (Cassandra|Mongo|Elasticsearch|OpenSearch)
     * @param url         url where is started Cassandra cluster
     */
    @Given("^I( securely)? connect to '(Cassandra|Mongo|Elasticsearch|OpenSearch)' cluster at '(.+)'$")
    public void connect(String secured, String clusterType, String url) throws DBException, UnknownHostException {
        switch (clusterType) {
            case "Cassandra":
                commonspec.getCassandraClient().setHost(url);
                commonspec.getCassandraClient().connect(secured);
                break;
            case "Mongo":
                commonspec.getMongoDBClient().connect();
                break;
            case "Elasticsearch":
                LinkedHashMap<String, Object> settings_map = new LinkedHashMap<String, Object>();
                settings_map.put("cluster.name", System.getProperty("ES_CLUSTER", ES_DEFAULT_CLUSTER_NAME));
                commonspec.getElasticSearchClient().setSettings(settings_map);
                commonspec.getElasticSearchClient().connect();
                break;
            case "OpenSearch":
                LinkedHashMap<String, Object> settings_map_open_search = new LinkedHashMap<String, Object>();
                settings_map_open_search.put("cluster.name", System.getProperty("ES_CLUSTER", ES_DEFAULT_CLUSTER_NAME));
                commonspec.getOpenSearchClient().setSettings(settings_map_open_search);
                commonspec.getOpenSearchClient().connect();
                break;
            default:
                throw new DBException("Unknown cluster type");
        }
    }

    /**
     * Connect to ElasticSearch using custom parameters
     *
     * @param host        ES host
     * @param nativePort  ES port
     * @param clusterName ES clustername
     * @param keyStorePath File keystore in format .jks
     * @param keyStorePassword KeyStore Password
     * @throws NumberFormatException exception
     */
    @Given("^I connect to Elasticsearch cluster at host '(.+?)'( using native port '(.+?)')?( with trustStorePath '(.+?)')?( and trustStorePassword '(.+?)')?( with keyStorePath '(.+?)')?( and keyStorePassword '(.+?)')?( using cluster name '(.+?)')?$")
    public void connectToElasticSearch(String host, String nativePort, String trustStorePath, String trustStorePassword, String keyStorePath, String keyStorePassword, String clusterName) throws NumberFormatException, SSLException {
        LinkedHashMap<String, Object> settings_map = new LinkedHashMap<String, Object>();
        if (clusterName != null) {
            settings_map.put("cluster.name", clusterName);
        } else {
            settings_map.put("cluster.name", ES_DEFAULT_CLUSTER_NAME);
        }
        commonspec.getElasticSearchClient().setSettings(settings_map);
        if (nativePort != null) {
            commonspec.getElasticSearchClient().setNativePort(Integer.valueOf(nativePort));
        } else {
            commonspec.getElasticSearchClient().setNativePort(ES_DEFAULT_NATIVE_PORT);
        }
        commonspec.getElasticSearchClient().setHost(host);

        if (trustStorePath != null && trustStorePassword != null && keyStorePath != null  && keyStorePassword != null) {
            commonspec.getElasticSearchClient().connect(keyStorePath, keyStorePassword, trustStorePath, trustStorePassword);
        } else {
            commonspec.getElasticSearchClient().connect();
        }
    }

    /**
     * Connect to OpenSearch using custom parameters
     *
     * @param host        ES host
     * @param nativePort  ES port
     * @param clusterName ES clustername
     * @param keyStorePath File keystore in format .jks
     * @param keyStorePassword KeyStore Password
     * @throws NumberFormatException exception
     */
    @Given("^I connect to OpenSearch cluster at host '(.+?)'( using native port '(.+?)')?( with trustStorePath '(.+?)')?( and trustStorePassword '(.+?)')?( with keyStorePath '(.+?)')?( and keyStorePassword '(.+?)')?( using cluster name '(.+?)')?$")
    public void connectToOpenSearch(String host, String nativePort, String trustStorePath, String trustStorePassword, String keyStorePath, String keyStorePassword, String clusterName) throws NumberFormatException, SSLException {
        LinkedHashMap<String, Object> settings_map = new LinkedHashMap<String, Object>();
        if (clusterName != null) {
            settings_map.put("cluster.name", clusterName);
        } else {
            settings_map.put("cluster.name", ES_DEFAULT_CLUSTER_NAME);
        }
        commonspec.getOpenSearchClient().setSettings(settings_map);
        if (nativePort != null) {
            commonspec.getOpenSearchClient().setNativePort(Integer.valueOf(nativePort));
        } else {
            commonspec.getOpenSearchClient().setNativePort(ES_DEFAULT_NATIVE_PORT);
        }
        commonspec.getOpenSearchClient().setHost(host);

        if (trustStorePath != null && trustStorePassword != null && keyStorePath != null  && keyStorePassword != null) {
            commonspec.getOpenSearchClient().connect(keyStorePath, keyStorePassword, trustStorePath, trustStorePassword);
        } else {
            commonspec.getOpenSearchClient().connect();
        }
    }

    /**
     * Create table
     *
     * @param table     Cassandra table
     * @param datatable datatable used for parsing elements
     * @param keyspace  Cassandra keyspace
     */
    @Given("^I create a Cassandra table named '(.+?)' using keyspace '(.+?)' with:$")
    public void createTableWithData(String table, String keyspace, DataTable datatable) {
        try {
            commonspec.getCassandraClient().useKeyspace(keyspace);
            int attrLength = datatable.cells().get(0).size();
            Map<String, String> columns = new HashMap<String, String>();
            ArrayList<String> pk = new ArrayList<String>();

            for (int i = 0; i < attrLength; i++) {
                columns.put(datatable.cells().get(0).get(i),
                        datatable.cells().get(1).get(i));
                // Cucumber returns a genuinely empty cell (| |) as null, so the PK marker has to be
                // compared the other way round: a table definition marks only some columns as PK
                // and leaves the rest of that row empty.
                if ((datatable.cells().size() == 3) && "PK".equalsIgnoreCase(datatable.cells().get(2).get(i))) {
                    pk.add(datatable.cells().get(0).get(i));
                }
            }
            if (pk.isEmpty()) {
                throw new Exception("A PK is needed");
            }
            commonspec.getCassandraClient().createTableWithData(table, columns, pk);
        } catch (Exception e) {
            commonspec.getLogger().debug("Exception captured");
            commonspec.getLogger().debug(e.toString());
            commonspec.getExceptions().add(e);
        }
    }

    /**
     * Insert Data
     *
     * @param table     Cassandra table
     * @param datatable datatable used for parsing elements
     * @param keyspace  Cassandra keyspace
     */
    @Given("^I insert in keyspace '(.+?)' and table '(.+?)' with:$")
    public void insertData(String keyspace, String table, DataTable datatable) {
        try {
            commonspec.getCassandraClient().useKeyspace(keyspace);
            int attrLength = datatable.cells().get(0).size();
            Map<String, Object> fields = new HashMap<String, Object>();
            for (int e = 1; e < datatable.cells().size(); e++) {
                for (int i = 0; i < attrLength; i++) {
                    fields.put(datatable.cells().get(0).get(i), datatable.cells().get(e).get(i));

                }
                commonspec.getCassandraClient().insertData(keyspace + "." + table, fields);

            }
        } catch (Exception e) {
            commonspec.getLogger().debug("Exception captured");
            commonspec.getLogger().debug(e.toString());
            commonspec.getExceptions().add(e);
        }
    }

    /**
     * Save clustername of elasticsearch in an environment varible for future use.
     *
     * @param host   elasticsearch connection
     * @param port   elasticsearch port
     * @param envVar thread variable where to store the value
     * @throws IllegalAccessException    exception
     * @throws IllegalArgumentException  exception
     * @throws SecurityException         exception
     * @throws NoSuchFieldException      exception
     * @throws ClassNotFoundException    exception
     * @throws InstantiationException    exception
     * @throws InvocationTargetException exception
     * @throws NoSuchMethodException     exception
     */
    @Given("^I obtain elasticsearch cluster name in '([^:]+?)(:.+?)?' and save it in variable '(.+?)'?$")
    public void saveElasticCluster(String host, String port, String envVar) throws Exception {

        commonspec.setRestProtocol("http://");
        commonspec.setRestHost(host);
        commonspec.setRestPort(port);

        Future<Response> response;

        response = commonspec.generateRequest("GET", false, null, null, "/", "", "json", "");
        commonspec.setResponse("GET", response.get());

        String json;
        String parsedElement;
        json = commonspec.getResponse().getResponse();
        parsedElement = "$..cluster_name";

        String json2 = "[" + json + "]";
        String value = commonspec.getJSONPathString(json2, parsedElement, "0");

        if (value == null) {
            throw new Exception("No cluster name is found");
        } else {
            ThreadProperty.set(envVar, value);
        }
    }


    /**
     * Save clustername of opensearch in an environment varible for future use.
     *
     * @param host   opensearch connection
     * @param port   opensearch port
     * @param envVar thread variable where to store the value
     * @throws IllegalAccessException    exception
     * @throws IllegalArgumentException  exception
     * @throws SecurityException         exception
     * @throws NoSuchFieldException      exception
     * @throws ClassNotFoundException    exception
     * @throws InstantiationException    exception
     * @throws InvocationTargetException exception
     * @throws NoSuchMethodException     exception
     */
    @Given("^I obtain opensearch cluster name in '([^:]+?)(:.+?)?' and save it in variable '(.+?)'?$")
    public void saveOpenSearchCluster(String host, String port, String envVar) throws Exception {

        commonspec.setRestProtocol("http://");
        commonspec.setRestHost(host);
        commonspec.setRestPort(port);

        Future<Response> response;

        response = commonspec.generateRequest("GET", false, null, null, "/", "", "json", "");
        commonspec.setResponse("GET", response.get());

        String json;
        String parsedElement;
        json = commonspec.getResponse().getResponse();
        parsedElement = "$..cluster_name";

        String json2 = "[" + json + "]";
        String value = commonspec.getJSONPathString(json2, parsedElement, "0");

        if (value == null) {
            throw new Exception("No cluster name is found");
        } else {
            ThreadProperty.set(envVar, value);
        }
    }


    /**
     * Drop all the ElasticSearch indexes.
     */
    @Given("^I drop every existing elasticsearch index$")
    public void dropElasticsearchIndexes() {
        commonspec.getElasticSearchClient().dropAllIndexes();
    }

    /**
     * Drop all the OpenSearch indexes.
     */
    @Given("^I drop every existing opensearch index$")
    public void dropOpenSearchIndexes() {
        commonspec.getOpenSearchClient().dropAllIndexes();
    }

    /**
     * Drop an specific index of ElasticSearch.
     *
     * @param index ES index
     */
    @Given("^I drop an elasticsearch index named '(.+?)'$")
    public void dropElasticsearchIndex(String index) {
        commonspec.getElasticSearchClient().dropSingleIndex(index);
    }

    /**
     * Drop an specific index of OpenSearch.
     *
     * @param index ES index
     */
    @Given("^I drop an opensearch index named '(.+?)'$")
    public void dropOpenSearchIndex(String index) {
        commonspec.getOpenSearchClient().dropSingleIndex(index);
    }

    /**
     * Drop a Cassandra Keyspace.
     *
     * @param keyspace Cassandra keyspace
     */
    @Given("^I drop a Cassandra keyspace '(.+)'$")
    public void dropCassandraKeyspace(String keyspace) {
        commonspec.getCassandraClient().dropKeyspace(keyspace);
    }

    /**
     * Create a MongoDB dataBase.
     *
     * @param databaseName Mongo database
     */
    @Given("^I create a MongoDB dataBase '(.+?)'$")
    public void createMongoDBDataBase(String databaseName) {
        commonspec.getMongoDBClient().connectToMongoDBDataBase(databaseName);

    }

    /**
     * Drop MongoDB Database.
     *
     * @param databaseName mongo database
     */
    @Given("^I drop a MongoDB database '(.+?)'$")
    public void dropMongoDBDataBase(String databaseName) {
        commonspec.getMongoDBClient().dropMongoDBDataBase(databaseName);
    }

    /**
     * Insert data in a MongoDB table.
     *
     * @param dataBase Mongo database
     * @param tabName  Mongo table
     * @param table    Datatable used for insert elements
     */
    @Given("^I insert into a MongoDB database '(.+?)' and table '(.+?)' this values:$")
    public void insertOnMongoTable(String dataBase, String tabName, DataTable table) {
        commonspec.getMongoDBClient().connectToMongoDBDataBase(dataBase);
        commonspec.getMongoDBClient().insertIntoMongoDBCollection(tabName, table);
    }

    /**
     * Truncate table in MongoDB.
     *
     * @param database Mongo database
     * @param table    Mongo table
     */
    @Given("^I drop every document at a MongoDB database '(.+?)' and table '(.+?)'")
    public void truncateTableInMongo(String database, String table) {
        commonspec.getMongoDBClient().connectToMongoDBDataBase(database);
        commonspec.getMongoDBClient().dropAllDataMongoDBCollection(table);
    }

    /**
     * Insert document in a MongoDB table.
     *
     * @param dataBase   Mongo database
     * @param collection Mongo collection
     * @param document   document used for schema
     */
    @Given("^I insert into MongoDB database '(.+?)' and collection '(.+?)' the document from schema '(.+?)'$")
    public void insertOnMongoTable(String dataBase, String collection, String document) throws Exception {
        String retrievedDoc = commonspec.retrieveData(document, "json");
        commonspec.getMongoDBClient().connectToMongoDBDataBase(dataBase);
        commonspec.getMongoDBClient().insertDocIntoMongoDBCollection(collection, retrievedDoc);
    }

    /**
     * Connect to JDBC secured/not secured database
     *
     * @param securityType  database security type string
     * @param encryption  database encription string
     * @param trans  database transactional string
     * @param database  database connection string
     * @param host      database host
     * @param port      database port
     * @param user      database user
     * @param password  database password
     * @param ca        database self signed certs
     * @param crt:      database certificate
     * @param key:      database private key
     * @throws Exception exception     *
     */
    @Given("^I connect with JDBC and security type '(TLS|MD5|TRUST|CERT|LDAP|tls|md5|trust|cert|ldap)'( and encryption)? to database( transactional)? '(.+?)' on host '(.+?)' and port '(.+?)' with user '(.+?)'( and password '(.+?)')?( and root ca '(.+?)')?(, crt '(.+?)')?( and key '(.+?)' certificates)?$")
    public void connectDatabasePostgres(String securityType, String encryption, String trans, String database, String host, String port, String user, String password, String ca, String crt, String key) throws Exception {
        this.commonspec.getExceptions().clear();
        if ("TLS".equals(securityType) || "tls".equals(securityType) || "CERT".equals(securityType) || "cert".equals(securityType)) {
            commonspec.getLogger().debug("opening secure database");
            try {
                this.commonspec.connectToPostgreSQLDatabase(encryption, trans, database, host, port, user, password, true, ca, crt, key);
            } catch (Exception e) {
                this.commonspec.getExceptions().add(e);
            }
        } else {
            commonspec.getLogger().debug("opening database");
            try {
                this.commonspec.connectToPostgreSQLDatabase(encryption, trans, database, host, port, user, password, false, ca, crt, key);
            } catch (Exception e) {
                this.commonspec.getExceptions().add(e);
            }
        }
    }

    /**
     * Execute a query with schema over a cluster
     *
     * @param fields        columns on which the query is executed. Example: "latitude,longitude" or "*" or "count(*)"
     * @param schema        the file of configuration (.conf) with the options of mappin. If schema is the word "empty", method will not add a where clause.
     * @param type          type of the changes in schema (string or json)
     * @param table         table for create the index
     * @param magic_column  magic column where index will be saved. If you don't need index, you can add the word "empty"
     * @param keyspace      keyspace used
     * @param modifications all data in "where" clause. Where schema is "empty", query has not a where clause. So it is necessary to provide an empty table. Example:  ||.
     */
    @When("^I execute a query over fields '(.+?)' with schema '(.+?)' of type '(json|string)' with magic_column '(.+?)' from table: '(.+?)' using keyspace: '(.+?)' with:$")
    public void sendQueryOfType(String fields, String schema, String type, String magic_column, String table, String keyspace, DataTable modifications) {
        try {
            commonspec.setResultsType("cassandra");
            commonspec.getCassandraClient().useKeyspace(keyspace);
            commonspec.getLogger().debug("Starting a query of type " + commonspec.getResultsType());

            String query = "";

            if (schema.equals("empty") && magic_column.equals("empty")) {

                query = "SELECT " + fields + " FROM " + table + ";";

            } else if (!schema.equals("empty") && magic_column.equals("empty")) {
                String retrievedData = commonspec.retrieveData(schema, type);
                String modifiedData = commonspec.modifyData(retrievedData, type, modifications).toString();
                query = "SELECT " + fields + " FROM " + table + " WHERE " + modifiedData + ";";


            } else {
                String retrievedData = commonspec.retrieveData(schema, type);
                String modifiedData = commonspec.modifyData(retrievedData, type, modifications).toString();
                query = "SELECT " + fields + " FROM " + table + " WHERE " + magic_column + " = '" + modifiedData + "';";

            }
            commonspec.getLogger().debug("query: {}", query);
            com.datastax.driver.core.ResultSet results = commonspec.getCassandraClient().executeQuery(query);
            commonspec.setCassandraResults(results);
        } catch (Exception e) {
            commonspec.getLogger().debug("Exception captured");
            commonspec.getLogger().debug(e.toString());
            commonspec.getExceptions().add(e);
        }


    }

    /**
     * Execute a query on (mongo) database
     *
     * @param query         path to query
     * @param type          type of data in query (string or json)
     * @param collection    collection in database
     * @param modifications modifications to perform in query
     */
    @When("^I execute a query '(.+?)' of type '(json|string)' in mongo '(.+?)' database using collection '(.+?)' with:$")
    public void sendQueryOfType(String query, String type, String database, String collection, DataTable modifications) throws Exception {
        try {
            commonspec.setResultsType("mongo");
            String retrievedData = commonspec.retrieveData(query, type);
            String modifiedData = commonspec.modifyData(retrievedData, type, modifications);
            commonspec.getMongoDBClient().connectToMongoDBDataBase(database);
            DBCollection dbCollection = commonspec.getMongoDBClient().getMongoDBCollection(collection);
            DBObject dbObject = (DBObject) JSON.parse(modifiedData);
            DBCursor cursor = dbCollection.find(dbObject);
            commonspec.setMongoResults(cursor);
        } catch (Exception e) {
            commonspec.getExceptions().add(e);
        }
    }

    /**
     * Execute query with filter over elasticsearch
     *
     * @param indexName
     * @param mappingName
     * @param columnName
     * @param filterType  it could be equals, gt, gte, lt and lte.
     * @param value       value of the column to be filtered.
     */
    @When("^I execute an elasticsearch query over index '(.*?)' and mapping '(.*?)' and column '(.*?)' with value '(.*?)' to '(.*?)'$")
    public void elasticSearchQueryWithFilter(String indexName, String mappingName, String
            columnName, String filterType, String value) {
        try {
            commonspec.setResultsType("elasticsearch");
            commonspec.setElasticsearchResults(
                    commonspec.getElasticSearchClient()
                            .searchSimpleFilterElasticsearchQuery(indexName, columnName, value, filterType)

            );
        } catch (Exception e) {
            commonspec.getLogger().debug("Exception captured");
            commonspec.getLogger().debug(e.toString());
            commonspec.getExceptions().add(e);
        }
    }


    /**
     * Create a Cassandra index.
     *
     * @param index_name    index name
     * @param schema        the file of configuration (.conf) with the options of mappin
     * @param type          type of the changes in schema (string or json)
     * @param table         table for create the index
     * @param magic_column  magic column where index will be saved
     * @param keyspace      keyspace used
     * @param modifications data introduced for query fields defined on schema
     */
    @When("^I create a Cassandra index named '(.+?)' with schema '(.+?)' of type '(json|string)' in table '(.+?)' using magic_column '(.+?)' using keyspace '(.+?)' with:$")
    public void createCustomMapping(String index_name, String schema, String type, String table, String magic_column, String keyspace, DataTable modifications) throws Exception {
        String retrievedData = commonspec.retrieveData(schema, type);
        String modifiedData = commonspec.modifyData(retrievedData, type, modifications).toString();
        String query = "CREATE CUSTOM INDEX " + index_name + " ON " + keyspace + "." + table + "(" + magic_column + ") "
                + "USING 'com.stratio.cassandra.lucene.Index' WITH OPTIONS = " + modifiedData;
        commonspec.getLogger().debug("Will execute a cassandra query: {}", query);
        commonspec.getCassandraClient().executeQuery(query);
    }

    /**
     * Drop table
     *
     * @param table
     * @param keyspace
     */
    @When("^I drop a Cassandra table named '(.+?)' using keyspace '(.+?)'$")
    public void dropTableWithData(String table, String keyspace) {
        try {
            commonspec.getCassandraClient().useKeyspace(keyspace);
            commonspec.getCassandraClient().dropTable(table);
        } catch (Exception e) {
            commonspec.getLogger().debug("Exception captured");
            commonspec.getLogger().debug(e.toString());
            commonspec.getExceptions().add(e);
        }
    }

    /**
     * Truncate table
     *
     * @param table
     * @param keyspace
     */
    @When("^I truncate a Cassandra table named '(.+?)' using keyspace '(.+?)'$")
    public void truncateTable(String table, String keyspace) {
        try {
            commonspec.getCassandraClient().useKeyspace(keyspace);
            commonspec.getCassandraClient().truncateTable(table);
        } catch (Exception e) {
            commonspec.getLogger().debug("Exception captured");
            commonspec.getLogger().debug(e.toString());
            commonspec.getExceptions().add(e);
        }
    }

    /**
     * Create an elasticsearch index.
     *
     * @param index
     */
    @When("^I create an elasticsearch index named '(.+?)'( with '(.*?)' shards)?( with '(.*?)' replicas)?( removing existing index if exist)?$")
    public void createElasticsearchIndex(String index, String shards, String replicas, String removeIndex) {
        Settings.Builder settings = Settings.builder();

        if (shards != null) {
            settings.put("index.number_of_shards", shards);
        }
        if (replicas != null) {
            settings.put("index.number_of_replicas", replicas);
        }
        if (removeIndex != null && commonspec.getElasticSearchClient().indexExists(index)) {
            commonspec.getElasticSearchClient().dropSingleIndex(index);
        }
        commonspec.getElasticSearchClient().createSingleIndex(index, settings);
    }


    /**
     * Create an opensearch index.
     *
     * @param index
     */
    @When("^I create an opensearch index named '(.+?)'( with '(.*?)' shards)?( with '(.*?)' replicas)?( removing existing index if exist)?$")
    public void createOpenSearchIndex(String index, String shards, String replicas, String removeIndex) {
        org.opensearch.common.settings.Settings.Builder settings = org.opensearch.common.settings.Settings.builder();

        if (shards != null) {
            settings.put("index.number_of_shards", shards);
        }
        if (replicas != null) {
            settings.put("index.number_of_replicas", replicas);
        }
        if (removeIndex != null && commonspec.getOpenSearchClient().indexExists(index)) {
            commonspec.getOpenSearchClient().dropSingleIndex(index);
        }
        commonspec.getOpenSearchClient().createSingleIndex(index, settings);
    }


    /**
     * Check number of shards from elasticsearch index.
     *
     * @param index
     * @param numberOfShards
     */
    @Then("^The number of shards from index '(.+?)' is '(.+?)'$")
    public void checkNumberOfShardsFromIndex(String index, String numberOfShards) {
        assertThat(commonspec.getElasticSearchClient().getNumberOfShardsFromIndex(index)).isEqualTo(numberOfShards);
    }

    /**
     * Check number of replicas from elasticsearch index.
     *
     * @param index
     * @param numberOfReplicas
     */
    @Then("^The number of replicas from index '(.+?)' is '(.+?)'$")
    public void checkNumberOfReplicasFromIndex(String index, String numberOfReplicas) {
        assertThat(commonspec.getElasticSearchClient().getNumberOfReplicasFromIndex(index)).isEqualTo(numberOfReplicas);
    }


    /**
     * Obtain number of shards from elasticsearch index.
     *
     * @param index
     */
    @Then("^I obtain the number of shards from index '(.+?)'$")
    public void obtainNumberOfShardsFromIndex(String index) {
        commonspec.getElasticSearchClient().getNumberOfShardsFromIndex(index);
    }

    /**
     * Check number of shards from elasticsearch index.
     *
     * @param index
     */
    @Then("^I obtain the number of replicas from index '(.+?)'$")
    public void obtainNumberOfReplicasFromIndex(String index) {
        commonspec.getElasticSearchClient().getNumberOfReplicasFromIndex(index);
    }

    /**
     * Index a document.
     *
     * @param baseData
     * @param indexName
     * @throws Exception
     */
    @When("^I index a document '(.+?)' with id '(.+?)' in the index named '(.+?)'$")
    public void indexElasticsearchDocument(String baseData, String id, String indexName) throws Exception {
        // Retrieve data
        String retrieveData = commonspec.retrieveData(baseData, "json");
        commonspec.getElasticSearchClient().indexDocument(indexName, id, retrieveData);
    }

    /**
     * Index a document in opensearch.
     *
     * @param baseData
     * @param indexName
     * @throws Exception
     */
    @When("^I index in opensearch a document '(.+?)' with id '(.+?)' in the index named '(.+?)'$")
    public void indexOpenSearchDocument(String baseData, String id, String indexName) throws Exception {
        // Retrieve data
        String retrieveData = commonspec.retrieveData(baseData, "json");
        commonspec.getOpenSearchClient().indexDocument(indexName, id, retrieveData);
    }

    /**
     * Check that the ElasticSearch index exists.
     *
     * @param documentId
     * @param indexName
     */
    @Then("^An elasticsearch document id '(.+?)' exists in an index named '(.+?)'")
    public void elasticSearchDocumentExist(String documentId, String indexName) {
        assert (commonspec.getElasticSearchClient().existsDocument(indexName, documentId)) : "There is no document in these index";
    }

    /**
     * Check that the OpenSearch index exists.
     *
     * @param documentId
     * @param indexName
     */
    @Then("^An opensearch document id '(.+?)' exists in an index named '(.+?)'")
    public void openSearchDocumentExist(String documentId, String indexName) {
        assert (commonspec.getOpenSearchClient().existsDocument(indexName, documentId)) : "There is no document in these index";
    }

    /**
     * Check that the ElasticSearch document not exists.
     *
     * @param documentId
     * @param indexName
     */
    @Then("^An elasticsearch document id '(.+?)' does not exist in an index named '(.+?)'")
    public void elasticSearchDocumentNotExist(String documentId, String indexName) {
        assert (!commonspec.getElasticSearchClient().existsDocument(indexName, documentId)) : "There is a document in these index";
    }

    /**
     * Check that the OpenSearch document not exists.
     *
     * @param documentId
     * @param indexName
     */
    @Then("^An opensearch document id '(.+?)' does not exist in an index named '(.+?)'")
    public void openSearchDocumentNotExist(String documentId, String indexName) {
        assert (!commonspec.getOpenSearchClient().existsDocument(indexName, documentId)) : "There is a document in these index";
    }

    /**
     * Delete a document.
     *
     * @param documentId
     * @param indexName
     * @throws Exception
     */
    @Then("^I delete an elasticsearch document with id '(.+?)' in the index named '(.+?)'$")
    public void elasticSearchDocumentDelete(String documentId, String indexName) {
        commonspec.getElasticSearchClient().deleteDocument(indexName, documentId);
    }

    /**
     * Delete a document.
     *
     * @param documentId
     * @param indexName
     * @throws Exception
     */
    @Then("^I delete an opensearch document with id '(.+?)' in the index named '(.+?)'$")
    public void openSearchDocumentDelete(String documentId, String indexName) {
        commonspec.getOpenSearchClient().deleteDocument(indexName, documentId);
    }

    /*
     * @param query
     * executes query in database
     *
     *
     */
    @When("^I execute query '(.+?)'$")
    public void executeQuery(String query) throws Exception {
        ThreadProperty.remove("querysize");
        getCommonSpec().setPreviousSqlResult(null);
        int result = 0;
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }

        try (Statement myStatement = myConnection.createStatement()) {
            result = myStatement.executeUpdate(query);
        } catch (Exception e) {
            e.printStackTrace();
            assertThat(result).as(e.getClass().getName() + ": " + e.getMessage()).isNotEqualTo(0);
        }
    }

    /*
     * @param query
     * executes query in database
     */
    @When("^I execute query '(.+?)' through JDBC connection(, returning an exception)?(, returning a warning)?( with message \"(.+?)\")?$")
    public void executeJdbcQuery(String query, String exceptionReturned, String warningReturned, String exceptionMessageExpected) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }
        try (Statement myStatement = myConnection.createStatement()) {
            String warningMessage = null;
            ThreadProperty.remove("querysize");
            getCommonSpec().setPreviousSqlResult(null);
            myStatement.execute(query);

            if (warningReturned != null) {
                if (myStatement.getWarnings() != null) {
                    warningMessage = myStatement.getWarnings().toString();
                } else {
                    fail("Query was executed successfully but we expected a warning");
                }
            }

            if (exceptionReturned != null) {
                fail("Query was executed successfully but we expected an exception");
            }
            if (warningReturned != null && exceptionMessageExpected != null) {
                Assertions.assertThat(warningMessage).contains(exceptionMessageExpected);
            }
        } catch (Exception e) {
            if (exceptionReturned == null) {
                fail("Error executing query -> " + e.getMessage());
            } else {
                getCommonSpec().getLogger().warn("Error executing query -> " + e.getMessage());
                if (exceptionMessageExpected != null) {
                    Assertions.assertThat(e.getMessage()).as("Query error message").contains(exceptionMessageExpected);
                }
            }
        }
    }

    @When("^I execute query through JDBC connection(, returning an exception)?(, returning a warning)?( with message \"(.+?)\")?:$")
    public void executeJdbcQueryDocStringFlags(String exceptionReturned,
                                               String warningReturned,
                                               String exceptionMessageExpected,
                                               String query) throws Exception {
        executeJdbcQuery(query, exceptionReturned, warningReturned, exceptionMessageExpected);
    }

    /**
     * Executes a set of SQL statements against a Postgres database running in Kubernetes, handling
     * the whole plumbing internally: Vault certificates, /etc/hosts entry, port forward, JDBC
     * connection, query execution and cleanup.
     * <p>
     * Replaces the 11-step block that had to be repeated in every feature needing SQL access:
     * <pre>
     *   I obtain Postgres certificates for Postgres
     *   I save host '...' with ip '127.0.0.1' in /etc/hosts
     *   I forward containerPort '...' in localhostPort '...' for service '...' in namespace '...'
     *   I connect with JDBC and security type 'CERT' to database transactional '...' ...
     *   an exception 'IS NOT' thrown
     *   I execute query '...' through JDBC connection      (xN)
     *   I close database connection
     *   I close port forward
     *   I restore /etc/hosts
     * </pre>
     * Cleanup always runs, even when a query fails, so a failing scenario cannot leave a stale port
     * forward or a polluted /etc/hosts behind. Every phase is logged into the scenario output.
     * <p>
     * Two authentication modes:
     * <ul>
     *   <li>mTLS (default): certificates are downloaded from Vault into {@code target/test-classes}
     *       ({@code <certName>.pem}, {@code <certName>.pk8} and {@code ca.crt}), with 3 retries.</li>
     *   <li>Password: given {@code with password '...'}, connects with security type {@code MD5} and
     *       no Vault access at all.</li>
     * </ul>
     * Example:
     * <pre>
     * When I execute the following queries in Postgres database 'genaimemories' on host 'my-pg-primary' in namespace 'my-ns':
     *   """
     *   -- Extensions required by Hindsight
     *   CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
     *   CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
     *   """
     * </pre>
     * Statements are separated by {@code ;}, so one of them may span as many lines as it needs.
     * A certificate other than the Postgres one is given with
     * {@code with certificates 'gosec-authz' from vault path 'gosec-authz.keos-core'}.
     * <p>
     * A failing statement fails the step, unless {@code ignoring errors} is given: then a failure
     * is logged and skipped, which is what a best-effort cleanup wants.
     *
     * @param database  database to connect to
     * @param host      Postgres service name (also the /etc/hosts entry and the JDBC host)
     * @param namespace namespace where the Postgres service lives
     * @param port      Postgres port (defaults to 5432)
     * @param user      database user (defaults to postgres)
     * @param certName  Vault certificate entry (defaults to postgres)
     * @param vaultPath Vault path of the certificates (defaults to the idPostgres test variable)
     * @param password  when set, connects with password instead of certificates
     * @param queries   statements to execute in order, separated by {@code ;}; statements may span
     *                  several lines and {@code --} comments are ignored
     * @param ignoringErrors           when set, a failing statement is logged and skipped instead
     *                                 of failing the step
     * @throws Exception if the certificates cannot be downloaded, the connection cannot be opened
     *                   or any of the queries fails
     */
    @When("^I execute the following queries in Postgres database '(.+?)' on host '(.+?)' in namespace '(.+?)'( on port '(.+?)')?( with user '(.+?)')?( with certificates '(.+?)')?( from vault path '(.+?)')?( with password '(.+?)')?( ignoring errors)?:$")
    public void executeQueriesInKubernetesPostgres(String database, String host, String namespace, String port,
                                                   String user, String certName, String vaultPath, String password,
                                                   String ignoringErrors, String queries) throws Exception {
        List<String> statements = SqlStatementSplitter.splitSqlStatements(queries);
        inKubernetesPostgresConnection(database, host, namespace, port, user, certName, vaultPath, password,
            statements.size() + " query(ies) to execute",
            () -> executeStatements(statements, ignoringErrors));
    }

    /**
     * Executes a SQL file against a Postgres database running in Kubernetes, handling the whole
     * plumbing internally exactly like
     * {@code executeQueriesInKubernetesPostgres}
     * - Vault certificates, /etc/hosts entry, port forward, JDBC connection and cleanup - so a
     * feature needing a schema loaded no longer has to spell the block out.
     * <p>
     * The file is read as UTF-8 and split by {@link SqlStatementSplitter#splitSqlStatements(String)}, the same way the
     * docstring of the step above is, so both behave alike: statements are separated by {@code ;},
     * may span several lines, and comments are dropped.
     * <p>
     * Example:
     * <pre>
     * When I execute the sql file 'src/test/resources/schemas/postgres-sakila-schema.sql' in Postgres database 'sakila' on host 'my-pg-primary' in namespace 'my-ns'
     * </pre>
     * {@code ignoring errors} means here exactly what it means in the step above.
     *
     * @param file      path of the SQL file to execute
     * @param database  database to connect to
     * @param host      Postgres service name (also the /etc/hosts entry and the JDBC host)
     * @param namespace namespace where the Postgres service lives
     * @param port      Postgres port (defaults to 5432)
     * @param user      database user (defaults to postgres)
     * @param certName  Vault certificate entry (defaults to postgres)
     * @param vaultPath Vault path of the certificates (defaults to the idPostgres test variable)
     * @param password  when set, connects with password instead of certificates
     * @param ignoringErrors           when set, a failing statement is logged and skipped instead
     *                                 of failing the step
     * @throws Exception if the certificates cannot be downloaded, the connection cannot be opened
     *                   or the file cannot be read or executed
     */
    @When("^I execute the sql file '(.+?)' in Postgres database '(.+?)' on host '(.+?)' in namespace '(.+?)'( on port '(.+?)')?( with user '(.+?)')?( with certificates '(.+?)')?( from vault path '(.+?)')?( with password '(.+?)')?( ignoring errors)?$")
    public void executeSqlFileInKubernetesPostgres(String file, String database, String host, String namespace,
                                                   String port, String user, String certName, String vaultPath,
                                                   String password, String ignoringErrors) throws Exception {
        List<String> statements = SqlStatementSplitter.splitSqlStatements(new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8));
        inKubernetesPostgresConnection(database, host, namespace, port, user, certName, vaultPath, password,
            "sql file '" + file + "', " + statements.size() + " query(ies) to execute",
            () -> executeStatements(statements, ignoringErrors));
    }

    /**
     * Executes {@code statements} in order over the JDBC connection already open.
     * <p>
     * A statement failing fails the step, unless {@code ignoring errors} was given: then every
     * statement is attempted and a failure is only logged, which is what a best-effort block such
     * as a cleanup wants. Autocommit is on, so a failed statement does not abort the ones after it.
     *
     * @param statements     statements to execute in order
     * @param ignoringErrors when set, a failing statement is logged instead of failing the step
     * @throws Exception if a statement fails and {@code ignoring errors} was not given
     */
    private void executeStatements(List<String> statements, String ignoringErrors) throws Exception {
        for (String statement : statements) {
            logIntoScenario("[Postgres] Executing query: " + statement);
            try {
                executeJdbcQuery(statement, null, null, null);
            } catch (Exception | AssertionError e) {
                if (ignoringErrors == null) {
                    throw e;
                }
                logIntoScenario("[Postgres] Ignored error -> " + e.getMessage());
            }
        }
        logIntoScenario("[Postgres] All queries executed successfully");
    }

    /**
     * Opens everything a Postgres database running in Kubernetes needs - Vault certificates,
     * /etc/hosts entry, port forward and JDBC connection - runs {@code work} against it and tears
     * it all down again.
     * <p>
     * Cleanup always runs, even when {@code work} fails, so a failing scenario cannot leave a stale
     * port forward or a polluted /etc/hosts behind, and a cleanup failure is logged rather than
     * thrown so it cannot mask the original error. Every phase is logged into the scenario output.
     *
     * @param workDescription what {@code work} is going to do, for the opening log line
     * @param work            what to run once the connection is open
     */
    private void inKubernetesPostgresConnection(String database, String host, String namespace, String port,
                                                String user, String certName, String vaultPath, String password,
                                                String workDescription, ThrowingRunnable work) throws Exception {
        String pgPort = port != null ? port : POSTGRES_DEFAULT_PORT;
        String pgUser = user != null ? user : POSTGRES_DEFAULT_USER;
        String pgCertName = certName != null ? certName : POSTGRES_DEFAULT_CERTIFICATE;
        String pgVaultPath = vaultPath != null ? vaultPath : ThreadProperty.get("idPostgres");

        if (password == null && (pgVaultPath == null || pgVaultPath.isEmpty())) {
            throw new Exception("No Postgres credentials given: add \"with certificates '<name>' from vault path '<path>'\", "
                    + "or \"with password '<password>'\", or set the 'idPostgres' test variable");
        }

        K8SSpec k8SSpec = new K8SSpec(commonspec);
        ETCHOSTSManagementSpec etcHostsManagementSpec = new ETCHOSTSManagementSpec(commonspec);
        String forwardId = "postgres-" + host + "-" + pgPort;
        boolean hostsFileModified = false;
        boolean portForwarded = false;

        logIntoScenario("[Postgres] " + pgUser + "@" + host + "." + namespace + ":" + pgPort + "/" + database
                + " - " + workDescription);

        try {
            if (password == null) {
                getPostgresCertificates(k8SSpec, pgCertName, pgVaultPath);
            }

            logIntoScenario("[Postgres] Adding '" + host + " -> 127.0.0.1' to /etc/hosts");
            etcHostsManagementSpec.addEntryHostsFile(host, "127.0.0.1", null, null);
            hostsFileModified = true;

            logIntoScenario("[Postgres] Forwarding localhost:" + pgPort + " to service '" + host + "' in namespace '" + namespace + "'");
            k8SSpec.setLocalPortForward(Integer.parseInt(pgPort), Integer.parseInt(pgPort), "service", host, namespace, forwardId);
            portForwarded = true;

            if (password != null) {
                logIntoScenario("[Postgres] Connecting with JDBC (MD5) to database '" + database + "'");
                connectDatabasePostgres("MD5", null, " transactional", database, host, pgPort, pgUser, password, null, null, null);
            } else {
                logIntoScenario("[Postgres] Connecting with JDBC (CERT) to database '" + database + "'");
                connectDatabasePostgres("CERT", null, " transactional", database, host, pgPort, pgUser, null,
                        CERTIFICATES_PATH + "ca.crt", CERTIFICATES_PATH + pgCertName + ".pem", CERTIFICATES_PATH + pgCertName + ".pk8");
            }
            if (!commonspec.getExceptions().isEmpty()) {
                Exception connectionException = commonspec.getExceptions().get(commonspec.getExceptions().size() - 1);
                throw new Exception("Could not connect to Postgres database '" + database + "' on " + host + ":" + pgPort
                        + " -> " + connectionException.getMessage(), connectionException);
            }

            work.run();
        } finally {
            closeQuietly("close database connection", this::closeDatabase);
            if (portForwarded) {
                closeQuietly("close port forward", () -> k8SSpec.closePortForward(forwardId));
            }
            if (hostsFileModified) {
                closeQuietly("restore /etc/hosts", () -> etcHostsManagementSpec.restoreHostsFile(null, null));
            }
        }
    }

    /**
     * Downloads the Postgres PEM/PK8/CA certificates from Vault into {@code target/test-classes},
     * retrying up to 3 times.
     *
     * @param k8SSpec   spec used to open the port forward to Vault
     * @param certName  Vault certificate entry (e.g. postgres)
     * @param vaultPath Vault path of the certificates (e.g. my-postgres.my-namespace)
     * @throws Exception if the certificates could not be downloaded after 3 attempts
     */
    private void getPostgresCertificates(K8SSpec k8SSpec, String certName, String vaultPath) throws Exception {
        VaultSpec vaultSpec = new VaultSpec(commonspec);
        String vaultForwardId = "postgres-certificates-vault-8200";
        Exception lastException = null;
        for (int i = 1; i <= 3; i++) {
            logIntoScenario("[Postgres] Downloading certificates '" + certName + "' from Vault path '" + vaultPath + "' (attempt " + i + "/3)");
            try {
                k8SSpec.setLocalPortForward(8200, 8200, "service", "vault", "keos-core", vaultForwardId);
                commonspec.runLocalCommand("rm -Rf " + CERTIFICATES_PATH + "*.pem " + CERTIFICATES_PATH + "*.key "
                        + CERTIFICATES_PATH + "*.pk8 " + CERTIFICATES_PATH + "ca.crt");
                vaultSpec.getPubCertificate(certName, vaultPath, null);
                vaultSpec.getPK8Certificate(certName, vaultPath, null);
                vaultSpec.getCA();
                lastException = null;
                break;
            } catch (Exception | Error e) {
                lastException = new Exception(e.getMessage(), e);
                commonspec.getLogger().warn("Error downloading Postgres certificates - " + e.getMessage());
            } finally {
                k8SSpec.closePortForward(vaultForwardId);
            }
        }
        if (lastException != null) {
            throw new Exception("Could not download Postgres certificates from Vault path '" + vaultPath
                    + "' after 3 attempts -> " + lastException.getMessage(), lastException);
        }
        logIntoScenario("[Postgres] Certificates available in " + CERTIFICATES_PATH);
    }

    /**
     * Runs a cleanup action, logging - but never propagating - its failure, so that one broken
     * cleanup step neither prevents the rest from running nor masks the original error.
     */
    private void closeQuietly(String description, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            commonspec.getLogger().warn("[Postgres] Could not " + description + " - " + t.getMessage());
        }
    }

    /**
     * Logs as INFO and, when running inside a scenario, into the scenario output.
     */
    private void logIntoScenario(String message) {
        commonspec.getLogger().info(message);
        if (scenario != null) {
            scenario.log(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /*
     * @param query
     * executes query in database
     */
    @When("^I execute file sql '(.+?)' through JDBC connection(, returning an exception)?(, returning a warning)?( with message \"(.+?)\")?$")
    public void executeJdbcQueryFile(String file, String exceptionReturned, String warningReturned, String exceptionMessageExpected) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }
        try (FileReader reader = new FileReader(file);
             // Wrap the FileReader in a BufferedReader for
             // efficient reading.
             BufferedReader bufferedReader
                     = new BufferedReader(reader);
             // Create a statement object to execute SQL
             // commands.
             Statement statement
                     = myConnection.createStatement();) {

            StringBuilder builder = new StringBuilder();

            String line;
            int lineNumber = 0;
            int count = 0;

            // Read lines from the SQL file until the end of the
            // file is reached.
            while ((line = bufferedReader.readLine()) != null) {
                lineNumber += 1;
                line = line.trim();

                // Skip empty lines and single-line comments.
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                builder.append(" ");
                builder.append(" " + line);
                // If the line ends with a semicolon, it
                // indicates the end of an SQL command.
                if (line.endsWith(";")) {
                    try {
                        // Execute the SQL command
                        statement.execute(builder.toString());
                        // Print a success message along with
                        // the first 15 characters of the
                        // executed command.
                        builder.setLength(0);
                    } catch (SQLException e) {
                        // If an SQLException occurs during
                        // execution, print an error message and
                        // stop further execution.
                        System.err.println(
                                "At line " + lineNumber + " : "
                                        + e.getMessage() + "\n");
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        catch (Exception e) {
            fail("Error running SQL file " + file + " -> " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /*
     * @param query Query to execute
     * @param cancelTime query will be cancelled when cancelTime was reached
     * Executes query in database and cancel it after calcelTime seconds
     */
    @When("^I execute query '(.+?)' in background and I cancel it after '(\\d+)' seconds, returning a Query cancelled SQLSyntaxErrorException$")
    public void executeJdbcQuery(String query, Integer cancelTime) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }
        try {
            ThreadProperty.remove("querysize");
            getCommonSpec().setPreviousSqlResult(null);
            Statement myStatement = myConnection.createStatement();
            Future<Void> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(cancelTime * 1000);
                    myStatement.cancel();
                } catch (SQLException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            myStatement.execute(query);
            future.cancel(true);
            fail("Query was executed succesfully (it should be cancelled)");
        } catch (SQLSyntaxErrorException e) {
            assertThat(e.getMessage()).as("SQLSyntaxErrorException message").containsIgnoringCase("cancelled");
        }
    }

    /*
     * @param query
     * selects data from database
     * and sends it to environment variable
     *
     */
    @When("^I query the database with '(.+?)'$")
    public void selectData(String query) throws Exception {
        //postgres table
        List<String> sqlTable = new ArrayList<String>();
        List<String> sqlTableAux = new ArrayList<String>();
        // LinkedHashMap so the column order returned by the query is kept when the result is printed
        Map<String, List<String>> sqlResultMap = new LinkedHashMap<>();
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }
        try (Statement myStatement = myConnection.createStatement();
             java.sql.ResultSet rs = myStatement.executeQuery(query)) {
            //column names
            ResultSetMetaData resultSetMetaData = rs.getMetaData();
            int count = resultSetMetaData.getColumnCount();
            for (int i = 1; i <= count; i++) {
                sqlTable.add(resultSetMetaData.getColumnName(i).toString());
                sqlResultMap.put(resultSetMetaData.getColumnName(i), new ArrayList<>());
            }
            //takes column names and column count
            int resultSize = 0;
            while (rs.next()) {
                resultSize++;
                for (int i = 1; i <= count; i++) {
                    //aux list without column names
                    String value = resultSetValue(rs, i);
                    sqlTableAux.add(value);
                    List<String> previousList = sqlResultMap.get(resultSetMetaData.getColumnName(i));
                    if (previousList == null) {
                        previousList = new ArrayList<>();
                    }
                    previousList.add(value);
                    sqlResultMap.put(resultSetMetaData.getColumnName(i), previousList);
                }
            }
            ThreadProperty.set("querysize", String.valueOf(resultSize));
            sqlTable.addAll(sqlTableAux);

            //sends raws to environment variable
            for (int i = 0; i < sqlTable.size(); i++) {
                ThreadProperty.set("queryresponse" + i, sqlTable.get(i));
            }
            ThreadProperty.remove("queryresponse" + sqlTable.size());
            getCommonSpec().setPreviousSqlResult(sqlResultMap);
        } catch (Exception e) {
            commonspec.getLogger().error("Exception in query", e);
            fail("There are no response from SELECT - Error message: " + e.getMessage());
        }
    }

    @When("I query the database with:")
    public void selectDataDocString(String query) throws Exception {
        selectData(query);
    }

    /**
     * Checks if a keyspaces exists in Cassandra.
     *
     * @param keyspace
     */
    @Then("^a Cassandra keyspace '(.+?)' exists$")
    public void assertKeyspaceOnCassandraExists(String keyspace) {
        assertThat(commonspec.getCassandraClient().getKeyspaces()).as("The keyspace " + keyspace + " exists on cassandra").contains(keyspace);
    }

    /**
     * Checks a keyspace does not exist in Cassandra.
     *
     * @param keyspace
     */
    @Then("^a Cassandra keyspace '(.+?)' does not exist$")
    public void assertKeyspaceOnCassandraDoesNotExist(String keyspace) {
        assertThat(commonspec.getCassandraClient().getKeyspaces()).as("The keyspace " + keyspace + " does not exist on cassandra").doesNotContain(keyspace);
    }

    /**
     * Checks if a cassandra keyspace contains a table.
     *
     * @param keyspace
     * @param tableName
     */
    @Then("^a Cassandra keyspace '(.+?)' contains a table '(.+?)'$")
    public void assertTableExistsOnCassandraKeyspace(String keyspace, String tableName) {
        assertThat(commonspec.getCassandraClient().getTables(keyspace)).as("The table " + tableName + "exists on cassandra").contains(tableName);
    }

    /**
     * Checks a cassandra keyspace does not contain a table.
     *
     * @param keyspace
     * @param tableName
     */
    @Then("^a Cassandra keyspace '(.+?)' does not contain a table '(.+?)'$")
    public void assertTableDoesNotExistOnCassandraKeyspace(String keyspace, String tableName) {
        assertThat(commonspec.getCassandraClient().getTables(keyspace)).as("The table " + tableName + "exists on cassandra").doesNotContain(tableName);
    }

    /**
     * Checks the number of rows in a cassandra table.
     *
     * @param keyspace
     * @param tableName
     * @param numberRows
     */
    @Then("^a Cassandra keyspace '(.+?)' contains a table '(.+?)' with '(.+?)' rows$")
    public void assertRowNumberOfTableOnCassandraKeyspace(String keyspace, String tableName, String numberRows) {
        Long numberRowsLong = Long.parseLong(numberRows);
        commonspec.getCassandraClient().useKeyspace(keyspace);
        assertThat(commonspec.getCassandraClient().executeQuery("SELECT COUNT(*) FROM " + tableName + ";").all().get(0).getLong(0)).as("The table " + tableName + "exists on cassandra").
                isEqualTo(numberRowsLong);
    }

    /**
     * Checks if a cassandra table contains the values of a DataTable.
     *
     * @param keyspace
     * @param tableName
     * @param data
     * @throws InterruptedException
     */
    @Then("^a Cassandra keyspace '(.+?)' contains a table '(.+?)' with values:$")
    public void assertValuesOfTable(String keyspace, String tableName, DataTable data) throws InterruptedException {
        //  USE of Keyspace
        commonspec.getCassandraClient().useKeyspace(keyspace);
        // Obtain the types and column names of the datatable
        // to return in a hashmap,
        Map<String, String> dataTableColumns = extractColumnNamesAndTypes(data.cells().get(0));
        // check if the table has columns
        String query = "SELECT * FROM " + tableName + " LIMIT 1;";
        com.datastax.driver.core.ResultSet res = commonspec.getCassandraClient().executeQuery(query);
        equalsColumns(res.getColumnDefinitions(), dataTableColumns);
        //receiving the string from the select with the columns
        // that belong to the dataTable
        List<String> selectQueries = giveQueriesList(data, tableName, columnNames(data.cells().get(0)));
        //Check the data  of cassandra with different queries
        int index = 1;
        for (String execQuery : selectQueries) {
            res = commonspec.getCassandraClient().executeQuery(execQuery);
            List<Row> resAsList = res.all();
            assertThat(resAsList.size()).as("The query " + execQuery + " not return any result on Cassandra").isGreaterThan(0);
            assertThat(resAsList.get(0).toString()
                    .substring(VALUE_SUBSTRING)).as("The resultSet is not as expected").isEqualTo(data.cells().get(index).toString().replace("'", ""));
            index++;
        }
    }

    @SuppressWarnings("rawtypes")
    private void equalsColumns(ColumnDefinitions resCols, Map<String, String> dataTableColumns) {
        Iterator it = dataTableColumns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            assertThat(resCols.toString()).as("The table not contains the column.").contains(e.getKey().toString());
            DataType type = resCols.getType(e.getKey().toString());
            assertThat(type.getName().toString()).as("The column type is not equals.").isEqualTo(e.getValue().toString());
        }
    }

    private List<String> giveQueriesList(DataTable data, String tableName, String colNames) {
        List<String> queryList = new ArrayList<String>();
        for (int i = 1; i < data.cells().size(); i++) {
            String query = "SELECT " + colNames + " FROM " + tableName;
            List<String> row = data.cells().get(i);
            query += conditionWhere(row, colNames.split(",")) + ";";
            queryList.add(query);
        }
        return queryList;
    }

    private String conditionWhere(List<String> values, String[] columnNames) {
        StringBuilder condition = new StringBuilder();
        condition.append(" WHERE ");
        Pattern numberPat = Pattern.compile("^\\d+(\\.*\\d*)?");
        Pattern booleanPat = Pattern.compile("true|false");
        for (int i = 0; i < values.size() - 1; i++) {
            condition.append(columnNames[i]).append(" =");
            condition.append(" ").append(values.get(i)).append(" AND ");
        }
        condition.append(columnNames[columnNames.length - 1]).append(" =");
        condition.append(" ").append(values.get(values.size() - 1));

        condition.append(" ALLOW FILTERING");
        return condition.toString();
    }

    private String columnNames(List<String> firstRow) {
        StringBuilder columnNamesForQuery = new StringBuilder();
        for (String s : firstRow) {
            String[] aux = s.split("-");
            columnNamesForQuery.append(aux[0]).append(",");
        }
        return columnNamesForQuery.toString().substring(0, columnNamesForQuery.length() - 1);
    }

    private Map<String, String> extractColumnNamesAndTypes(List<String> firstRow) {
        HashMap<String, String> columns = new HashMap<String, String>();
        for (String s : firstRow) {
            String[] aux = s.split("-");
            columns.put(aux[0], aux[1]);
        }
        return columns;
    }


    /**
     * Checks the values of a MongoDB table.
     *
     * @param dataBase
     * @param tableName
     * @param data
     */
    @Then("^a Mongo dataBase '(.+?)' contains a table '(.+?)' with values:")
    public void assertValuesOfTableMongo(String dataBase, String tableName, DataTable data) {
        commonspec.getMongoDBClient().connectToMongoDBDataBase(dataBase);
        ArrayList<DBObject> result = (ArrayList<DBObject>) commonspec.getMongoDBClient().readFromMongoDBCollection(
                tableName, data);
        DBObjectsAssert.assertThat(result).containedInMongoDBResult(data);

    }

    /**
     * Checks if a MongoDB database contains a table.
     *
     * @param database
     * @param tableName
     */
    @Then("^a Mongo dataBase '(.+?)' doesnt contains a table '(.+?)'$")
    public void aMongoDataBaseContainsaTable(String database, String tableName) {
        commonspec.getMongoDBClient().connectToMongoDBDataBase(database);
        Set<String> collectionsNames = commonspec.getMongoDBClient().getMongoDBCollections();
        assertThat(collectionsNames).as("The Mongo dataBase contains the table").doesNotContain(tableName);
    }

    /**
     * Checks the different results of a previous query
     *
     * @param expectedResults A DataTable Object with all data needed for check the results. The DataTable must contains at least 2 columns:
     *                        a) A field column from the result
     *                        b) Occurrences column (Integer type)
     *                        <p>
     *                        Example:
     *                        |latitude| longitude|place     |occurrences|
     *                        |12.5    |12.7      |Valencia  |1           |
     *                        |2.5     | 2.6      |Stratio   |0           |
     *                        |12.5    |13.7      |Sevilla   |1           |
     *                        IMPORTANT: There no should be no existing columns
     * @throws Exception
     */
    @Then("^There are results found with:$")
    public void resultsMustBe(DataTable expectedResults) throws Exception {

        String type = commonspec.getResultsType();
        assertThat(type).isNotEqualTo("").overridingErrorMessage("It's necessary to define the result type");
        switch (type) {
            case "cassandra":
                commonspec.resultsMustBeCassandra(expectedResults);
                break;
            case "mongo":
                commonspec.resultsMustBeMongo(expectedResults);
                break;
            case "elasticsearch":
                commonspec.resultsMustBeElasticsearch(expectedResults);
                break;
            case "csv":
                commonspec.resultsMustBeCSV(expectedResults);
                break;
            default:
                commonspec.getLogger().warn("default switch branch on results check");
        }
    }

    /**
     * Check that the ElasticSearch index exists.
     *
     * @param indexName
     */
    @Then("^An elasticsearch index named '(.+?)' exists")
    public void elasticSearchIndexExist(String indexName) {
        assert (commonspec.getElasticSearchClient().indexExists(indexName)) : "There is no index with that name";
    }

    /**
     * Check that the OpenSearch index exists.
     *
     * @param indexName
     */
    @Then("^An opensearch index named '(.+?)' exists")
    public void openSearchIndexExist(String indexName) {
        assert (commonspec.getOpenSearchClient().indexExists(indexName)) : "There is no index with that name";
    }

    /**
     * Check that the ElasticSearch index does not exist.
     *
     * @param indexName
     */
    @Then("^An elasticsearch index named '(.+?)' does not exist")
    public void elasticSearchIndexDoesNotExist(String indexName) {
        assert !commonspec.getElasticSearchClient().indexExists(indexName) : "There is an index with that name";
    }

    /**
     * Check that the OpenSearch index does not exist.
     *
     * @param indexName
     */
    @Then("^An opensearch index named '(.+?)' does not exist")
    public void openSearchIndexDoesNotExist(String indexName) {
        assert !commonspec.getOpenSearchClient().indexExists(indexName) : "There is an index with that name";
    }

    /**
     * Check that an elasticsearch index contains a specific document
     *
     * @param indexName
     * @param columnName
     * @param columnValue
     */
    @Then("^The Elasticsearch index named '(.+?)' contains a column named '(.+?)' with the value '(.+?)'$")
    public void elasticSearchIndexContainsDocument(String indexName, String columnName, String columnValue) throws Exception {
        Assertions.assertThat((commonspec.getElasticSearchClient().searchSimpleFilterElasticsearchQuery(
                indexName,
                columnName,
                columnValue,
                "equals"
        ).size()) > 0).isTrue().withFailMessage("The index does not contain that document");
    }

    /**
     * Check that an opensearch index contains a specific document
     *
     * @param indexName
     * @param columnName
     * @param columnValue
     */
    @Then("^The OpenSearch index named '(.+?)' contains a column named '(.+?)' with the value '(.+?)'$")
    public void openSearchIndexContainsDocument(String indexName, String columnName, String columnValue) throws Exception {
        Assertions.assertThat((commonspec.getOpenSearchClient().searchSimpleFilterOpensearchQuery(
                indexName,
                columnName,
                columnValue,
                "equals"
        ).size()) > 0).isTrue().withFailMessage("The index does not contain that document");
    }

    /*
     * @param table
     * checks table existence
     *
     */
    @Then("^table '(.+?)' exists$")
    public void checkTable(String tableName) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }

        //query checks table existence, existence table name in system table  pg_tables
        try (PreparedStatement myStatement = myConnection.prepareStatement("SELECT * FROM pg_tables WHERE tablename = ?")) {
            myStatement.setString(1, tableName);
            try (java.sql.ResultSet rs = myStatement.executeQuery()) {
                //if there are no data row
                if (rs.next() == false) {
                    fail("There is no table " + tableName);
                } else {
                    //data exist
                    String resultTableName = rs.getString(2);
                    assertThat(resultTableName).as("there are incorrect table name " + tableName).contains(tableName);
                }
            }
        } catch (Exception e) {
            fail("Could not check whether table " + tableName + " exists -> " + e.getMessage());
        }

    }


    /*
     * @param table
     * checks table existence negative case
     *
     */
    @Then("^table '(.+?)' doesn't exists$")
    public void checkTableFalse(String tableName) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }

        try (PreparedStatement myStatement = myConnection.prepareStatement("SELECT * FROM pg_tables WHERE tablename = ?")) {
            myStatement.setString(1, tableName);
            try (java.sql.ResultSet rs = myStatement.executeQuery()) {
                //if there are no data row, table doesn't exists
                if (rs.next() != false) {
                    String resultTableName = rs.getString(2);
                    assertThat(resultTableName).as("table exists " + tableName).isNotEqualToIgnoringCase(tableName);
                }
            }
        } catch (Exception e) {
            fail("Could not check whether table " + tableName + " exists -> " + e.getMessage());
        }
    }


    /*
     * @param tableName
     * @param dataTable
     * compares two tables: pattern table and the result from remote database
     * by default: order by id
     *
     */
    @Then("^I check that table '(.+?)' is iqual to$")
    public void comparetable(String tableName, DataTable dataTable) throws Exception {
        //from postgres table
        List<String> sqlTable = new ArrayList<String>();
        List<String> sqlTableAux = new ArrayList<String>();
        //from Cucumber Datatable
        List<String> tablePattern = dataTable.cells().stream().flatMap(List::stream).map(DatabaseSpec::cellValue).collect(Collectors.toList());

        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }
        if (!tableName.matches("[a-zA-Z0-9_\\.]+")) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        String query = "SELECT * FROM " + tableName + " order by id;";
        try (Statement myStatement = myConnection.createStatement();
             java.sql.ResultSet rs = myStatement.executeQuery(query)) {
            //takes column names and culumn count
            ResultSetMetaData resultSetMetaData = rs.getMetaData();
            int count = resultSetMetaData.getColumnCount();
            for (int i = 1; i <= count; i++) {
                sqlTable.add(resultSetMetaData.getColumnName(i).toString());
            }

            //takes column names and culumn count
            while (rs.next()) {
                for (int i = 1; i <= count; i++) {
                    //aux list without column names
                    sqlTableAux.add(resultSetValue(rs, i));
                }
            }
            sqlTable.addAll(sqlTableAux);

            if (!tablePattern.equals(sqlTable)) {
                int columns = dataTable.cells().get(0).size();
                commonspec.getLogger().error("Expected content of table " + tableName + ":" + System.lineSeparator()
                        + flatResultAsText(tablePattern, columns) + System.lineSeparator()
                        + "Actual content of table " + tableName + ":" + System.lineSeparator()
                        + flatResultAsText(sqlTable, columns));
                fail("Error in table comparison: table " + tableName + " is not equal to the expected result."
                        + System.lineSeparator() + sizesAsText(tablePattern, sqlTable, columns)
                        + System.lineSeparator() + differencesAsText(tablePattern, sqlTable, normalizeCells(dataTable.cells()).get(0)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("There are no table " + tableName + ": " + e.getMessage());
        }
    }

    /*
     * closes opened database
     *
     */
    @Then("^I close database connection$")
    public void closeDatabase() throws Exception {
        JDBCConnection.closeConnection();
    }

    /*
     * @param tableName
     * checks the result from select
     *
     */
    @Then("^I check that result is:$")
    public void comparetable(DataTable dataTable) throws Exception {

        //from Cucumber Datatable, the pattern to verify
        List<String> tablePattern = new ArrayList<String>();
        tablePattern = dataTable.cells().stream().flatMap(List::stream).map(DatabaseSpec::cellValue).collect(Collectors.toList());

        //the result from select
        List<String> sqlTable = new ArrayList<String>();

        //the result is taken from previous step
        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            String ip_value = ThreadProperty.get("queryresponse" + i);
            sqlTable.add(i, ip_value);
        }

        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            ThreadProperty.remove("queryresponse" + i);
        }

        if (!tablePattern.equals(sqlTable)) {
            int columns = dataTable.cells().get(0).size();
            commonspec.getLogger().error("Expected result:" + System.lineSeparator()
                    + flatResultAsText(tablePattern, columns) + System.lineSeparator()
                    + "Full query response:" + System.lineSeparator() + flatResultAsText(sqlTable, columns));
            fail("Error in table comparison: query response is not equal to the expected result."
                    + System.lineSeparator() + sizesAsText(tablePattern, sqlTable, columns)
                    + System.lineSeparator() + differencesAsText(tablePattern, sqlTable, normalizeCells(dataTable.cells()).get(0)));
        }
    }

    /**
     * Describes the size of both tables, in cells and in rows, so a shifted comparison is easy to spot.
     */
    private String sizesAsText(List<String> expectedCells, List<String> actualCells, int columns) {
        String sizes = "Expected " + expectedCells.size() + " cell(s) (" + rowCount(expectedCells.size(), columns)
                + " row(s) x " + columns + " column(s), header included), query response has "
                + actualCells.size() + " cell(s).";
        if (expectedCells.size() != actualCells.size()) {
            sizes = sizes + " Sizes differ, so cells may be shifted and every position after the first missing"
                    + " or extra cell may look wrong.";
        }
        return sizes;
    }

    /**
     * Lists the cells that differ as 'row R, column C (cell N)', truncated to MAX_ROWS_IN_COMPARISON_FAILURE entries.
     */
    private String differencesAsText(List<String> expectedCells, List<String> actualCells, List<String> headersList) {
        int columns = headersList.size();
        List<String> differences = new ArrayList<>();
        int totalDifferences = 0;
        for (int cell = 0; cell < Math.max(expectedCells.size(), actualCells.size()); cell++) {
            String expectedValue = cell < expectedCells.size() ? "'" + expectedCells.get(cell) + "'" : "<MISSING CELL>";
            String actualValue = cell < actualCells.size() ? "'" + actualCells.get(cell) + "'" : "<MISSING CELL>";
            if (!expectedValue.equals(actualValue)) {
                totalDifferences++;
                if (differences.size() < MAX_ROWS_IN_COMPARISON_FAILURE) {
                    int row = columns > 0 ? cell / columns : 0;
                    String column = columns > 0 ? "'" + headersList.get(cell % columns) + "'" : "'?'";
                    differences.add("  row " + row + (row == 0 ? " (headers)" : "") + ", column " + column
                            + " (cell " + cell + "): expected " + expectedValue + " but found " + actualValue);
                }
            }
        }
        return "Cells that differ (" + totalDifferences + " in total"
                + (differences.size() < totalDifferences ? ", only first " + differences.size() + " shown" : "") + "):"
                + System.lineSeparator() + String.join(System.lineSeparator(), differences);
    }

    /**
     * Renders a row-major flat list of cells as one line per row, truncated to MAX_ROWS_IN_COMPARISON_FAILURE rows.
     */
    private String flatResultAsText(List<String> cells, int columns) {
        if (columns <= 0) {
            return "  " + cells;
        }
        List<String> lines = new ArrayList<>();
        int rows = rowCount(cells.size(), columns);
        int rowsToShow = Math.min(rows, MAX_ROWS_IN_COMPARISON_FAILURE);
        for (int row = 0; row < rowsToShow; row++) {
            List<String> values = cells.subList(row * columns, Math.min((row + 1) * columns, cells.size()));
            lines.add("  | " + String.join(" | ", values) + " |");
        }
        if (rowsToShow < rows) {
            lines.add("  ... " + (rows - rowsToShow) + " more row(s)");
        }
        return String.join(System.lineSeparator(), lines);
    }

    private int rowCount(int cells, int columns) {
        return columns > 0 ? (cells + columns - 1) / columns : 0;
    }

    /**
     * @param objetType
     * @param objectName
     * @throws Exception
     */
    @Then("^'(.+?)' '(.+?)' exists$")
    public void checkObjectExists(String objetType, String objectName) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }

        String query;

        switch (objetType) {
            case "Database":
                query = "SELECT datname FROM pg_database WHERE datname = " + "\'" + objectName + "\'" + ";";
                break;

            case "Table":
                query = "SELECT tablename FROM pg_tables WHERE tablename = " + "\'" + objectName + "\'" + ";";
                break;

            case "View":
                query = "SELECT viewname FROM pg_views WHERE viewname = " + "\'" + objectName + "\'" + ";";
                break;

            case "MaterializedView":
                query = "SELECT matviewname FROM pg_matviews WHERE matviewname = " + "\'" + objectName + "\'" + ";";
                break;

            case "Sequence":
                query = "SELECT sequence_name FROM information_schema.sequences WHERE sequence_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Foreign Data Wrapper":
                query = "SELECT foreign_data_wrapper_name FROM information_schema.foreign_data_wrappers WHERE foreign_data_wrapper_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Foreign Server":
                query = "SELECT foreign_server_name FROM information_schema.foreign_servers WHERE foreign_server_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Function":
                query = "SELECT p.proname FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE p.proname = " + "\'" + objectName + "\'" + ";";
                break;

            case "Schema":
                query = "SELECT schema_name from information_schema.schemata join pg_namespace on schema_name = nspname where schema_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Domain":
                query = "SELECT domain_name from information_schema.domains WHERE domain_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Type":
                query = "SELECT user_defined_type_name FROM information_schema.user_defined_types WHERE user_defined_type_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Column":
                query = "select column_name from information_schema.columns WHERE column_name = " + "\'" + objectName + "\'" + ";";
                break;

            default:
                query = "SELECT 1;";
                break;
        }

        try (Statement myStatement = myConnection.createStatement();
             ResultSet rs = myStatement.executeQuery(query)) {
            //if there are no data row
            if (rs.next() == false) {
                fail("There is no " + objetType + ": " + objectName);
            } else {
                //data exist
                String resultName = rs.getString(1);
                assertThat(resultName).as("there are incorrect " + objetType + " name: " + objectName).contains(objectName);
            }
        } catch (Exception e) {
            fail("Could not check whether " + objetType + " " + objectName + " exists -> " + e.getMessage());
        }
    }

    @Then("^'(.+?)' '(.+?)' doesn't exists$")
    public void checkObjectNoExists(String objetType, String objectName) throws Exception {
        Connection myConnection = this.commonspec.getConnection();
        if (myConnection == null) {
            throw new Exception("JDBC connection is not opened");
        }

        String query;

        switch (objetType) {
            case "Database":
                query = "SELECT datname FROM pg_database WHERE datname = " + "\'" + objectName + "\'" + ";";
                break;

            case "Table":
                query = "SELECT tablename FROM pg_tables WHERE tablename = " + "\'" + objectName + "\'" + ";";
                break;

            case "View":
                query = "SELECT viewname FROM pg_views WHERE viewname = " + "\'" + objectName + "\'" + ";";
                break;

            case "MaterializedView":
                query = "SELECT matviewname FROM pg_matviews WHERE matviewname = " + "\'" + objectName + "\'" + ";";
                break;

            case "Sequence":
                query = "SELECT sequence_name FROM information_schema.sequences WHERE sequence_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Foreign Data Wrapper":
                query = "SELECT foreign_data_wrapper_name FROM information_schema.foreign_data_wrappers WHERE foreign_data_wrapper_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Foreign Server":
                query = "SELECT foreign_server_name FROM information_schema.foreign_servers WHERE foreign_server_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Function":
                query = "SELECT p.proname FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE p.proname = " + "\'" + objectName + "\'" + ";";
                break;

            case "Schema":
                query = "SELECT schema_name from information_schema.schemata join pg_namespace on schema_name = nspname where schema_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Domain":
                query = "SELECT domain_name from information_schema.domains WHERE domain_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Type":
                query = "SELECT user_defined_type_name FROM information_schema.user_defined_types WHERE user_defined_type_name = " + "\'" + objectName + "\'" + ";";
                break;

            case "Column":
                query = "select column_name from information_schema.columns WHERE column_name = " + "\'" + objectName + "\'" + ";";
                break;

            default:
                query = "SELECT 1;";
                break;
        }

        try (Statement myStatement = myConnection.createStatement();
             ResultSet rs = myStatement.executeQuery(query)) {
            //if there are no data row, table doesn't exists
            if (rs.next() != false) {
                String resultName = rs.getString(1);
                assertThat(resultName).as(objetType + " " + objectName + " exists").isNotEqualToIgnoringCase(objectName);
            }
        } catch (Exception e) {
            fail("Could not check whether " + objetType + " " + objectName + " exists -> " + e.getMessage());
        }
    }

    @Then(value = "^The result has to have '(.*?)' rows$")
    public void assertLengthResultXDDriver(String resultSize) {
        Assert.assertEquals(ThreadProperty.get("querysize"), resultSize);
        ThreadProperty.remove("querysize");
    }

    /**
     * Connects to a Crossdata server through JDBC.
     *
     * Both keystore and truststore passwords are optional. Omitting one leaves its key out of the
     * connection string, and passing {@value #EMPTY_VALUE} sends it with an empty value, which is
     * what a JKS file stored without password needs [ROCK-15021].
     *
     * By default a connection error fails the step. With the 'capturing the exception' modifier the
     * error is recorded instead, so the scenario can assert it with the step
     * 'an exception IS thrown with class ... and message like ...'.
     *
     * The optional 'properties' parameter carries any other connection property the step does not
     * model, written as in the connection string: {@code properties 'SerializationMode=JSON'}, or
     * several separated by ';'. They are appended as they are.
     */
    @Given(value = "^I connect with JDBC to Crossdata server( using TLS)?( without mutualTLS)? with parameters: host '(.*?)', port '(.*?)', user '(.*?)'(, password '(.*?)')?(, keystore '(.*?)')?(, keystore password '(.*?)')?(, truststore '(.*?)')?(, truststore password '(.*?)')?(, limit '(.*?)')?(, timeout '(.*?)')?(, properties '(.*?)')?( and pagination)?( capturing the exception)?$")
    public void createXDDriver(String security, String withoutMutualTLS, String host, String port, String user, String password, String keyStorePath, String keyStorePass, String trustStorePath, String trustStorePass, String limit, String timeout, String properties, String pagination, String capturingException) {
        if (capturingException != null) {
            commonspec.getExceptions().clear();
        }
        try {
            commonspec.connectToCrossdataDatabase(security != null, withoutMutualTLS != null, host, port, keyStorePath, keyStorePass, trustStorePath, trustStorePass, user, password, pagination != null, limit, timeout, properties);
        } catch (Exception e) {
            if (capturingException == null) {
                fail("Error when we trying to connect to Crossdata Database -> " + e.getMessage());
            } else {
                commonspec.getLogger().error(e.toString());
                commonspec.getExceptions().add(e);
            }
        }
    }

    /**
     * @deprecated kept for callers that invoke this method directly. Use
     * {@link #createXDDriver(String, String, String, String, String, String, String, String, String, String, String, String, String, String, String)},
     * which also accepts extra connection properties. Behaviour is unchanged: no property is added.
     */
    @Deprecated
    public void createXDDriver(String security, String withoutMutualTLS, String host, String port, String user, String password, String keyStorePath, String keyStorePass, String trustStorePath, String trustStorePass, String limit, String timeout, String pagination, String capturingException) {
        createXDDriver(security, withoutMutualTLS, host, port, user, password, keyStorePath, keyStorePass, trustStorePath, trustStorePass, limit, timeout, null, pagination, capturingException);
    }

    /**
     * @deprecated kept for callers that invoke this method directly. Use
     * {@link #createXDDriver(String, String, String, String, String, String, String, String, String, String, String, String, String, String, String)},
     * which also accepts the 'capturing the exception' modifier and extra connection properties.
     * Behaviour is unchanged: a connection error fails the step.
     */
    @Deprecated
    public void createXDDriver(String security, String withoutMutualTLS, String host, String port, String user, String password, String keyStorePath, String keyStorePass, String trustStorePath, String trustStorePass, String limit, String timeout, String pagination) {
        createXDDriver(security, withoutMutualTLS, host, port, user, password, keyStorePath, keyStorePass, trustStorePath, trustStorePass, limit, timeout, null, pagination, null);
    }

    /**
     * Makes the next Crossdata JDBC connections of the scenario authenticate with an OAuth2 access token that is
     * already available (driver AUTH_MODE=token_passthrough), for instance one saved by the step
     * 'I get an OIDC token ...'.
     *
     * The tokens are given by the name of the variable that holds them, not by their value, so they never reach
     * the step text nor the logs: {@code access token 'OIDC_ACCESS'}, not {@code access token '!{OIDC_ACCESS}'}.
     * With {@code -DSHOW_OIDC_TOKENS_IN_SCENARIO=true} they are written in the scenario output, for debugging.
     *
     * To renew the access token once it expires the driver needs the refresh token, the client id and the issuer
     * (or the token endpoint). Without the refresh token the connection stops working when the token expires.
     *
     * The session user of the connection step ('user') must be the user of the token ('sub' claim).
     *
     * @param accessTokenVar  variable that holds the access token
     * @param refreshTokenVar variable that holds the refresh token
     * @param issuer          OIDC issuer, e.g. https://sis.k8s.romeo.labs.stratio.com/sso/oidc
     * @param clientId        OIDC client the tokens were issued to, e.g. virtualizer-dbc
     * @param scope           scopes to ask for when renewing the token
     * @param tokenEndpoint   token endpoint, to use it instead of discovering it from the issuer
     */
    @Given("^I configure the Crossdata JDBC connection with OAuth2 token passthrough: access token '(.+?)'(?:, refresh token '(.+?)')?(?:, issuer '(.+?)')?(?:, client id '(.+?)')?(?:, scope '(.+?)')?(?:, token endpoint '(.+?)')?$")
    public void configureCrossdataOAuth2TokenPassthrough(String accessTokenVar, String refreshTokenVar, String issuer, String clientId, String scope, String tokenEndpoint) {
        String accessToken = tokenFromVariable(accessTokenVar);
        String refreshToken = refreshTokenVar == null ? null : tokenFromVariable(refreshTokenVar);
        if (Boolean.parseBoolean(System.getProperty("SHOW_OIDC_TOKENS_IN_SCENARIO"))) {
            scenario.log("OAuth2 access token (" + accessTokenVar + "): " + accessToken
                    + (refreshToken == null ? "" : "\nOAuth2 refresh token (" + refreshTokenVar + "): " + refreshToken));
        }
        commonspec.setCrossdataOAuth2Settings(CrossdataOAuth2Settings.tokenPassthrough(accessToken, refreshToken, issuer, clientId, scope, tokenEndpoint));
    }

    private static String tokenFromVariable(String variable) {
        String token = ThreadProperty.get(variable);
        Assert.assertNotNull(token, "Variable '" + variable + "' is not defined: give the name of the variable that "
                + "holds the token, e.g. 'OIDC_ACCESS', not its value");
        return token;
    }

    /**
     * Makes the next Crossdata JDBC connections of the scenario authenticate as before any OAuth2 step: basic.
     */
    @Given("^I configure the Crossdata JDBC connection with basic authentication$")
    public void configureCrossdataBasicAuthentication() {
        commonspec.setCrossdataOAuth2Settings(null);
    }

    /**
     * OAuth2 machine to machine (client credentials) is out of the scope of Virtualizer 15.2 (GOAL-2262), so the
     * step is pending until it is implemented.
     */
    @Given("^I configure the Crossdata JDBC connection with OAuth2 M2M(?::.*)?$")
    public void configureCrossdataOAuth2M2M() {
        throw new PendingException("OAuth2 M2M (client credentials) is not implemented yet: it is out of the scope of Virtualizer 15.2");
    }

    /**
     * Makes the next Crossdata JDBC connections of the scenario authenticate with the OAuth2 U2M login of the driver
     * (AUTH_MODE=u2m): the driver opens the authorization URL in the browser, the user logs in and accepts the
     * consent, and the driver gets the tokens and keeps them in its token cache. The BDT plays that browser: it logs
     * in with the given user while the connection step runs. The connection step's user must be this same user.
     *
     * The aspect com.stratio.qa.aspects.CrossdataOAuth2BrowserAspect hands the BDT the URL the driver opens, so it
     * has to be in the META-INF/aop.xml of the test project. No real browser, display nor environment is needed.
     * Unless the scenario sets 'cache.path', the driver keeps its token cache in target/crossdata-oauth2/tokencache,
     * shared by the scenarios of the run and not by the user's own connections.
     *
     * The driver discovers the endpoints from the issuer with the default truststore of the JVM; giving both
     * endpoints avoids that discovery.
     *
     * @param issuer                OIDC issuer, e.g. https://sis.k8s.romeo.labs.stratio.com/sso/oidc
     * @param clientId              OIDC public client of the driver, e.g. virtualizer-dbc
     * @param scope                 scopes to ask for, e.g. 'openid login_data offline_access'. Without
     *                              offline_access there is no refresh token
     * @param authorizationEndpoint authorization endpoint, to use it instead of discovering it
     * @param tokenEndpoint         token endpoint, to use it instead of discovering it
     * @param userName              user to log in with in the browser
     * @param password              its password
     * @param tenant                tenant to choose in the login form
     * @param hostVerifier          whether the fake browser verifies the certificate of the identity server
     */
    @Given("^I configure the Crossdata JDBC connection with OAuth2 U2M:(?: issuer '(.+?)',)? client id '(.+?)'(?:, scope '(.+?)')?(?:, authorization endpoint '(.+?)')?(?:, token endpoint '(.+?)')?, logging in with user '(.+?)', password '(.+?)'(?: and tenant '(.+?)')?( without host name verification)?$")
    public void configureCrossdataOAuth2U2M(String issuer, String clientId, String scope, String authorizationEndpoint, String tokenEndpoint,
                                            String userName, String password, String tenant, String hostVerifier) {
        CrossdataOAuth2Settings settings = CrossdataOAuth2Settings.u2m(issuer, clientId, scope, authorizationEndpoint, tokenEndpoint,
                userName, password, tenant, hostVerifier == null);
        CrossdataOAuth2Browser.checkDriverLauncher();
        if (commonspec.getCrossdataOAuth2Browser() == null) {
            commonspec.setCrossdataOAuth2Browser(new CrossdataOAuth2Browser());
        }
        if (CrossdataOAuth2Settings.driverSetting("cache.path") == null) {
            CrossdataOAuth2Settings.setDriverSetting("cache.path", CrossdataOAuth2Settings.tokenCacheFile().toString());
        }
        commonspec.setCrossdataOAuth2Settings(settings);
    }

    /**
     * Sets an OAuth2 setting of the driver that the connection string cannot carry, for the next connections of the
     * scenario: cache.path, cache.enabled, browser-timeout-sec, expiry-skew-sec, redirect-port...
     * (crossdata-driver.oauth2.* in the driver configuration). They are removed when the scenario ends.
     *
     * An expiry-skew-sec bigger than the life of the access token makes the driver renew it with the refresh token
     * on the next connection, without waiting for it to expire.
     *
     * @param key   setting, e.g. browser-timeout-sec
     * @param value its value
     */
    @Given("^I set the Crossdata OAuth2 driver setting '(.+?)' to '(.*?)'$")
    public void setCrossdataOAuth2DriverSetting(String key, String value) {
        CrossdataOAuth2Settings.setDriverSetting(key, value);
    }

    /**
     * Deletes the driver's U2M token cache of the tests, so that the next U2M connection opens the browser again:
     * the 'cache.path' setting of the scenario or, without it, target/crossdata-oauth2/tokencache. It can go before
     * or after the U2M step. The user's own cache of the driver is never deleted.
     *
     * @throws IOException if the cache cannot be deleted
     */
    @Given("^I clean the Crossdata OAuth2 token cache$")
    public void cleanCrossdataOAuth2TokenCache() throws IOException {
        Path cache = CrossdataOAuth2Settings.tokenCacheFile();
        Files.deleteIfExists(cache);
        commonspec.getLogger().debug("Crossdata OAuth2 token cache deleted: {}", cache);
    }

    /**
     * Checks how many times the driver has opened the browser for the U2M login in the scenario: once per login,
     * none when it reuses or renews the tokens of its cache.
     *
     * @param times expected number of logins in the browser
     */
    @Then("^the Crossdata driver has opened the OAuth2 login in the browser '(\\d+)' times?$")
    public void assertCrossdataOAuth2BrowserLogins(int times) {
        CrossdataOAuth2Browser browser = commonspec.getCrossdataOAuth2Browser();
        Assert.assertNotNull(browser, "The scenario has not configured an OAuth2 U2M connection");
        Assert.assertEquals(browser.requests(), times, "Times the driver opened the browser for the OAuth2 login"
                + (browser.errors().isEmpty() ? "" : ". Failed logins: " + browser.errors()));
    }

    @Given(value = "^I connect with JDBC to database '(.*?)' with url '(.*?)', user '(.*?)'( and password '(.*?)')$")
    public void createCustomDriver(String database, String url, String user, String password) {
        try {
            commonspec.connectToCustomDatabase(database, url, user, password);
        } catch (Exception e) {
            fail("Error when we trying to connect to JDBC Database -> " + e.getMessage());
        }
    }

    /*
     * Test if dataTable is contained in query result
     *
     * @param tableName
     */
    @Then("^I check that result contains:$")
    public void compareTableContains(DataTable dataTable) {
        if (getCommonSpec().getPreviousSqlResult() == null) {
            fail("Result not stored. Query must be executed with step 'I query the database with ...'");
        }
        List<List<String>> datatableCells = normalizeCells(dataTable.cells());
        Map<String, List<String>> previousSqlResult = getCommonSpec().getPreviousSqlResult();
        List<String> headersList = datatableCells.get(0);
        // Check that columns exists
        for (String header : headersList) {
            if (previousSqlResult.get(header) == null) {
                fail("Column '" + header + "' doesn't appear in SQL result. Columns returned by the query: "
                        + previousSqlResult.keySet());
            }
        }
        int responseRows = previousSqlResult.get(headersList.get(0)).size();
        if (responseRows == 0) {
            fail("Query response is empty (0 rows), so it cannot contain the expected rows:" + System.lineSeparator()
                    + expectedRowsAsText(headersList, datatableCells));
        }
        for (int row = 1; row < datatableCells.size(); row++) {
            List<String> expectedRow = datatableCells.get(row);
            List<String> mismatches = new ArrayList<>();
            // Response row matching the highest number of columns, to point at the most likely candidate
            int closestRow = -1;
            int closestMatchedColumns = -1;
            String closestDetail = "";
            boolean found = false;
            for (int responseRow = 0; responseRow < responseRows && !found; responseRow++) {
                found = true;
                for (int col = 0; col < expectedRow.size(); col++) {
                    String expectedValue = expectedRow.get(col);
                    String actualValue = previousSqlResult.get(headersList.get(col)).get(responseRow);
                    if (!actualValue.equals(expectedValue)) {
                        found = false;
                        String detail = "column '" + headersList.get(col) + "': expected '" + expectedValue
                                + "' but found '" + actualValue + "'";
                        if (mismatches.size() < MAX_ROWS_IN_COMPARISON_FAILURE) {
                            mismatches.add("  response row " + responseRow + " -> " + detail);
                        }
                        if (col > closestMatchedColumns) {
                            closestMatchedColumns = col;
                            closestRow = responseRow;
                            closestDetail = detail;
                        }
                        break;
                    }
                }
            }
            if (!found) {
                commonspec.getLogger().error("Full query response:" + System.lineSeparator()
                        + resultAsText(previousSqlResult, responseRows));
                fail("Error in table comparison: row " + row + " of the datatable was not found in the query response."
                        + System.lineSeparator()
                        + "Looking for: " + rowAsText(headersList, expectedRow) + System.lineSeparator()
                        + "Query response has " + responseRows + " row(s). First column that differs on each of them"
                        + (mismatches.size() < responseRows ? " (only first " + mismatches.size() + " shown)" : "") + ":"
                        + System.lineSeparator() + String.join(System.lineSeparator(), mismatches)
                        + System.lineSeparator() + "Closest response row: " + closestRow + " (" + closestMatchedColumns
                        + " of " + expectedRow.size() + " columns matched) -> " + closestDetail);
            }
        }
    }

    /**
     * Value of a ResultSet column as text. A SQL NULL becomes the EMPTY_VALUE marker instead of throwing a
     * NullPointerException, so a nullable column stays comparable against a datatable.
     */
    private static String resultSetValue(java.sql.ResultSet resultSet, int column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value != null ? value.toString() : EMPTY_VALUE;
    }

    /**
     * Cucumber returns genuinely empty DataTable cells (| |) as null, so an empty cell must be compared as the
     * empty string "". A SQL NULL is never "" here: it is stored as the EMPTY_VALUE marker.
     */
    private static String cellValue(String cell) {
        return cell == null ? "" : cell;
    }

    /**
     * Copy of the datatable cells with every empty (null) cell turned into an empty string.
     */
    private static List<List<String>> normalizeCells(List<List<String>> cells) {
        return cells.stream()
                .map(row -> row.stream().map(DatabaseSpec::cellValue).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    /**
     * Renders a single row as '| column=value | column=value |', using only the columns present in the row.
     */
    private String rowAsText(List<String> headersList, List<String> row) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < row.size() && col < headersList.size(); col++) {
            sb.append("| ").append(headersList.get(col)).append("=").append(row.get(col)).append(" ");
        }
        return sb.append("|").toString();
    }

    /**
     * Renders every data row of a datatable (row 0 holds the headers), one per line.
     */
    private String expectedRowsAsText(List<String> headersList, List<List<String>> datatableCells) {
        List<String> rows = new ArrayList<>();
        for (int row = 1; row < datatableCells.size(); row++) {
            rows.add("  " + rowAsText(headersList, datatableCells.get(row)));
        }
        return String.join(System.lineSeparator(), rows);
    }

    /**
     * Renders the stored query response as a header line plus one line per row, truncated to
     * MAX_ROWS_IN_COMPARISON_FAILURE rows.
     */
    private String resultAsText(Map<String, List<String>> sqlResult, int responseRows) {
        List<String> columns = new ArrayList<>(sqlResult.keySet());
        List<String> lines = new ArrayList<>();
        lines.add("  | " + String.join(" | ", columns) + " |");
        int rowsToShow = Math.min(responseRows, MAX_ROWS_IN_COMPARISON_FAILURE);
        for (int row = 0; row < rowsToShow; row++) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                values.add(sqlResult.get(column).get(row));
            }
            lines.add("  | " + String.join(" | ", values) + " |");
        }
        if (rowsToShow < responseRows) {
            lines.add("  ... " + (responseRows - rowsToShow) + " more row(s)");
        }
        return String.join(System.lineSeparator(), lines);
    }

    @Then("^I wait for '(\\d+)' seconds, checking every '(\\d+)' seconds for users existance:$")
    public void waitForUsersExistance(int wait, int period, DataTable users) throws Exception {
        String prefixQuery = "SELECT usename FROM pg_catalog.pg_user WHERE usename IN (";
        String suffixQuery = ") ORDER BY usename;";
        String usersQuery = "";
        boolean finished = false;

        List<String> expectedResult = new ArrayList<>();
        expectedResult.add("usename");
        for (int i = 0; i < users.cells().size(); i++) {
            String user = users.cells().get(i).get(0);
            if (i == 0) {
                usersQuery = usersQuery + "'" + user + "'";
            } else {
                usersQuery = usersQuery + ",'" + user + "'";
            }
            expectedResult.add(user);
        }

        String query = prefixQuery + usersQuery + suffixQuery;

        for (int i = 0; (i <= wait); i += period) {

            try {
                this.selectData(query);
                compareList(expectedResult);
                finished = true;
                commonspec.getLogger().info("Users found in database.");
                break;
            } catch (Throwable e) {
                commonspec.getLogger().info("Users not in database yet. Sleeping for " + period + " seconds.");
                Thread.sleep(period * 1000);
            }
        }

        if (!finished) {
            Assert.fail("Users are not available in database after specified timeout.");
        }
    }

    @Then("^I wait for '(\\d+)' seconds, checking every '(\\d+)' seconds for groups existance:$")
    public void waitForGroupsExistance(int wait, int period, DataTable groups) throws Exception {
        String prefixQuery = "SELECT groname FROM pg_catalog.pg_group WHERE groname IN (";
        String suffixQuery = ") ORDER BY groname;";
        String groupsQuery = "";
        boolean finished = false;

        List<String> expectedResult = new ArrayList<>();
        expectedResult.add("groname");
        for (int i = 0; i < groups.cells().size(); i++) {
            String group = groups.cells().get(i).get(0);
            if (i == 0) {
                groupsQuery = groupsQuery + "'g_" + group + "'";
            } else {
                groupsQuery = groupsQuery + ",'g_" + group + "'";
            }
            expectedResult.add("g_" + group);
        }

        String query = prefixQuery + groupsQuery + suffixQuery;

        for (int i = 0; (i <= wait); i += period) {

            try {
                this.selectData(query);
                compareList(expectedResult);
                finished = true;
                commonspec.getLogger().info("Groups found in database.");
                break;
            } catch (AssertionError | Exception e) {
                commonspec.getLogger().info("Groups not in database yet. Sleeping for " + period + " seconds.");
                Thread.sleep(period * 1000);
            }
        }

        if (!finished) {
            Assert.fail("Groups are not available in database after specified timeout.");
        }
    }

    @Then("^I wait for '(\\d+)' seconds, checking every '(\\d+)' seconds in group '(.*?)' for users existance:$")
    public void waitUsersInGroupExistance(int wait, int period, String group, DataTable users) throws Exception {
        String prefixQuery = "SELECT group_.groname group_name, array_to_string(array_agg(member_.rolname::text ORDER BY member_.rolname), ',') AS members FROM pg_group group_ LEFT JOIN pg_auth_members am ON am.roleid=group_.grosysid LEFT JOIN pg_roles member_ ON member_.oid=am.member WHERE group_.groname='g_";
        String suffixQuery = "' GROUP BY group_.grosysid , group_.groname ORDER BY group_.groname;";
        String query = prefixQuery + group + suffixQuery;

        String usersList = "";
        for (int i = 0; i < users.cells().size(); i++) {
            String user = users.cells().get(i).get(0);
            if (i == 0) {
                usersList = usersList + user;
            } else {
                usersList = usersList + "," + user;
            }
        }

        boolean finished = false;

        List<List<String>> rawData = Arrays.asList(Arrays.asList("group_name", "members"), Arrays.asList("g_" + group, usersList));
        List<String> expectedResult = new ArrayList<>();
        rawData.forEach(e -> expectedResult.addAll(e));

        for (int i = 0; (i <= wait); i += period) {

            try {
                this.selectData(query);
                compareList(expectedResult);
                finished = true;
                commonspec.getLogger().info("Users belonging to group in database found.");
                break;
            } catch (AssertionError | Exception e) {
                commonspec.getLogger().info("Users do not belong to group in database yet. Sleeping for " + period + " seconds.");
                Thread.sleep(period * 1000);
            }
        }

        if (!finished) {
            Assert.fail("Users do not belong to group in database after specified timeout.");
        }
    }

    public void compareList(List<String> tablePattern) throws Exception {

        List<String> sqlTable = new ArrayList<String>();

        // the result is taken from previous step
        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            String ip_value = ThreadProperty.get("queryresponse" + i);
            sqlTable.add(i, ip_value);
        }

        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            ThreadProperty.remove("queryresponse" + i);
        }

        assertThat(tablePattern).as("response is not equal to the expected").isEqualTo(sqlTable);
    }

    @Then("^I save in '(.+?)' the value of column '(.+?)' where column '(.+?)' is '(.+?)'$")
    public void saveValueFromColumnWhere(String variableName, String targetColumn, String filterColumn, String filterValue) throws Exception {
        Map<String, List<String>> previousSqlResult = this.getCommonSpec().getPreviousSqlResult();

        if (previousSqlResult == null || previousSqlResult.isEmpty()) {
            throw new Exception("Result not stored. Query must be executed with step 'I query the database with ...'");
        }

        List<String> filterValues = previousSqlResult.get(filterColumn);
        List<String> targetValues = previousSqlResult.get(targetColumn);

        if (filterValues == null) {
            throw new Exception("Column '" + filterColumn + "' doesn't appear in SQL result");
        }

        if (targetValues == null) {
            throw new Exception("Column '" + targetColumn + "' doesn't appear in SQL result");
        }

        if (filterValues.size() != targetValues.size()) {
            throw new Exception("SQL result columns '" + filterColumn + "' and '" + targetColumn + "' have different sizes");
        }

        for (int i = 0; i < filterValues.size(); i++) {
            if (filterValue.equals(filterValues.get(i))) {
                ThreadProperty.set(variableName, targetValues.get(i));
                scenario.log("Variable: " + variableName + "\n" + "Value: " + targetValues.get(i));
                return;
            }
        }

        throw new Exception("No row found where column '" + filterColumn + "' has value '" + filterValue + "'");
    }

    @Then("^the value of column '(.+?)' where column '(.+?)' is '(.+?)' has to be '(.+?)'$")
    public void checkValueFromColumnWhere(String targetColumn, String filterColumn, String filterValue, String expectedValue) throws Exception {

        Map<String, List<String>> previousSqlResult = this.getCommonSpec().getPreviousSqlResult();

        if (previousSqlResult == null || previousSqlResult.isEmpty()) {
            throw new Exception("Result not stored. Query must be executed with step 'I query the database with ...'");
        }

        List<String> filterValues = previousSqlResult.get(filterColumn);
        List<String> targetValues = previousSqlResult.get(targetColumn);

        if (filterValues == null) {
            throw new Exception("Column '" + filterColumn + "' doesn't appear in SQL result");
        }

        if (targetValues == null) {
            throw new Exception("Column '" + targetColumn + "' doesn't appear in SQL result");
        }

        if (filterValues.size() != targetValues.size()) {
            throw new Exception("SQL result columns '" + filterColumn + "' and '" + targetColumn + "' have different sizes");
        }

        for (int i = 0; i < filterValues.size(); i++) {
            if (filterValue.equals(filterValues.get(i))) {

                String actualValue = targetValues.get(i);
                scenario.log("Actual value: " + actualValue);

                Assertions.assertThat(actualValue)
                        .as("Value of column '" + targetColumn + "' where column '" + filterColumn + "' is '" + filterValue + "'")
                        .isEqualTo(expectedValue);

                return;
            }
        }

        throw new Exception("No row found where column '" + filterColumn + "' has value '" + filterValue + "'");
    }

    @Then("^I save the row where column '(.+?)' is '(.+?)' with prefix '(.+?)'$")
    public void saveRowWhere(String filterColumn, String filterValue, String prefix) throws Exception {

        Map<String, List<String>> previousSqlResult = this.getCommonSpec().getPreviousSqlResult();

        if (previousSqlResult == null || previousSqlResult.isEmpty()) {
            throw new Exception("Result not stored. Query must be executed with step 'I query the database with ...'");
        }

        List<String> filterValues = previousSqlResult.get(filterColumn);

        if (filterValues == null) {
            throw new Exception("Column '" + filterColumn + "' doesn't appear in SQL result");
        }

        int rowIndex = -1;

        for (int i = 0; i < filterValues.size(); i++) {
            if (filterValue.equals(filterValues.get(i))) {
                rowIndex = i;
                break;
            }
        }

        if (rowIndex == -1) {
            throw new Exception("No row found where column '" + filterColumn + "' has value '" + filterValue + "'");
        }

        for (Map.Entry<String, List<String>> entry : previousSqlResult.entrySet()) {

            String columnName = entry.getKey();
            List<String> columnValues = entry.getValue();

            if (rowIndex >= columnValues.size()) {
                throw new Exception("Column '" + columnName + "' does not contain row index " + rowIndex);
            }

            String value = columnValues.get(rowIndex);
            scenario.log("EnvVar " + prefix + "." + columnName + " = " + value);
            ThreadProperty.set(prefix + "." + columnName, value);
        }
    }
}
