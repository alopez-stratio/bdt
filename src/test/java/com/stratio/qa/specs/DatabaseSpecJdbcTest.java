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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.stratio.qa.utils.ExceptionList;
import com.stratio.qa.utils.JDBCConnection;
import com.stratio.qa.utils.ThreadProperty;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.Scenario;

/**
 * Covers the JDBC surface of {@link DatabaseSpec} against a real in-memory H2 database.
 * <p>
 * Every JDBC step reads its connection from {@code CommonG.getConnection()}, which delegates to the
 * static {@link JDBCConnection}. That static is the injection point: handing it an H2 connection
 * makes the steps run real SQL, with real result sets, metadata and errors, and no server anywhere.
 * <p>
 * H2 runs in PostgreSQL compatibility mode because the steps speak PostgreSQL. It provides
 * {@code pg_catalog.pg_user} and the {@code information_schema} views out of the box; the
 * {@code pg_tables} the table-existence steps query does not exist, so {@link #seedPgTablesView()}
 * creates it over {@code information_schema.tables}, which is what PostgreSQL does anyway.
 * <p>
 * Steps whose whole behaviour is the SQL they build (the object-existence switches) are checked
 * with a capturing mock instead: what matters there is the query text, not its result.
 */
public class DatabaseSpecJdbcTest {

    private static final String NO_CONNECTION = "JDBC connection is not opened";

    private Connection connection;

    private CommonG commong;

    private DatabaseSpec databaseSpec;

    private Path sqlFile;

    @BeforeMethod
    public void setUp() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        clearQueryResponses();
        commong = new CommonG();
        databaseSpec = new DatabaseSpec(commong);
        databaseSpec.before(mock(Scenario.class));
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:bdt" + UUID.randomUUID().toString().replace("-", "")
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        JDBCConnection.setConnection(connection);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        clearQueryResponses();
        // The connect steps record their failure in ExceptionList, which is a global singleton that
        // ExceptionListTest asserts is empty. Leaving entries behind breaks that test depending on
        // the order the suite happens to run in.
        ExceptionList.INSTANCE.getExceptions().clear();
        JDBCConnection.setConnection(null);
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        if (sqlFile != null) {
            Files.deleteIfExists(sqlFile);
            sqlFile = null;
        }
    }

    // -----------------------------------------------------------------------
    // executeQuery / executeJdbcQuery
    // -----------------------------------------------------------------------

    @Test
    public void executeQueryRunsTheStatementAndClearsThePreviousResult() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id FROM demo ORDER BY id");
        assertThat(commong.getPreviousSqlResult()).isNotNull();

        databaseSpec.executeQuery("UPDATE demo SET comment = 'updated' WHERE id = 1");

        assertThat(commong.getPreviousSqlResult()).as("a new query invalidates the stored result").isNull();
        assertThat(ThreadProperty.get("querysize")).isNull();
        assertThat(singleValue("SELECT comment FROM demo WHERE id = 1")).isEqualTo("updated");
    }

    @Test
    public void executeQueryReportsABrokenStatement() throws Exception {
        seedDemoTable();

        // executeUpdate throws for a broken statement, and the step then asserts the (still zero)
        // row count is not zero -- which is how a failing query surfaces.
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeQuery("UPDATE nonexistent SET x = 1"));
    }

    @Test
    public void executeQueryNeedsAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.executeQuery("SELECT 1"))
                .withMessage(NO_CONNECTION);
    }

    @Test
    public void jdbcQueryRunsSuccessfully() throws Exception {
        seedDemoTable();

        databaseSpec.executeJdbcQuery("INSERT INTO demo VALUES (3, 'new')", null, null, null);

        assertThat(singleValue("SELECT comment FROM demo WHERE id = 3")).isEqualTo("new");
    }

    @Test
    public void jdbcQueryThatWasExpectedToFailButSucceededIsReported() throws Exception {
        seedDemoTable();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT 1", ", returning an exception", null, null))
                .withMessageContaining("we expected an exception");
    }

    @Test
    public void jdbcQueryFailureIsReportedWhenNoExceptionWasExpected() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT * FROM nonexistent", null, null, null))
                .withMessageContaining("Error executing query");
    }

    @Test
    public void anExpectedJdbcFailureIsAcceptedAndItsMessageChecked() throws Exception {
        databaseSpec.executeJdbcQuery("SELECT * FROM nonexistent", ", returning an exception", null, "nonexistent");
    }

    @Test
    public void anExpectedJdbcFailureWithADifferentMessageIsReported() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery(
                        "SELECT * FROM nonexistent", ", returning an exception", null, "some other message"))
                .withMessageContaining("Query error message");
    }

    @Test
    public void aQueryExpectedToWarnButThatDidNotIsReported() throws Exception {
        seedDemoTable();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT 1", null, ", returning a warning", null))
                .withMessageContaining("we expected a warning");
    }

    @Test
    public void aQueryWarningIsCheckedAgainstTheExpectedMessage() throws Exception {
        Statement statement = mock(Statement.class);
        when(statement.getWarnings()).thenReturn(new SQLWarning("deprecated syntax"));
        Connection warningConnection = mock(Connection.class);
        when(warningConnection.createStatement()).thenReturn(statement);
        JDBCConnection.setConnection(warningConnection);

        databaseSpec.executeJdbcQuery("SELECT 1", null, ", returning a warning", "deprecated syntax");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT 1", null, ", returning a warning", "another warning"));
    }

    @Test
    public void jdbcQueryNeedsAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT 1", null, null, null))
                .withMessage(NO_CONNECTION);
    }

    @Test
    public void theDocStringFormOfTheJdbcQueryStepDelegatesToTheInlineOne() throws Exception {
        seedDemoTable();

        databaseSpec.executeJdbcQueryDocStringFlags(null, null, null, "INSERT INTO demo VALUES (4, 'docstring')");

        assertThat(singleValue("SELECT comment FROM demo WHERE id = 4")).isEqualTo("docstring");
    }

    @Test
    public void aCancelledQueryIsAcceptedWhenItReportsBeingCancelled() throws Exception {
        // H2 reports a cancellation as SQLTimeoutException, while the step is written against the
        // SQLSyntaxErrorException Crossdata raises, so the statement is mocked here.
        Statement statement = mock(Statement.class);
        when(statement.execute(anyString())).thenThrow(new SQLSyntaxErrorException("Query cancelled by user"));
        Connection cancelConnection = mock(Connection.class);
        when(cancelConnection.createStatement()).thenReturn(statement);
        JDBCConnection.setConnection(cancelConnection);

        databaseSpec.executeJdbcQuery("SELECT 1", 1);

        verify(statement).execute("SELECT 1");
    }

    @Test
    public void aQueryThatWasSupposedToBeCancelledButCompletedIsReported() throws Exception {
        seedDemoTable();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT 1", 1))
                .withMessageContaining("it should be cancelled");
    }

    @Test
    public void theCancellableQueryStepNeedsAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQuery("SELECT 1", 1))
                .withMessage(NO_CONNECTION);
    }

    // -----------------------------------------------------------------------
    // executeJdbcQueryFile
    // -----------------------------------------------------------------------

    @Test
    public void aSqlFileIsRunStatementByStatementSkippingBlanksAndComments() throws Exception {
        sqlFile = writeSqlFile(
                "-- a leading comment",
                "",
                "CREATE TABLE from_file (id INT, name VARCHAR(20));",
                "-- another comment",
                "INSERT INTO from_file VALUES (1, 'one');",
                "INSERT INTO from_file",
                "  VALUES (2, 'two');");

        databaseSpec.executeJdbcQueryFile(sqlFile.toString(), null, null, null);

        assertThat(singleValue("SELECT count(*) FROM from_file")).isEqualTo("2");
        assertThat(singleValue("SELECT name FROM from_file WHERE id = 2")).isEqualTo("two");
    }

    @Test
    public void aStatementWithoutATrailingSemicolonIsNeverRun() throws Exception {
        sqlFile = writeSqlFile("CREATE TABLE never_created (id INT)");

        databaseSpec.executeJdbcQueryFile(sqlFile.toString(), null, null, null);

        assertThat(tableExists("never_created"))
                .as("only statements terminated with ';' are executed")
                .isFalse();
    }

    @Test
    public void aBrokenSqlFileIsReported() throws Exception {
        sqlFile = writeSqlFile("SELECT * FROM nonexistent;");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQueryFile(sqlFile.toString(), null, null, null))
                .withMessageContaining("Error running SQL file")
                .withMessageContaining("nonexistent");
    }

    @Test
    public void aMissingSqlFileIsReported() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQueryFile("/nonexistent/path/to/file.sql", null, null, null))
                .withMessageContaining("Error running SQL file");
    }

    @Test
    public void theSqlFileStepNeedsAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.executeJdbcQueryFile("whatever.sql", null, null, null))
                .withMessage(NO_CONNECTION);
    }

    // -----------------------------------------------------------------------
    // selectData
    // -----------------------------------------------------------------------

    @Test
    public void selectDataStoresColumnsRowsAndSize() throws Exception {
        seedDemoTable();

        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        assertThat(ThreadProperty.get("querysize")).isEqualTo("2");
        assertThat(commong.getPreviousSqlResult())
                .containsEntry("id", Arrays.asList("1", "2"))
                .containsEntry("comment", Arrays.asList("pk", "fk"));
        assertThat(queryResponses()).containsExactly("id", "comment", "1", "pk", "2", "fk");
    }

    @Test
    public void selectDataKeepsTheColumnOrderTheQueryReturned() throws Exception {
        seedDemoTable();

        databaseSpec.selectData("SELECT comment, id FROM demo ORDER BY id");

        assertThat(commong.getPreviousSqlResult().keySet()).containsExactly("comment", "id");
    }

    @Test
    public void selectDataReportsAFailingQuery() throws Exception {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.selectData("SELECT * FROM nonexistent"))
                .withMessageContaining("There are no response from SELECT");
    }

    @Test
    public void selectDataNeedsAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.selectData("SELECT 1"))
                .withMessage(NO_CONNECTION);
    }

    @Test
    public void theDocStringFormOfTheSelectStepDelegatesToTheInlineOne() throws Exception {
        seedDemoTable();

        databaseSpec.selectDataDocString("SELECT id FROM demo ORDER BY id");

        assertThat(ThreadProperty.get("querysize")).isEqualTo("2");
    }

    // -----------------------------------------------------------------------
    // Table comparison
    // -----------------------------------------------------------------------

    @Test
    public void aTableEqualToTheDatatablePasses() throws Exception {
        seedDemoTable();

        databaseSpec.comparetable("demo", dataTable(
                Arrays.asList("id", "comment"),
                Arrays.asList("1", "pk"),
                Arrays.asList("2", "fk")));
    }

    @Test
    public void aTableDifferentFromTheDatatableIsReportedWithTheDifferences() throws Exception {
        seedDemoTable();

        AssertionError error = catchThrowableOfType(() -> databaseSpec.comparetable("demo", dataTable(
                Arrays.asList("id", "comment"),
                Arrays.asList("1", "pk"),
                Arrays.asList("2", "OTHER"))), AssertionError.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage())
                .contains("is not equal to the expected result")
                .contains("OTHER");
    }

    @Test
    public void comparingAMissingTableIsReported() {
        AssertionError error = catchThrowableOfType(
                () -> databaseSpec.comparetable("nonexistent", dataTable(Arrays.asList("id"))),
                AssertionError.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("There are no table nonexistent");
    }

    @Test
    public void aTableNameThatIsNotAPlainIdentifierIsRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> databaseSpec.comparetable("demo; DROP TABLE demo", dataTable(Arrays.asList("id"))))
                .withMessageContaining("Invalid table name");
    }

    @Test
    public void theTableComparisonNeedsAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.comparetable("demo", dataTable(Arrays.asList("id"))))
                .withMessage(NO_CONNECTION);
    }

    @Test
    public void theStoredQueryResponseIsComparedAgainstTheDatatable() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        databaseSpec.comparetable(dataTable(
                Arrays.asList("id", "comment"),
                Arrays.asList("1", "pk"),
                Arrays.asList("2", "fk")));
    }

    @Test
    public void aStoredQueryResponseDifferentFromTheDatatableIsReported() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        AssertionError error = catchThrowableOfType(() -> databaseSpec.comparetable(dataTable(
                Arrays.asList("id", "comment"),
                Arrays.asList("1", "pk"))), AssertionError.class);

        assertThat(error).isNotNull();
        assertThat(error.getMessage()).contains("query response is not equal to the expected result");
    }

    // -----------------------------------------------------------------------
    // Table existence
    // -----------------------------------------------------------------------

    @Test
    public void anExistingTableIsFound() throws Exception {
        seedDemoTable();
        seedPgTablesView();

        databaseSpec.checkTable("demo");
    }

    @Test
    public void aMissingTableIsReported() throws Exception {
        seedPgTablesView();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkTable("nonexistent"))
                .withMessageContaining("There is no table nonexistent");
    }

    @Test
    public void aTableExpectedToBeAbsentAndThatIsAbsentPasses() throws Exception {
        seedPgTablesView();

        databaseSpec.checkTableFalse("nonexistent");
    }

    @Test
    public void aTableExpectedToBeAbsentButPresentIsReported() throws Exception {
        seedDemoTable();
        seedPgTablesView();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkTableFalse("demo"))
                .withMessageContaining("table exists demo");
    }

    @Test
    public void aSqlErrorIsReportedByTheTableExistenceSteps() {
        // No pg_tables view here, so the query itself fails. "I could not ask" must not be
        // confused with "the table is not there".
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkTable("demo"))
                .withMessageContaining("Could not check whether table demo exists");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkTableFalse("demo"))
                .withMessageContaining("Could not check whether table demo exists");
    }

    @Test
    public void aSqlErrorIsReportedByTheObjectExistenceSteps() throws Exception {
        connection.close();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkObjectExists("Sequence", "demo_seq"))
                .withMessageContaining("Could not check whether Sequence demo_seq exists");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkObjectNoExists("Sequence", "demo_seq"))
                .withMessageContaining("Could not check whether Sequence demo_seq exists");
    }

    @Test
    public void theTableExistenceStepsNeedAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.checkTable("demo"))
                .withMessage(NO_CONNECTION);
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.checkTableFalse("demo"))
                .withMessage(NO_CONNECTION);
    }

    // -----------------------------------------------------------------------
    // Object existence: the query each object type maps to
    // -----------------------------------------------------------------------

    @DataProvider(name = "objectQueries")
    public Object[][] objectQueries() {
        return new Object[][]{
                {"Database", "SELECT datname FROM pg_database WHERE datname = 'obj';"},
                {"Table", "SELECT tablename FROM pg_tables WHERE tablename = 'obj';"},
                {"View", "SELECT viewname FROM pg_views WHERE viewname = 'obj';"},
                {"MaterializedView", "SELECT matviewname FROM pg_matviews WHERE matviewname = 'obj';"},
                {"Sequence", "SELECT sequence_name FROM information_schema.sequences WHERE sequence_name = 'obj';"},
                {"Foreign Data Wrapper", "SELECT foreign_data_wrapper_name FROM information_schema.foreign_data_wrappers WHERE foreign_data_wrapper_name = 'obj';"},
                {"Foreign Server", "SELECT foreign_server_name FROM information_schema.foreign_servers WHERE foreign_server_name = 'obj';"},
                {"Function", "SELECT p.proname FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE p.proname = 'obj';"},
                {"Schema", "SELECT schema_name from information_schema.schemata join pg_namespace on schema_name = nspname where schema_name = 'obj';"},
                {"Domain", "SELECT domain_name from information_schema.domains WHERE domain_name = 'obj';"},
                {"Type", "SELECT user_defined_type_name FROM information_schema.user_defined_types WHERE user_defined_type_name = 'obj';"},
                {"Column", "select column_name from information_schema.columns WHERE column_name = 'obj';"},
                {"Anything else", "SELECT 1;"},
        };
    }

    @Test(dataProvider = "objectQueries")
    public void eachObjectTypeMapsToItsCatalogQueryWhenCheckingExistence(String objectType, String expectedQuery) throws Exception {
        assertThat(captureQueryOf(() -> databaseSpec.checkObjectExists(objectType, "obj"), "obj"))
                .isEqualTo(expectedQuery);
    }

    @Test(dataProvider = "objectQueries")
    public void eachObjectTypeMapsToItsCatalogQueryWhenCheckingAbsence(String objectType, String expectedQuery) throws Exception {
        assertThat(captureQueryOf(() -> databaseSpec.checkObjectNoExists(objectType, "obj"), "obj", false))
                .isEqualTo(expectedQuery);
    }

    @Test
    public void anExistingSequenceIsFound() throws Exception {
        exec("CREATE SEQUENCE demo_seq");

        databaseSpec.checkObjectExists("Sequence", "demo_seq");
    }

    @Test
    public void aMissingSequenceIsReported() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkObjectExists("Sequence", "nonexistent_seq"))
                .withMessageContaining("There is no Sequence: nonexistent_seq");
    }

    @Test
    public void aSequenceExpectedToBeAbsentAndThatIsAbsentPasses() throws Exception {
        databaseSpec.checkObjectNoExists("Sequence", "nonexistent_seq");
    }

    @Test
    public void anObjectExpectedToBeAbsentButPresentIsReported() throws Exception {
        exec("CREATE SEQUENCE demo_seq");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkObjectNoExists("Sequence", "demo_seq"))
                .withMessageContaining("Sequence demo_seq exists");
    }

    @Test
    public void theObjectExistenceStepsNeedAnOpenConnection() {
        JDBCConnection.setConnection(null);

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.checkObjectExists("Table", "demo"))
                .withMessage(NO_CONNECTION);
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.checkObjectNoExists("Table", "demo"))
                .withMessage(NO_CONNECTION);
    }

    // -----------------------------------------------------------------------
    // Waiting for users and groups
    // -----------------------------------------------------------------------

    @Test
    public void usersAlreadyInTheDatabaseAreFoundOnTheFirstAttempt() throws Exception {
        exec("CREATE USER bdt_ana PASSWORD 'x'");
        exec("CREATE USER bdt_mary PASSWORD 'x'");

        databaseSpec.waitForUsersExistance(10, 1, dataTable(
                Arrays.asList("bdt_ana"), Arrays.asList("bdt_mary")));
    }

    @Test
    public void missingUsersAreReportedAfterTheTimeout() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.waitForUsersExistance(0, 1, dataTable(Arrays.asList("bdt_nobody"))))
                .withMessageContaining("Users are not available in database");
    }

    @Test
    public void missingGroupsAreReportedAfterTheTimeout() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.waitForGroupsExistance(0, 1, dataTable(Arrays.asList("nobody"))))
                .withMessageContaining("Groups are not available in database");
    }

    @Test
    public void groupsAlreadyInTheDatabaseAreFoundOnTheFirstAttempt() throws Exception {
        // H2 exposes pg_catalog.pg_group as a system view that CREATE ROLE does not feed, so the
        // rows the step expects are stubbed instead. The 'g_' prefix is the step's own convention.
        stubQueryResult(Arrays.asList("groname"), Arrays.asList(Arrays.asList("g_devs")));

        databaseSpec.waitForGroupsExistance(10, 1, dataTable(Arrays.asList("devs")));
    }

    @Test
    public void usersBelongingToTheGroupAreFoundOnTheFirstAttempt() throws Exception {
        seedPgGroupTables();
        exec("INSERT INTO pg_group VALUES ('g_devs', 1)");
        exec("INSERT INTO pg_roles VALUES (10, 'bdt_ana'), (11, 'bdt_mary')");
        exec("INSERT INTO pg_auth_members VALUES (1, 10), (1, 11)");

        databaseSpec.waitUsersInGroupExistance(10, 1, "devs",
                dataTable(Arrays.asList("bdt_ana"), Arrays.asList("bdt_mary")));
    }

    @Test
    public void missingGroupMembershipIsReportedAfterTheTimeout() throws Exception {
        seedPgGroupTables();

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.waitUsersInGroupExistance(0, 1, "devs", dataTable(Arrays.asList("bdt_ana"))))
                .withMessageContaining("Users do not belong to group in database");
    }

    @Test
    public void aQueryThatCannotRunIsRetriedUntilTheTimeout() {
        // The pg_group tables are not seeded, so the SELECT itself fails. That is precisely the
        // state these steps exist to wait through, so it must be retried and end in the timeout
        // message, not abort on the first attempt with the SELECT error.
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.waitUsersInGroupExistance(1, 1, "devs", dataTable(Arrays.asList("bdt_ana"))))
                .withMessageContaining("Users do not belong to group in database");
    }

    // -----------------------------------------------------------------------
    // compareList
    // -----------------------------------------------------------------------

    @Test
    public void compareListMatchesTheStoredQueryResponseAndConsumesIt() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id FROM demo ORDER BY id");

        databaseSpec.compareList(Arrays.asList("id", "1", "2"));

        assertThat(queryResponses()).as("the stored response is consumed").isEmpty();
    }

    @Test
    public void compareListReportsADifferentResponse() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id FROM demo ORDER BY id");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.compareList(Arrays.asList("id", "1")))
                .withMessageContaining("response is not equal to the expected");
    }

    // -----------------------------------------------------------------------
    // Reading single values and rows out of the stored result
    // -----------------------------------------------------------------------

    @Test
    public void aValueIsSavedFromTheRowMatchingTheFilter() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        databaseSpec.saveValueFromColumnWhere("savedComment", "comment", "id", "2");

        assertThat(ThreadProperty.get("savedComment")).isEqualTo("fk");
    }

    @Test
    public void savingAValueNeedsAPreviousQuery() {
        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.saveValueFromColumnWhere("x", "comment", "id", "1"))
                .withMessageContaining("Result not stored");
    }

    @Test
    public void savingAValueNeedsBothColumnsInTheResult() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        assertThat(catchThrowable(() -> databaseSpec.saveValueFromColumnWhere("x", "comment", "missing", "1")))
                .hasMessageContaining("Column 'missing' doesn't appear in SQL result");
        assertThat(catchThrowable(() -> databaseSpec.saveValueFromColumnWhere("x", "missing", "id", "1")))
                .hasMessageContaining("Column 'missing' doesn't appear in SQL result");
    }

    @Test
    public void savingAValueNeedsAMatchingRow() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        assertThatExceptionOfType(Exception.class)
                .isThrownBy(() -> databaseSpec.saveValueFromColumnWhere("x", "comment", "id", "99"))
                .withMessageContaining("No row found where column 'id' has value '99'");
    }

    @Test
    public void aValueIsCheckedAgainstTheRowMatchingTheFilter() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        databaseSpec.checkValueFromColumnWhere("comment", "id", "1", "pk");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.checkValueFromColumnWhere("comment", "id", "1", "other"));
    }

    @Test
    public void checkingAValueNeedsAPreviousQueryMatchingColumnsAndAMatchingRow() throws Exception {
        assertThat(catchThrowable(() -> databaseSpec.checkValueFromColumnWhere("comment", "id", "1", "pk")))
                .hasMessageContaining("Result not stored");

        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        assertThat(catchThrowable(() -> databaseSpec.checkValueFromColumnWhere("comment", "missing", "1", "pk")))
                .hasMessageContaining("Column 'missing' doesn't appear in SQL result");
        assertThat(catchThrowable(() -> databaseSpec.checkValueFromColumnWhere("missing", "id", "1", "pk")))
                .hasMessageContaining("Column 'missing' doesn't appear in SQL result");
        assertThat(catchThrowable(() -> databaseSpec.checkValueFromColumnWhere("comment", "id", "99", "pk")))
                .hasMessageContaining("No row found where column 'id' has value '99'");
    }

    @Test
    public void aWholeRowIsSavedUnderTheGivenPrefix() throws Exception {
        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        databaseSpec.saveRowWhere("id", "2", "row");

        assertThat(ThreadProperty.get("row.id")).isEqualTo("2");
        assertThat(ThreadProperty.get("row.comment")).isEqualTo("fk");
    }

    @Test
    public void savingARowNeedsAPreviousQueryTheColumnAndAMatchingRow() throws Exception {
        assertThat(catchThrowable(() -> databaseSpec.saveRowWhere("id", "1", "row")))
                .hasMessageContaining("Result not stored");

        seedDemoTable();
        databaseSpec.selectData("SELECT id, comment FROM demo ORDER BY id");

        assertThat(catchThrowable(() -> databaseSpec.saveRowWhere("missing", "1", "row")))
                .hasMessageContaining("Column 'missing' doesn't appear in SQL result");
        assertThat(catchThrowable(() -> databaseSpec.saveRowWhere("id", "99", "row")))
                .hasMessageContaining("No row found where column 'id' has value '99'");
    }

    // -----------------------------------------------------------------------
    // Connecting
    // -----------------------------------------------------------------------

    @Test
    public void aFailedPostgresConnectionIsRecordedInsteadOfThrown() throws Exception {
        databaseSpec.connectDatabasePostgres("md5", null, null, "db", "127.0.0.1", "1", "user", "pass", null, null, null);

        assertThat(commong.getExceptions()).as("the step records the failure for a later assertion").isNotEmpty();
    }

    @Test
    public void aFailedSecurePostgresConnectionIsRecordedInsteadOfThrown() throws Exception {
        databaseSpec.connectDatabasePostgres("TLS", null, null, "db", "127.0.0.1", "1", "user", "pass",
                "ca.crt", "client.crt", "client.key");

        assertThat(commong.getExceptions()).isNotEmpty();
    }

    @Test
    public void anUnknownCustomDatabaseIsReported() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> databaseSpec.createCustomDriver("h2", "jdbc:h2:mem:whatever", "sa", ""))
                .withMessageContaining("Error when we trying to connect to JDBC Database");
    }

    @Test
    public void closingTheConnectionClearsIt() throws Exception {
        databaseSpec.closeDatabase();

        assertThat(JDBCConnection.getConnection()).isNull();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs a step against a connection that answers every query with a single row holding
     * {@code value}, and returns the SQL the step asked for.
     */
    private String captureQueryOf(ThrowingRunnable step, String value) throws Exception {
        return captureQueryOf(step, value, true);
    }

    /**
     * Runs a step against a connection that answers every query with either one row holding
     * {@code value} or no rows at all, and returns the SQL the step asked for. Which of the two the
     * step needs in order to pass differs: the existence step wants the row, the absence step wants
     * an empty result.
     */
    private String captureQueryOf(ThrowingRunnable step, String value, boolean withRow) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(withRow);
        when(resultSet.getString(1)).thenReturn(value);
        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        Connection capturing = mock(Connection.class);
        when(capturing.createStatement()).thenReturn(statement);
        JDBCConnection.setConnection(capturing);

        step.run();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement).executeQuery(sql.capture());
        return sql.getValue();
    }

    private void seedDemoTable() throws SQLException {
        exec("CREATE TABLE demo (id INT PRIMARY KEY, comment VARCHAR(50))");
        exec("INSERT INTO demo VALUES (1, 'pk'), (2, 'fk')");
    }

    /**
     * H2 has no {@code pg_tables}, so the table-existence steps get the PostgreSQL shape of it:
     * schema first, table name second, which is the column {@code checkTable} reads.
     */
    private void seedPgTablesView() throws SQLException {
        exec("CREATE VIEW pg_tables AS SELECT table_schema AS schemaname, table_name AS tablename "
                + "FROM information_schema.tables");
    }

    /**
     * The three catalog tables {@code waitUsersInGroupExistance} joins. H2 has none of them under
     * the default schema, so they are created with the columns the step's query reads.
     */
    private void seedPgGroupTables() throws SQLException {
        exec("CREATE TABLE pg_group (groname VARCHAR(50), grosysid INT)");
        exec("CREATE TABLE pg_auth_members (roleid INT, member INT)");
        exec("CREATE TABLE pg_roles (oid INT, rolname VARCHAR(50))");
    }

    /**
     * Installs a connection whose only query answers with the given columns and rows, for the steps
     * whose query cannot run against H2.
     */
    private void stubQueryResult(List<String> columns, List<List<String>> rows) throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(metaData.getColumnCount()).thenReturn(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            when(metaData.getColumnName(i + 1)).thenReturn(columns.get(i));
        }

        AtomicInteger cursor = new AtomicInteger(-1);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(resultSet.next()).thenAnswer(invocation -> cursor.incrementAndGet() < rows.size());
        when(resultSet.getObject(anyInt()))
                .thenAnswer(invocation -> rows.get(cursor.get()).get((Integer) invocation.getArgument(0) - 1));

        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        Connection stubbed = mock(Connection.class);
        when(stubbed.createStatement()).thenReturn(statement);
        JDBCConnection.setConnection(stubbed);
    }

    private void exec(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String singleValue(String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        return singleValue("SELECT count(*) FROM information_schema.tables WHERE table_name = '"
                + tableName + "'").equals("1");
    }

    // -----------------------------------------------------------------------
    // Crossdata OAuth2 settings
    // -----------------------------------------------------------------------

    @Test
    public void theCrossdataOAuth2StepsSetAndClearTheSettingsOfTheScenario() {
        ThreadProperty.set("T_ACCESS", "AT");
        ThreadProperty.set("T_REFRESH", "RT");
        try {
            databaseSpec.configureCrossdataOAuth2TokenPassthrough("T_ACCESS", "T_REFRESH", "https://sis/sso/oidc", "virtualizer-dbc", null, null);
            assertThat(commong.getCrossdataOAuth2Settings().connectionProperties())
                    .as("the tokens are read from the variables")
                    .startsWith(";AUTH_MODE=token_passthrough;OAUTH2_ACCESS_TOKEN=AT;OAUTH2_REFRESH_TOKEN=RT;");

            databaseSpec.configureCrossdataBasicAuthentication();
            assertThat(commong.getCrossdataOAuth2Settings()).isNull();

            databaseSpec.configureCrossdataOAuth2TokenPassthrough("T_ACCESS", null, null, null, null, null);
            databaseSpec.before(mock(Scenario.class));
            assertThat(commong.getCrossdataOAuth2Settings()).as("a new scenario starts with basic authentication").isNull();
        } finally {
            ThreadProperty.remove("T_ACCESS");
            ThreadProperty.remove("T_REFRESH");
        }
    }

    @Test
    public void aCrossdataOAuth2TokenMustBeGivenByItsVariable() {
        assertThat(catchThrowable(() -> databaseSpec.configureCrossdataOAuth2TokenPassthrough("eyJ.not.a.variable", null, null, null, null, null)))
                .hasMessageContaining("Variable 'eyJ.not.a.variable' is not defined");
        assertThat(commong.getCrossdataOAuth2Settings()).isNull();
    }

    @Test
    public void crossdataOAuth2TokensAreWrittenInTheScenarioOnlyWhenAsked() {
        Scenario scenario = mock(Scenario.class);
        databaseSpec.before(scenario);
        ThreadProperty.set("T_ACCESS", "AT");
        try {
            databaseSpec.configureCrossdataOAuth2TokenPassthrough("T_ACCESS", null, null, null, null, null);
            verify(scenario, never()).log(anyString());

            System.setProperty("SHOW_OIDC_TOKENS_IN_SCENARIO", "true");
            databaseSpec.configureCrossdataOAuth2TokenPassthrough("T_ACCESS", null, null, null, null, null);
            verify(scenario).log("OAuth2 access token (T_ACCESS): AT");
        } finally {
            System.clearProperty("SHOW_OIDC_TOKENS_IN_SCENARIO");
            ThreadProperty.remove("T_ACCESS");
        }
    }

    @Test
    public void crossdataOAuth2M2MIsPending() {
        assertThat(catchThrowable(() -> databaseSpec.configureCrossdataOAuth2M2M()))
                .isInstanceOf(PendingException.class)
                .hasMessageContaining("out of the scope of Virtualizer 15.2");
    }

    private Path writeSqlFile(String... lines) throws Exception {
        File file = File.createTempFile("bdt-sql-", ".sql");
        file.deleteOnExit();
        Files.write(file.toPath(), String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
        return file.toPath();
    }

    private DataTable dataTable(List<String>... rows) {
        return DataTable.create(Arrays.asList(rows));
    }

    private List<String> queryResponses() {
        List<String> responses = new java.util.ArrayList<>();
        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            responses.add(ThreadProperty.get("queryresponse" + i));
        }
        return responses;
    }

    private void clearQueryResponses() {
        for (int i = 0; ThreadProperty.get("queryresponse" + i) != null; i++) {
            ThreadProperty.remove("queryresponse" + i);
        }
        ThreadProperty.remove("querysize");
    }
}
