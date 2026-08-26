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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.stratio.qa.utils.CassandraUtils;
import com.stratio.qa.utils.ExceptionList;
import com.stratio.qa.utils.ThreadProperty;

import io.cucumber.datatable.DataTable;

/**
 * Covers the Cassandra table-definition step without a cluster, by handing {@link DatabaseSpec} a
 * {@link CommonG} whose Cassandra client is a mock. {@code CassandraUtil} is a singleton enum with a
 * final field and no setter, so replacing {@code CommonG} is the only way in.
 */
public class DatabaseSpecCassandraTest {

    private CommonG commong;

    private CassandraUtils cassandraUtils;

    private DatabaseSpec databaseSpec;

    @BeforeMethod
    public void setUp() {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        // ExceptionList is a global singleton and other tests leave entries in it, so these tests
        // start from a known state rather than depending on the order the suite runs in.
        ExceptionList.INSTANCE.getExceptions().clear();
        cassandraUtils = mock(CassandraUtils.class);
        commong = mock(CommonG.class);
        when(commong.getCassandraClient()).thenReturn(cassandraUtils);
        when(commong.getLogger()).thenReturn(LoggerFactory.getLogger(this.getClass()));
        when(commong.getExceptions()).thenReturn(ExceptionList.INSTANCE.getExceptions());
        databaseSpec = new DatabaseSpec(commong);
    }

    @AfterMethod
    public void tearDown() {
        ExceptionList.INSTANCE.getExceptions().clear();
    }

    /**
     * Reproduces the regression the Cucumber 7 upgrade introduced. A table definition marks only
     * some columns as PK and leaves the rest of that row empty; Cucumber 4 handed those cells over
     * as "", Cucumber 7 hands them over as null, and the step called equalsIgnoreCase on them.
     * <p>
     * The resulting NullPointerException was swallowed into the exception list, so the table was
     * never created and every later step of the scenario failed against a table that did not exist.
     */
    @Test
    public void aPartiallyMarkedPrimaryKeyRowDoesNotBreakTheTableDefinition() {
        DataTable definition = DataTable.create(Arrays.asList(
                Arrays.asList("name", "comment", "lucene"),
                Arrays.asList("TEXT", "TEXT", "TEXT"),
                Arrays.asList("PK", null, null)));

        databaseSpec.createTableWithData("analyzertable", "opera", definition);

        assertThat(ExceptionList.INSTANCE.getExceptions())
                .as("an empty cell in the PK row is not an error")
                .isEmpty();

        ArgumentCaptor<ArrayList<String>> pk = captorForPrimaryKey();
        ArgumentCaptor<Map<String, String>> columns = captorForColumns();
        verify(cassandraUtils).createTableWithData(eq("analyzertable"),
                columns.capture(), pk.capture());

        assertThat(pk.getValue()).containsExactly("name");
        assertThat(columns.getValue())
                .containsEntry("name", "TEXT")
                .containsEntry("comment", "TEXT")
                .containsEntry("lucene", "TEXT");
    }

    @Test
    public void severalColumnsCanBeMarkedAsPrimaryKey() {
        DataTable definition = DataTable.create(Arrays.asList(
                Arrays.asList("place", "latitude", "longitude", "lucene"),
                Arrays.asList("TEXT", "DECIMAL", "DECIMAL", "TEXT"),
                Arrays.asList("PK", "PK", null, null)));

        databaseSpec.createTableWithData("location", "opera", definition);

        assertThat(ExceptionList.INSTANCE.getExceptions()).isEmpty();

        ArgumentCaptor<ArrayList<String>> pk = captorForPrimaryKey();
        verify(cassandraUtils).createTableWithData(eq("location"),
                any(), pk.capture());

        assertThat(pk.getValue()).containsExactly("place", "latitude");
    }

    @Test
    public void aTableDefinitionWithoutAPrimaryKeyIsRecordedAsAnError() {
        DataTable definition = DataTable.create(Arrays.asList(
                Arrays.asList("name", "comment"),
                Arrays.asList("TEXT", "TEXT"),
                Arrays.asList(null, null)));

        databaseSpec.createTableWithData("nopk", "opera", definition);

        assertThat(ExceptionList.INSTANCE.getExceptions())
                .extracting(Throwable::getMessage)
                .contains("A PK is needed");
    }

    @Test
    public void aTwoRowDefinitionIsTreatedAsHavingNoPrimaryKey() {
        DataTable definition = DataTable.create(Arrays.asList(
                Arrays.asList("name", "comment"),
                Arrays.asList("TEXT", "TEXT")));

        databaseSpec.createTableWithData("nopk", "opera", definition);

        assertThat(ExceptionList.INSTANCE.getExceptions())
                .extracting(Throwable::getMessage)
                .contains("A PK is needed");
    }

    @Test
    public void everyRowOfTheDatatableIsInserted() {
        DataTable rows = DataTable.create(Arrays.asList(
                Arrays.asList("name", "comment"),
                Arrays.asList("'Kurt'", "'Hello to a man'"),
                Arrays.asList("'Michael'", "'Hello to a woman'")));

        databaseSpec.insertData("opera", "analyzertable", rows);

        assertThat(ExceptionList.INSTANCE.getExceptions()).isEmpty();
        verify(cassandraUtils, times(2))
                .insertData(eq("opera.analyzertable"),
                        any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ArrayList<String>> captorForPrimaryKey() {
        return ArgumentCaptor.forClass((Class<ArrayList<String>>) (Class<?>) ArrayList.class);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, String>> captorForColumns() {
        return ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
    }
}
