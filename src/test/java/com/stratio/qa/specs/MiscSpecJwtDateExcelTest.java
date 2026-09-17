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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.stratio.qa.utils.ExceptionList;
import com.stratio.qa.utils.ThreadProperty;

import io.cucumber.datatable.DataTable;

/**
 * Covers the parts of {@link MiscSpec} that need no service behind them: JWT generation and
 * decoding, the date helpers, the Excel reader and the JSONPath matcher.
 */
public class MiscSpecJwtDateExcelTest {

    private static final String PAYLOAD_TENANT = "NONE";

    private CommonG commong;

    private MiscSpec miscSpec;

    private File excelFile;

    @BeforeMethod
    public void setUp() {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        commong = new CommonG();
        miscSpec = new MiscSpec(commong);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ExceptionList.INSTANCE.getExceptions().clear();
        if (excelFile != null) {
            Files.deleteIfExists(excelFile.toPath());
            excelFile = null;
        }
    }

    // -----------------------------------------------------------------------
    // JWT
    // -----------------------------------------------------------------------

    @Test
    public void aJwtSignedWithASecretCarriesTheGivenClaims() {
        miscSpec.getJwtFromData(PAYLOAD_TENANT, "ana", "ana@stratio.com", "admin,devs",
                "stratio", "a-secret-key-long-enough-for-hs256", "jwt");

        String jwt = ThreadProperty.get("jwt");
        assertThat(jwt).isNotNull();
        assertThat(jwt.split("\\.")).as("header, payload and signature").hasSize(3);

        miscSpec.decodeJwtPayload("jwt", "payload");
        String payload = ThreadProperty.get("payload");
        assertThat(payload)
                .contains("\"cn\":\"ana\"")
                .contains("\"uid\":\"ana\"")
                .contains("\"tenant\":\"" + PAYLOAD_TENANT + "\"")
                .contains("\"mail\":\"ana@stratio.com\"")
                .contains("\"iss\":\"stratio\"")
                .contains("\"groups\":[\"admin\",\"devs\"]");
    }

    @Test
    public void aJwtWithoutGroupsCarriesAnEmptyGroupList() {
        miscSpec.getJwtFromData(PAYLOAD_TENANT, "ana", "ana@stratio.com", null,
                "stratio", "a-secret-key-long-enough-for-hs256", "jwtNoGroups");

        miscSpec.decodeJwtPayload("jwtNoGroups", "payloadNoGroups");

        assertThat(ThreadProperty.get("payloadNoGroups")).contains("\"groups\":[]");
    }

    @Test
    public void aJwtCanBeSignedWithAnRsaPrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----newline"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "newline-----END PRIVATE KEY-----";

        miscSpec.getJwtUsingPrivateKey(PAYLOAD_TENANT, "ana", "ana@stratio.com", "admin",
                "stratio", pem, "jwtRsa");

        String jwt = ThreadProperty.get("jwtRsa");
        assertThat(jwt).isNotNull();

        miscSpec.decodeJwtPayload("jwtRsa", "payloadRsa");
        assertThat(ThreadProperty.get("payloadRsa"))
                .contains("\"cn\":\"ana\"")
                .contains("\"groups\":[\"admin\"]");
    }

    @Test
    public void anUnparseablePrivateKeyIsReported() {
        assertThat(catchThrowable(() -> miscSpec.getJwtUsingPrivateKey(PAYLOAD_TENANT, "ana",
                "ana@stratio.com", null, "stratio", "not-a-key", "jwtBroken")))
                .isNotNull();
        assertThat(ThreadProperty.get("jwtBroken")).isNull();
    }

    @Test
    public void decodingNeedsANonEmptyJwtVariable() {
        assertThat(catchThrowable(() -> miscSpec.decodeJwtPayload("neverSet", "payload")))
                .hasMessageContaining("is empty or not set");
    }

    @Test
    public void decodingRejectsSomethingThatIsNotAJwt() {
        ThreadProperty.set("notAJwt", "just-one-part");

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> miscSpec.decodeJwtPayload("notAJwt", "payload"))
                .withMessageContaining("Failed to decode JWT");
    }

    // -----------------------------------------------------------------------
    // Dates
    // -----------------------------------------------------------------------

    @Test
    public void theCurrentDateIsSavedInTheRequestedTimezone() throws Exception {
        miscSpec.getCurrentDateTime("Europe/Madrid", "madridNow");
        miscSpec.getCurrentDateTime(null, "utcNow");

        assertThat(ThreadProperty.get("madridNow")).matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");
        assertThat(ThreadProperty.get("utcNow")).matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");

        long madrid = parse(ThreadProperty.get("madridNow"));
        long utc = parse(ThreadProperty.get("utcNow"));
        assertThat(Math.abs(madrid - utc))
                .as("Madrid is one or two hours ahead of UTC, never the same")
                .isBetween(3_600_000L - 5_000L, 7_200_000L + 5_000L);
    }

    @Test
    public void minutesAndSecondsAreAddedToTheCurrentTime() throws Exception {
        miscSpec.getCurrentDateTime("UTC", "base");
        miscSpec.getOneMoreMinute(5, "UTC", "inFiveMinutes");
        miscSpec.getOneMoreSecond(30, null, "inThirtySeconds");

        assertThat(parse(ThreadProperty.get("inFiveMinutes")) - parse(ThreadProperty.get("base")))
                .isBetween(295_000L, 305_000L);
        assertThat(parse(ThreadProperty.get("inThirtySeconds")) - parse(ThreadProperty.get("base")))
                .isBetween(25_000L, 35_000L);
    }

    @Test
    public void aDateIsConvertedToATimestamp() throws Exception {
        miscSpec.convertDateToTimestamp("2021/01/01 00:00:00", "yyyy/MM/dd HH:mm:ss", "UTC", "ts");

        assertThat(ThreadProperty.get("ts")).isEqualTo("1609459200000");
    }

    // -----------------------------------------------------------------------
    // Variables and captured exceptions
    // -----------------------------------------------------------------------

    @Test
    public void aVariableIsReportedAsDefinedWhenItIsAThreadOrASystemProperty() throws Exception {
        ThreadProperty.set("aThreadVar", "value");
        System.setProperty("aSystemVar", "value");
        try {
            miscSpec.checkVariableDefined("aThreadVar", "is defined");
            miscSpec.checkVariableDefined("aSystemVar", "is defined");
            miscSpec.checkVariableDefined("neverDefined", "is not defined");

            assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> miscSpec.checkVariableDefined("aThreadVar", "is not defined"))
                    .withMessageContaining("Variable is defined");
            assertThatExceptionOfType(AssertionError.class)
                    .isThrownBy(() -> miscSpec.checkVariableDefined("neverDefined", "is defined"))
                    .withMessageContaining("Variable is not defined");
        } finally {
            System.clearProperty("aSystemVar");
        }
    }

    @Test
    public void anUnknownComparisonForAVariableIsRejected() {
        assertThat(catchThrowable(() -> miscSpec.checkVariableDefined("whatever", "might be defined")))
                .hasMessageContaining("Not a valid comparison");
    }

    @Test
    public void anExpectedExceptionIsMatchedByClassAndMessageAndThenConsumed() throws Exception {
        commong.getExceptions().add(new IllegalStateException("the thing broke"));

        miscSpec.assertExceptionNotThrown("IS", "IllegalStateException", "the thing broke");

        assertThat(commong.getExceptions())
                .as("a matched exception is cleared so the next step starts clean")
                .isEmpty();
    }

    @Test
    public void anExceptionCanBeMatchedByClassAlone() throws Exception {
        commong.getExceptions().add(new IllegalStateException("the thing broke"));

        miscSpec.assertExceptionNotThrown("IS", "IllegalStateException", null);

        assertThat(commong.getExceptions()).isEmpty();
    }

    @Test
    public void aMismatchedExceptionIsReported() {
        commong.getExceptions().add(new IllegalStateException("the thing broke"));

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> miscSpec.assertExceptionNotThrown("IS", "IllegalStateException", "something else"))
                .withMessageContaining("Unexpected last exception message");
    }

    @Test
    public void anEmptyExceptionListIsRequiredByTheNegativeForm() throws Exception {
        miscSpec.assertExceptionNotThrown("IS NOT", null, null);

        commong.getExceptions().add(new IllegalStateException("the thing broke"));
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> miscSpec.assertExceptionNotThrown("IS NOT", null, null))
                .withMessageContaining("Captured exception list is not empty");
    }

    @Test
    public void expectingAnExceptionThatNeverHappenedIsReported() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> miscSpec.assertExceptionNotThrown("IS", null, null))
                .withMessageContaining("Captured exception list is empty");
    }

    // -----------------------------------------------------------------------
    // JSONPath matcher
    // -----------------------------------------------------------------------

    @Test
    public void everyCaseOfTheDatatableIsEvaluatedAgainstTheStoredJson() throws Exception {
        ThreadProperty.set("json", "{\"columns\":[\"Test\"],\"name\":\"bdt\",\"empty\":null}");

        miscSpec.matchWithExpresion("json", DataTable.create(Arrays.asList(
                Arrays.asList("$.columns", "size", "1"),
                Arrays.asList("$.columns[0]", "equal", "Test"),
                Arrays.asList("$.name", "contains", "bd"),
                Arrays.asList("$.name", "not equal", "other"),
                Arrays.asList("$.missing", "PathNotFound", null),
                Arrays.asList("$.empty", "is null", null))));
    }

    @Test
    public void everyFailingCaseIsCollectedIntoASingleReport() {
        ThreadProperty.set("json", "{\"columns\":[\"Test\"],\"name\":\"bdt\"}");

        assertThat(catchThrowable(() -> miscSpec.matchWithExpresion("json", DataTable.create(Arrays.asList(
                Arrays.asList("$.columns", "size", "2"),
                Arrays.asList("$.name", "equal", "other"),
                Arrays.asList("$.columns", "PathNotFound", null))))))
                .as("all three failures must be reported at once, not just the first")
                .hasMessageContaining("$.columns")
                .hasMessageContaining("$.name")
                .hasMessageContaining("is expected to not be found");
    }

    // -----------------------------------------------------------------------
    // Excel
    // -----------------------------------------------------------------------

    @Test
    public void aValueIsFoundAnywhereInTheFirstSheet() throws Exception {
        excelFile = writeWorkbook("Data",
                Arrays.asList("id", "name", "city"),
                Arrays.asList("1", "Ana", "Madrid"),
                Arrays.asList("2", "John", "Bilbao"));

        miscSpec.verifyExcelCellValue(excelFile.getAbsolutePath(), "Bilbao", null, null, null, null, null);
    }

    @Test
    public void aValueIsFoundInANamedSheet() throws Exception {
        excelFile = writeWorkbook("People",
                Arrays.asList("id", "name"),
                Arrays.asList("1", "Ana"));

        miscSpec.verifyExcelCellValue(excelFile.getAbsolutePath(), "Ana", "People", null, null, null, null);
    }

    @Test
    public void anUnknownSheetIsReported() throws Exception {
        excelFile = writeWorkbook("People", Arrays.asList("id"), Arrays.asList("1"));

        assertThat(catchThrowable(() -> miscSpec.verifyExcelCellValue(
                excelFile.getAbsolutePath(), "1", "Missing", null, null, null, null)))
                .hasMessageContaining("Sheet with name 'Missing' not found");
    }

    @Test
    public void aValueIsLookedUpByColumnNumberAndByColumnName() throws Exception {
        excelFile = writeWorkbook("Data",
                Arrays.asList("id", "name", "city"),
                Arrays.asList("1", "Ana", "Madrid"),
                Arrays.asList("2", "Madrid", "Bilbao"));

        // Column 3 is 'city', so only the Bilbao row matches there.
        miscSpec.verifyExcelCellValue(excelFile.getAbsolutePath(), "Bilbao", null, "3", null, null, null);
        miscSpec.verifyExcelCellValue(excelFile.getAbsolutePath(), "Madrid", null, null, "city", null, null);

        assertThat(catchThrowable(() -> miscSpec.verifyExcelCellValue(
                excelFile.getAbsolutePath(), "Bilbao", null, "2", null, null, null)))
                .as("Bilbao is in column 3, not 2")
                .hasMessageContaining("NOT found");
    }

    @Test
    public void aValueIsLookedUpByRowNumberAndByRowName() throws Exception {
        excelFile = writeWorkbook("Data",
                Arrays.asList("id", "name", "city"),
                Arrays.asList("Ana", "engineer", "Madrid"),
                Arrays.asList("John", "designer", "Bilbao"));

        miscSpec.verifyExcelCellValue(excelFile.getAbsolutePath(), "Madrid", null, null, null, "2", null);
        miscSpec.verifyExcelCellValue(excelFile.getAbsolutePath(), "Bilbao", null, null, null, null, "John");

        assertThat(catchThrowable(() -> miscSpec.verifyExcelCellValue(
                excelFile.getAbsolutePath(), "Madrid", null, null, null, null, "John")))
                .as("Madrid belongs to Ana's row")
                .hasMessageContaining("NOT found");
    }

    @Test
    public void aValueThatIsNowhereInTheSheetIsReported() throws Exception {
        excelFile = writeWorkbook("Data", Arrays.asList("id"), Arrays.asList("1"));

        assertThat(catchThrowable(() -> miscSpec.verifyExcelCellValue(
                excelFile.getAbsolutePath(), "nowhere", null, null, null, null, null)))
                .hasMessageContaining("String nowhere NOT found");
    }

    @Test
    public void aMissingExcelFileIsReported() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> miscSpec.verifyExcelCellValue(
                        "/nonexistent/path/book.xlsx", "anything", null, null, null, null, null))
                .withMessageContaining("Could not read the Excel file");
    }

    @Test
    public void aFileThatIsNotAWorkbookIsReported() throws Exception {
        excelFile = File.createTempFile("bdt-not-excel-", ".xlsx");
        Files.write(excelFile.toPath(), "this is not a workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // What matters is that a file that is not a workbook fails loudly and names the file, not
        // how POI happens to word it. Which exception POI raises depends on the commons-compress it
        // runs against: it used to be NotOfficeXmlFileException ("not a valid OOXML"), a
        // RuntimeException that propagated raw, and from commons-compress 1.26 it arrives as an
        // IOException ("Cannot find zip signature within the file") that verifyExcelCellValue
        // catches and wraps. Both fail the step; the wrapped one also reports the path. Asserting on
        // POI's wording pinned the test to a third-party message instead of to this behaviour.
        assertThat(catchThrowable(() -> miscSpec.verifyExcelCellValue(
                excelFile.getAbsolutePath(), "anything", null, null, null, null, null)))
                .isNotNull()
                .hasMessageContaining(excelFile.getAbsolutePath());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private long parse(String formatted) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = format.parse(formatted);
        return date.getTime();
    }

    @SafeVarargs
    private final File writeWorkbook(String sheetName, List<String>... rows) throws Exception {
        File file = File.createTempFile("bdt-excel-", ".xlsx");
        file.deleteOnExit();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows[r];
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c));
                }
            }
            workbook.write(out);
        }
        return file;
    }
}
