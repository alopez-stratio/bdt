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

import com.stratio.qa.utils.JDBCConnection;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.datatable.DataTable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DatabaseSpecTest {

    private DatabaseSpec databaseSpec;

    private CommonG commong;

    @BeforeMethod
    public void setUp() {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            ThreadProperty.remove("queryresponse" + i);
        }
        commong = new CommonG();
        databaseSpec = new DatabaseSpec(commong);
    }

    @AfterMethod
    public void tearDown() {
        JDBCConnection.setConnection(null);
    }

    /**
     * Connection whose only query returns two columns, 'id' and 'comment', with a SQL NULL in the second
     * cell of 'comment'.
     */
    private void mockConnectionWithNullColumn() throws Exception {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnName(1)).thenReturn("id");
        when(metaData.getColumnName(2)).thenReturn("comment");

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn("1", "2");
        when(resultSet.getObject(2)).thenReturn("pk", null);

        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);

        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        JDBCConnection.setConnection(connection);
    }

    private Map<String, List<String>> usersResult() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("user", Arrays.asList("mary", "john", "ana"));
        result.put("id", Arrays.asList("1", "7", "9"));
        result.put("enabled", Arrays.asList("true", "true", "false"));
        return result;
    }

    private DataTable dataTable(List<String>... rows) {
        return DataTable.create(Arrays.asList(rows));
    }

    private String errorMessageFor(DataTable dataTable) {
        AssertionError error = catchThrowableOfType(() -> databaseSpec.compareTableContains(dataTable), AssertionError.class);
        assertThat(error).as("Table comparison should have failed").isNotNull();
        return error.getMessage();
    }

    private void storeQueryResponse(String... cells) {
        for (int i = 0; i < cells.length; i++) {
            ThreadProperty.set("queryresponse" + i, cells[i]);
        }
        ThreadProperty.remove("queryresponse" + cells.length);
    }

    private String errorMessageForComparetable(DataTable dataTable) {
        AssertionError error = catchThrowableOfType(() -> databaseSpec.comparetable(dataTable), AssertionError.class);
        assertThat(error).as("Table comparison should have failed").isNotNull();
        return error.getMessage();
    }

    @Test
    public void testComparetableEqualResult() throws Exception {
        storeQueryResponse("user", "id", "john", "7");
        databaseSpec.comparetable(dataTable(Arrays.asList("user", "id"), Arrays.asList("john", "7")));
    }

    @Test
    public void testComparetablePointsAtTheCellThatDiffers() {
        storeQueryResponse("user", "id", "john", "7", "ana", "9");
        String message = errorMessageForComparetable(dataTable(
                Arrays.asList("user", "id"),
                Arrays.asList("john", "7"),
                Arrays.asList("ana", "3")));
        assertThat(message).contains("query response is not equal to the expected result")
                .contains("Expected 6 cell(s) (3 row(s) x 2 column(s), header included), query response has 6 cell(s).")
                .contains("Cells that differ (1 in total):")
                .contains("row 2, column 'id' (cell 5): expected '3' but found '9'");
    }

    @Test
    public void testComparetableFlagsHeaderRowAndSizeMismatch() {
        storeQueryResponse("user", "identifier", "john", "7");
        String message = errorMessageForComparetable(dataTable(
                Arrays.asList("user", "id"),
                Arrays.asList("john", "7"),
                Arrays.asList("ana", "9")));
        assertThat(message).contains("query response has 4 cell(s)")
                .contains("Sizes differ, so cells may be shifted")
                .contains("row 0 (headers), column 'id' (cell 1): expected 'id' but found 'identifier'")
                .contains("row 2, column 'user' (cell 4): expected 'ana' but found <MISSING CELL>");
    }

    @Test
    public void testComparetableTruncatesDifferences() {
        int cells = (DatabaseSpec.MAX_ROWS_IN_COMPARISON_FAILURE + 4) * 2;
        List<String> response = new ArrayList<>();
        List<List<String>> expectedRows = new ArrayList<>();
        expectedRows.add(Arrays.asList("id", "value"));
        response.add("id");
        response.add("value");
        for (int i = 0; i < cells / 2 - 1; i++) {
            expectedRows.add(Arrays.asList(String.valueOf(i), "expected" + i));
            response.add(String.valueOf(i));
            response.add("actual" + i);
        }
        storeQueryResponse(response.toArray(new String[0]));
        String message = errorMessageForComparetable(DataTable.create(expectedRows));
        assertThat(message).contains("Cells that differ (" + (cells / 2 - 1) + " in total, only first "
                        + DatabaseSpec.MAX_ROWS_IN_COMPARISON_FAILURE + " shown):")
                .contains("column 'value' (cell 3): expected 'expected0' but found 'actual0'");
    }

    @Test
    public void testCompareTableContainsMatchingRow() {
        commong.setPreviousSqlResult(usersResult());
        databaseSpec.compareTableContains(dataTable(Arrays.asList("user", "id"), Arrays.asList("john", "7")));
    }

    /**
     * Cucumber returns genuinely empty cells (| |) as null, so an empty cell must match an empty string
     * in the query response instead of never matching. Same root cause as PR #1174 in modifyData.
     */
    @Test
    public void testCompareTableContainsEmptyCellMatchesEmptyValue() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("col_name", Arrays.asList("id", "Table Properties"));
        result.put("comment", Arrays.asList("pk", ""));
        commong.setPreviousSqlResult(result);
        databaseSpec.compareTableContains(dataTable(
                Arrays.asList("col_name", "comment"),
                Arrays.asList("Table Properties", null)));
    }

    @Test
    public void testComparetableEmptyCellMatchesEmptyValue() throws Exception {
        storeQueryResponse("col_name", "comment", "Table Properties", "");
        databaseSpec.comparetable(dataTable(
                Arrays.asList("col_name", "comment"),
                Arrays.asList("Table Properties", null)));
    }

    /**
     * A nullable column used to throw a NullPointerException, reported as the misleading message
     * 'There are no table <name>: null'.
     */
    @Test
    public void testComparetableOfTableWithNullColumn() throws Exception {
        mockConnectionWithNullColumn();
        databaseSpec.comparetable("mytable", dataTable(
                Arrays.asList("id", "comment"),
                Arrays.asList("1", "pk"),
                Arrays.asList("2", DatabaseSpec.EMPTY_VALUE)));
    }

    @Test
    public void testSelectDataStoresNullColumnAsEmptyMarker() throws Exception {
        mockConnectionWithNullColumn();
        databaseSpec.selectData("SELECT id, comment FROM mytable;");
        assertThat(commong.getPreviousSqlResult().get("comment"))
                .containsExactly("pk", DatabaseSpec.EMPTY_VALUE);
        assertThat(ThreadProperty.get("queryresponse5")).isEqualTo(DatabaseSpec.EMPTY_VALUE);
    }

    @Test
    public void testCompareTableContainsWithoutPreviousResult() {
        commong.setPreviousSqlResult(null);
        String message = errorMessageFor(dataTable(Arrays.asList("user"), Arrays.asList("john")));
        assertThat(message).contains("Result not stored");
    }

    @Test
    public void testCompareTableContainsUnknownColumnListsAvailableColumns() {
        commong.setPreviousSqlResult(usersResult());
        String message = errorMessageFor(dataTable(Arrays.asList("user", "nope"), Arrays.asList("john", "x")));
        assertThat(message).contains("Column 'nope' doesn't appear in SQL result")
                .contains("Columns returned by the query: [user, id, enabled]");
    }

    @Test
    public void testCompareTableContainsEmptyResponseShowsExpectedRows() {
        Map<String, List<String>> emptyResult = new LinkedHashMap<>();
        emptyResult.put("user", new ArrayList<>());
        emptyResult.put("id", new ArrayList<>());
        commong.setPreviousSqlResult(emptyResult);
        String message = errorMessageFor(dataTable(Arrays.asList("user", "id"), Arrays.asList("john", "3")));
        assertThat(message).contains("Query response is empty (0 rows)")
                .contains("| user=john | id=3 |");
    }

    @Test
    public void testCompareTableContainsDetailsMismatchPerResponseRow() {
        commong.setPreviousSqlResult(usersResult());
        String message = errorMessageFor(dataTable(
                Arrays.asList("user", "id", "enabled"),
                Arrays.asList("john", "3", "true")));
        assertThat(message).contains("row 1 of the datatable was not found in the query response")
                .contains("Looking for: | user=john | id=3 | enabled=true |")
                .contains("Query response has 3 row(s)")
                .contains("response row 0 -> column 'user': expected 'john' but found 'mary'")
                .contains("response row 1 -> column 'id': expected '3' but found '7'")
                .contains("response row 2 -> column 'user': expected 'john' but found 'ana'");
    }

    @Test
    public void testCompareTableContainsReportsClosestResponseRow() {
        commong.setPreviousSqlResult(usersResult());
        String message = errorMessageFor(dataTable(
                Arrays.asList("user", "id", "enabled"),
                Arrays.asList("john", "7", "false")));
        assertThat(message).contains("Closest response row: 1 (2 of 3 columns matched) -> column 'enabled': expected 'false' but found 'true'");
    }

    @Test
    public void testCompareTableContainsTruncatesMismatchList() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < DatabaseSpec.MAX_ROWS_IN_COMPARISON_FAILURE + 5; i++) {
            ids.add(String.valueOf(i));
        }
        Map<String, List<String>> bigResult = new LinkedHashMap<>();
        bigResult.put("id", ids);
        commong.setPreviousSqlResult(bigResult);
        String message = errorMessageFor(dataTable(Arrays.asList("id"), Arrays.asList("missing")));
        assertThat(message).contains("Query response has " + ids.size() + " row(s)")
                .contains("(only first " + DatabaseSpec.MAX_ROWS_IN_COMPARISON_FAILURE + " shown)")
                .contains("response row " + (DatabaseSpec.MAX_ROWS_IN_COMPARISON_FAILURE - 1) + " -> ")
                .doesNotContain("response row " + DatabaseSpec.MAX_ROWS_IN_COMPARISON_FAILURE + " -> ");
    }
}
