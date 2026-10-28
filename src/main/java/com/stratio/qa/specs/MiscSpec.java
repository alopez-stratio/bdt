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

import com.jayway.jsonpath.PathNotFoundException;
import com.stratio.qa.aspects.RunOnTagAspect;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.datatable.DataTable;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.lang.time.DateUtils;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.Assertions;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.hjson.JsonArray;
import org.hjson.JsonValue;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.testng.Assert;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.fail;
import static com.stratio.qa.assertions.Assertions.assertThat;

/**
 * Generic Miscellaneous Specs.
 *
 * @see <a href="MiscSpec-annotations.html">Miscellaneous Steps &amp; Matching Regex</a>
 */
public class MiscSpec extends BaseGSpec {

    public static final int DEFAULT_TIMEOUT = 1000;

    /** Condition of the catch-all row in {@link #setVariableToFirstValueWhoseConditionHolds}. */
    private static final String OTHERWISE_CONDITION = "otherwise";

    private Scenario scenario;

    @Before
    public void before(Scenario scenario) {
        this.scenario = scenario;
    }

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public MiscSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**
     * Save value for future use.
     * <p>
     * If element is a jsonpath expression (i.e. $.fragments[0].id), it will be
     * applied over the last httpResponse.
     * <p>
     * If element is a jsonpath expression preceded by some other string
     * (i.e. ["a","b",,"c"].$.[0]), it will be applied over this string.
     * This will help to save the result of a jsonpath expression evaluated over
     * previous stored variable.
     *
     * @param position position from a search result
     * @param element  key in the json response to be saved
     * @param envVar   thread environment variable where to store the value
     * @throws IllegalAccessException    exception
     * @throws IllegalArgumentException  exception
     * @throws SecurityException         exception
     * @throws NoSuchFieldException      exception
     * @throws ClassNotFoundException    exception
     * @throws InstantiationException    exception
     * @throws InvocationTargetException exception
     * @throws NoSuchMethodException     exception
     */
    @Given("^I save element (in position '(.+?)' in )?'(.+?)'( in environment variable '(.+?)')?( in file '(.+?)')?$")
    public void saveElementEnvironmentFile(String position, String element, String envVar, String file) throws Exception {
        Pattern pattern = Pattern.compile("^((.*)(\\.)+)(\\$.*)$");
        Matcher matcher = pattern.matcher(element);
        String json;
        String parsedElement;

        if (matcher.find()) {
            json = matcher.group(2);
            parsedElement = matcher.group(4);
        } else {
            json = commonspec.getResponse().getResponse();
            parsedElement = element;
        }

        String value = commonspec.getJSONPathString(json, parsedElement, position);

        if (envVar != null) {
            ThreadProperty.set(envVar, value.replaceAll("\n", ""));
        }
        if (file != null) {
            writeInFile(value.replaceAll("\n", ""), file);
        }
    }

    @Deprecated
    public void saveElementEnvironment(String position, String element, String envVar) throws Exception {
        saveElementEnvironmentFile(position, element, envVar, null);
    }

    /**
     * Save value for future use.
     *
     * @param value  value to be saved
     * @param envVar thread environment variable where to store the value
     */
    @Given("^I save '(.+?)' in variable '(.+?)'$")
    public void saveInEnvironment(String value, String envVar) {
        ThreadProperty.set(envVar, value);
    }

    /**
     * Sets multiple ThreadProperty variables using a data table
     * Given I save the following variables:
     * | NEW_VAR | NEW_VALUE |
     * | OLD_VAR | NEW_VALUE |
     */
    @Given("^I save the following variables:$")
    public void saveMultipleVariables(DataTable dataTable) {
        for (List<String> row : dataTable.cells()) {
            String key = row.get(0);
            // Cucumber 7: empty Gherkin cells arrive as null; normalize to "" to preserve Cucumber 4 behaviour.
            String value = row.get(1) != null ? row.get(1) : "";

            ThreadProperty.set(key, value);

            logIntoScenario(String.format("%s -> %s", key, value));
        }
    }

    /**
     * Step to write a text to a variable, using the doc string as the contents
     * Usage:
     * Given I save the following text in variable 'test':
     *     """
     *     line 1
     *     line 2
     *
     *     line 3
     *     """
     */
    @When("^I save the following text in variable '(.+?)':$")
    public void createFileFromText(String envVar, String textContents) {
        saveInEnvironment(textContents, envVar);
    }

    /**
     * Sets a variable to the value of the first variable if defined, otherwise sets it to a default value.
     * Usage:
     * Given I set variable 'KEY' to 'VAR' if defined, or 'VAR_DEFAULT' as default
     *
     * @param key        the key to set
     * @param value      the value to set if defined
     * @param defaultVal the default value to set if not defined
     */
    @Given("^I set variable '(.+?)' to the value of '(.+?)' if defined, or '(.+?)' as default$")
    public void setVariableIfDefinedElseDefault(String key, String value, String defaultVal) {
        String finalValue = Optional.ofNullable(ThreadProperty.get(value))
                .orElseGet(() -> Optional.ofNullable(System.getProperty(value))
                        .orElseGet(() -> Optional.ofNullable(ThreadProperty.get(defaultVal))
                                .orElse(System.getProperty(defaultVal, defaultVal))));
        ThreadProperty.set(key, finalValue);
        logIntoScenario(String.format("%s -> %s", key, finalValue));
    }

    /**
     * Sets a variable to one value or another depending on a condition, using the same expression syntax as the
     * \@runOnEnv tag: =, &gt;, &lt;, &gt;=, &lt;=, != and the conditional operators &amp;&amp; and ||.
     * Usage:
     * Given I set variable 'MODE' to 'new' if condition 'STRATIO_VIRTUALIZER_VERSION&gt;=0.11.0', or to 'old' otherwise
     * Given I set variable 'MODE' to 'new' if condition 'VERSION&gt;=0.11.0&amp;&amp;VERSION&lt;1.0.0', or to 'old' otherwise
     * <p>
     * A condition with no operator is the "is present" check of the tag: it holds when the property is defined,
     * either as a system or as a thread property. There is no negation operator, so "is not present" is written by
     * swapping the branches:
     * Given I set variable 'MODE' to 'custom' if condition 'MY_VAR', or to 'default' otherwise
     * Given I set variable 'MODE' to 'default' if condition 'MY_VAR', or to 'custom' otherwise
     * <p>
     * Note that '!=' is not an "is present" check either: it requires the property to be defined, so it holds only
     * when the property exists and its value is a different one. And there is no null literal: 'MY_VAR!=null'
     * compares against the literal string "null".
     * <p>
     * The left side of a comparison is the <b>name</b> of a system or thread property, not its value, exactly as in
     * the tag: write 'VERSION&gt;=0.11.0', never '!{VERSION}&gt;=0.11.0'. The values of both branches are ordinary
     * step arguments, so '!{VAR}' and '${VAR}' are replaced in them as usual.
     * <p>
     * When the condition does not hold and no otherwise value is given, the variable is set to an empty string
     * rather than left unset, so that a later '!{VAR}' still resolves.
     *
     * @param envVar    thread environment variable where to store the value
     * @param value     value to set when the condition holds
     * @param condition runOnEnv-style expression to evaluate
     * @param elseValue value to set when the condition does not hold, empty string when not given
     * @throws Exception if the expression cannot be parsed
     */
    @Given("^I set variable '(.+?)' to '(.+?)' if condition '(.+?)'(?:, or to '(.+?)' otherwise)?$")
    public void setVariableIfCondition(String envVar, String value, String condition, String elseValue) throws Exception {
        RunOnTagAspect runOnTagAspect = new RunOnTagAspect();
        boolean holds = runOnTagAspect.checkParams(runOnTagAspect.getParams("@runOnEnv(" + condition + ")"));
        String finalValue = holds ? value : (elseValue != null ? elseValue : "");
        ThreadProperty.set(envVar, finalValue);
        logIntoScenario(String.format("%s -> %s (condition '%s' evaluated to %s)", envVar, finalValue, condition, holds));
    }

    /**
     * Sets a variable to the value of the first row whose condition holds, like a switch. Each row holds a condition,
     * with the same syntax as the \@runOnEnv tag, and the value to set when it holds. Rows are evaluated top to
     * bottom, and an optional last row with 'otherwise' as its condition is taken when no other row holds:
     * Given I set variable 'MODE' to the first value whose condition holds:
     *   | VERSION&gt;=0.3.0 | C |
     *   | VERSION&gt;=0.1.0 | B |
     *   | otherwise      | A |
     * <p>
     * Since the first row that holds wins, ranges can be written with their lower bound alone when the rows go from
     * the highest one down, as above. The explicit form works as well:
     *   | VERSION&lt;0.1.0                    | A |
     *   | VERSION&gt;=0.1.0&amp;&amp;VERSION&lt;0.3.0 | B |
     *   | VERSION&gt;=0.3.0                   | C |
     * <p>
     * '|' separates the cells of a Gherkin table, so the '||' operator must be escaped as '\|\|'. Repeating the
     * value in two consecutive rows reads better and does the same:
     *   | VERSION&lt;0.1.0\|\|VERSION&gt;=0.5.0 | X |
     * is equivalent to
     *   | VERSION&lt;0.1.0  | X |
     *   | VERSION&gt;=0.5.0 | X |
     * <p>
     * A condition on a property that is not defined does not hold, so it falls through to the next row and, in the
     * end, to 'otherwise'. When no row holds and there is no 'otherwise' row, the step fails instead of setting an
     * empty value: with several branches that almost always means a range left uncovered. Every condition is parsed
     * before any is evaluated, so a malformed condition fails even when an earlier row holds. The conditions
     * evaluated, their results and the row taken are logged into the scenario.
     * <p>
     * Versions are compared component by component and both sides must have the same number of them: write
     * 'VERSION&gt;=0.3.0' when VERSION holds '0.11.0', since 'VERSION&gt;=0.3' fails. That depends on the value of
     * the property, so it is only detected when the row is evaluated. Suffixes such as '-SNAPSHOT', '-M&lt;n&gt;' or
     * '-RC&lt;n&gt;' are ignored, as in the tag.
     * <p>
     * As in the single-condition step, the left side of a comparison is the <b>name</b> of a system or thread
     * property, not its value: write 'VERSION&gt;=0.3.0', never '!{VERSION}&gt;=0.3.0'. The values are replaced as
     * usual, so '!{VAR}' and '${VAR}' can be used in them.
     *
     * @param envVar thread environment variable where to store the value
     * @param table  rows of condition and value, evaluated in order
     * @throws Exception if a row is malformed, a condition cannot be parsed, or no row holds and there is no
     *                   'otherwise' row
     */
    @Given("^I set variable '(.+?)' to the first value whose condition holds:$")
    public void setVariableToFirstValueWhoseConditionHolds(String envVar, DataTable table) throws Exception {
        List<List<String>> rows = table.cells();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The table must have at least one row of condition and value");
        }

        // Parse every condition before evaluating any, so that a typo in a branch not taken today fails now
        // rather than on the day the property reaches that range. A null entry stands for the 'otherwise' row.
        RunOnTagAspect runOnTagAspect = new RunOnTagAspect();
        List<String[][]> parsedConditions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int rowNumber = i + 1;
            if (row.size() != 2) {
                throw new IllegalArgumentException(String.format("Row %d must have two cells, condition and value, "
                        + "but has %d: %s. If its condition uses the '||' operator, escape it as '\\|\\|'",
                        rowNumber, row.size(), row));
            }
            String condition = row.get(0);
            if (condition == null) {
                throw new IllegalArgumentException(String.format("Row %d has an empty condition", rowNumber));
            }
            if (OTHERWISE_CONDITION.equals(condition)) {
                if (rowNumber != rows.size()) {
                    throw new IllegalArgumentException(String.format(
                            "'%s' must be the last row, but it is row %d of %d", OTHERWISE_CONDITION, rowNumber, rows.size()));
                }
                parsedConditions.add(null);
                continue;
            }
            if (condition.replace("||", "").contains("|")) {
                throw new IllegalArgumentException(String.format("Row %d has a single '|' in its condition '%s'. "
                        + "The 'or' operator is '||', escaped as '\\|\\|' inside a table", rowNumber, condition));
            }
            parsedConditions.add(runOnTagAspect.getParams("@runOnEnv(" + condition + ")"));
        }

        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String condition = rows.get(i).get(0);
            String[][] params = parsedConditions.get(i);
            boolean holds = params == null || runOnTagAspect.checkParams(params);
            trace.append(String.format("%n  %s -> %s", condition, holds));
            if (holds) {
                // Cucumber 7: empty Gherkin cells arrive as null; an empty value is a legitimate choice.
                String value = rows.get(i).get(1) != null ? rows.get(i).get(1) : "";
                ThreadProperty.set(envVar, value);
                logIntoScenario(String.format("%s -> %s (row %d)%s", envVar, value, i + 1, trace));
                return;
            }
        }
        throw new IllegalStateException(String.format("No condition holds and there is no '%s' row, so '%s' was not set%s",
                OTHERWISE_CONDITION, envVar, trace));
    }

    /**
     * Prints the value of a specified variable (Thread or System) to console and into the scenario.
     * Ex.: Given I print the variable 'KEOS_VERSION' value
     *
     * @param key the name of the variable whose value is to be printed
     */
    @Given("^I print the variable '(.+?)' value$")
    public void printVarValue(String key) {
        logIntoScenario(String.format("%s -> %s", key,
                System.getProperty(key, Objects.requireNonNullElse(ThreadProperty.get(key), "NOT_SET"))));
    }

    /**
     * Prints the contents of a specified file to console and into the scenario.
     * Ex.: Given I print the file 'keosReport.txt' contents
     *
     * @param file the name of the file whose contents are to be printed
     */
    @Given("^I print the file '(.+?)' contents$")
    public void printFileContents(String file) {
        logIntoScenario(String.format("%s -> \n%s", file, commonspec.retrieveData(file, "txt")));
    }

    /**
     * Prints the service response to console and into the scenario.
     */
    @Given("^I print the service's response$")
    public void printServiceResponse() {
        logIntoScenario(String.format("Response HTTP Status -> %s", commonspec.getResponse().getStatusCode()));
        logIntoScenario(String.format("Response Contents -> \n%s", commonspec.getResponse().getResponse()));
    }

    /**
     * Wait seconds.
     *
     * @param seconds
     * @throws InterruptedException
     */
    @When("^I wait '(\\d+)' seconds?$")
    public void idleWait(Integer seconds) throws InterruptedException {
        Thread.sleep(seconds * DEFAULT_TIMEOUT);
    }

    /**
     * Sort elements in envVar by a criteria and order.
     *
     * @param envVar   Environment variable to be sorted
     * @param criteria alphabetical,...
     * @param order    ascending or descending
     */
    @When("^I sort elements in '(.+?)' by '(.+?)' criteria in '(.+?)' order$")
    public void sortElements(String envVar, String criteria, String order) {

        String value = ThreadProperty.get(envVar);
        JsonArray jsonArr = JsonValue.readHjson(value).asArray();

        List<JsonValue> jsonValues = new ArrayList<JsonValue>();
        for (int i = 0; i < jsonArr.size(); i++) {
            jsonValues.add(jsonArr.get(i));
        }

        Comparator<JsonValue> comparator;
        switch (criteria) {
            case "alphabetical":
                commonspec.getLogger().debug("Alphabetical criteria selected.");
                comparator = new Comparator<JsonValue>() {
                    public int compare(JsonValue json1, JsonValue json2) {
                        int res = String.CASE_INSENSITIVE_ORDER.compare(json1.toString(), json2.toString());
                        if (res == 0) {
                            res = json1.toString().compareTo(json2.toString());
                        }
                        return res;
                    }
                };
                break;
            default:
                commonspec.getLogger().debug("No criteria selected.");
                comparator = null;
        }

        if ("ascending".equals(order)) {
            Collections.sort(jsonValues, comparator);
        } else {
            Collections.sort(jsonValues, comparator.reversed());
        }

        ThreadProperty.set(envVar, jsonValues.toString());
    }

    /**
     * Checks if an exception has been thrown.
     *
     * @param exception    : "IS NOT" | "IS"
     * @param clazz
     * @param exceptionMsg
     */
    @Then("^an exception '(.+?)' thrown( with class '(.+?)')?( and message like '(.+?)')?")
    public void assertExceptionNotThrown(String exception, String clazz, String exceptionMsg)
            throws ClassNotFoundException {
        List<Exception> exceptions = commonspec.getExceptions();
        if ("IS NOT".equals(exception)) {
            assertThat(exceptions).as("Captured exception list is not empty").isEmpty();
        } else {
            assertThat(exceptions).as("Captured exception list is empty").isNotEmpty();
            Exception ex = exceptions.get(exceptions.size() - 1);
            if (clazz != null && exceptionMsg != null) {
                assertThat(ex.toString()).as("Unexpected last exception class").contains(clazz);
                assertThat(ex.toString()).as("Unexpected last exception message").contains(exceptionMsg);
            } else if (clazz != null) {
                assertThat(exceptions.get(exceptions.size() - 1).getClass().getSimpleName()).as("Unexpected last exception class").isEqualTo(clazz);
            }

            commonspec.getExceptions().clear();
        }
    }

    /**
     * Check if expression defined by JSONPath match in JSON stored in an environment variable.
     * The allowed operations are: [equal, not equal, contains, does not contain, not empty, matches, size, higher than, lower than, is null, PathNotFound]
     * Usage:
     * Then 'var_name' matches the following cases:
     * | $.columns    | size         | 1    |
     * | $.columns[0] | equal        | Test |
     * | $.columns[1] | PathNotFound |      |
     *
     * @param envVar environment variable where JSON is stored
     * @param table  data table in which each row stores one expression
     */
    @Then("^'(.+?)' matches the following cases:$")
    public void matchWithExpresion(String envVar, DataTable table) throws Exception {
        String jsonString = ThreadProperty.get(envVar);
        ArrayList<String> errorList = new ArrayList<>();

        for (List<String> row : table.cells()) {
            String json_path = row.get(0);
            String condition = row.get(1);
            // Cucumber 7 Gherkin empty cells (| equal | |) arrive as null; normalize to ""
            // to preserve Cucumber 4 behaviour where empty cells were empty strings.
            String expected_value = row.get(2) != null ? row.get(2) : "";

            // PathNotFound
            if (condition.equals("PathNotFound")) {
                if (commonspec.hasJSONPath(jsonString, json_path)) {
                    errorList.add(String.format("%s - [JSONPath is expected to not be found but it exists] Value found: <%s>",
                            json_path, commonspec.getJSONPathString(jsonString, json_path, null)));
                }

            // is null
            } else if (condition.equals("is null")) {
                try {
                    String value = commonspec.getJSONPathString(jsonString, json_path, null);
                    if (value != null) {
                        errorList.add(String.format("%s - [JSONPath is expected to be null but is not] Value found: <%s>",
                                json_path, value));
                    }
                } catch (PathNotFoundException | AssertionError e) {
                    errorList.add(String.format("%s - %s", json_path, e.getMessage()));
                } catch (NullPointerException e) {
                    // pass (value was null as expected)
                }

            // equal, not equal, contains, does not contain, not empty, matches, size, higher than, lower than
            } else {
                try {
                    String value = commonspec.getJSONPathString(jsonString, json_path, null);
                    commonspec.evaluateJSONElementOperation(value, condition, expected_value);
                } catch (PathNotFoundException | AssertionError e) {
                    errorList.add(String.format("%s - %s", json_path, e.getMessage()));
                } catch (NullPointerException ex) {
                    errorList.add(String.format("%s - %s", json_path, "The JSONPath extracted value was null"));
                }
            }
        }
        Assert.assertEquals(errorList.size(), 0, "\n" + String.join("\n", errorList) + "\n");
    }

    /**
     * Check if expression defined by JSONPath match in the service's JSON response.
     * The allowed operations are: [equal, not equal, contains, does not contain, not empty, matches, size, higher than, lower than, is null, PathNotFound]
     * Usage:
     * Then the JSON response matches the following cases:
     * | $.columns    | size         | 1    |
     * | $.columns[0] | equal        | Test |
     * | $.columns[1] | PathNotFound |      |
     *
     * @param table data table in which each row stores one expression
     */
    @Then("^the JSON response matches the following cases:$")
    public void matchWithExpressionResponse(DataTable table) throws Exception {
        try {
            saveElementEnvironmentFile(null, "$", "_tmp_response_", null);
        } catch (org.hjson.ParseException e) {
            throw new Exception("Error parsing the service's response! Not a valid JSON:\n" + commonspec.getResponse().getResponse());
        }
        try {
            matchWithExpresion("_tmp_response_", table);
        } finally {
            ThreadProperty.remove("_tmp_response_");
        }
    }

    /**
     * Checks the value stored in an environment variable against a provided value using the specified operation.
     * Supported operations:
     * - is: checks if the values are exactly equal.
     * - matches: checks if the value matches the given regular expression pattern.
     * - does not match: checks if the value does not match the given regular expression pattern.
     * - is higher than: checks if the environment variable (as a number) is greater than the provided value.
     * - is higher than or equal to: checks if the environment variable (as a number) is greater than or equal to the provided value.
     * - is lower than: checks if the environment variable (as a number) is less than the provided value.
     * - is lower than or equal to: checks if the environment variable (as a number) is less than or equal to the provided value.
     * - contains: checks if the environment variable contains the provided value as a substring.
     * - does not contain: checks if the environment variable does not contain the provided value as a substring.
     * - is different from: checks if the values are not equal.
     *
     * @param envVar    the environment variable value to check
     * @param operation the operation to perform (see above)
     * @param value     the value to compare against
     */
    @Then("^'(.+)' (is|matches|does not match|is higher than|is higher than or equal to|is lower than|is lower than or equal to|contains|does not contain|is different from) '(.+)'$")
    public void checkValue(String envVar, String operation, String value) throws Exception {
        switch (operation.toLowerCase()) {
            case "is":
                Assertions.assertThat(envVar).as("Values are not equal.").isEqualTo(value);
                break;
            case "matches":
                Assertions.assertThat(envVar).as("The value does not matches the pattern.").matches(value);
                break;
            case "does not match":
                Assertions.assertThat(envVar).as("The value matches the pattern, but it should not.").doesNotMatch(value);
                break;
            case "is higher than":
                if (envVar.matches("^-?\\d+$") && value.matches("^-?\\d+$")) {
                    Assertions.assertThat(Integer.parseInt(envVar)).as("First value is not higher than second one.").isGreaterThan(Integer.parseInt(value));
                } else {
                    throw new Exception("A number should be provided in order to perform a valid comparison.");
                }
                break;
            case "is higher than or equal to":
                if (envVar.matches("^-?\\d+$") && value.matches("^-?\\d+$")) {
                    Assertions.assertThat(Integer.parseInt(envVar)).as("First value is not higher than or equal to second one.").isGreaterThanOrEqualTo(Integer.parseInt(value));
                } else {
                    throw new Exception("A number should be provided in order to perform a valid comparison.");

                }
                break;
            case "is lower than":
                if (envVar.matches("^-?\\d+$") && value.matches("^-?\\d+$")) {
                    Assertions.assertThat(Integer.parseInt(envVar)).as("First value is not lower than second one.").isLessThan(Integer.parseInt(value));
                } else {
                    throw new Exception("A number should be provided in order to perform a valid comparison.");
                }
                break;
            case "is lower than or equal to":
                if (envVar.matches("^-?\\d+$") && value.matches("^-?\\d+$")) {
                    Assertions.assertThat(Integer.parseInt(envVar)).as("First value is not lower than or equal to second one.").isLessThanOrEqualTo(Integer.parseInt(value));
                } else {
                    throw new Exception("A number should be provided in order to perform a valid comparison.");
                }
                break;
            case "contains":
                Assertions.assertThat(envVar).as("Second value is not contained in first one.").contains(value);
                break;
            case "does not contain":
                Assertions.assertThat(envVar).as("Second value is contained in first one.").doesNotContain(value);
                break;
            case "is different from":
                Assertions.assertThat(envVar).as("Both values are equal.").isNotEqualTo(value);
                break;
            default:
                throw new Exception("Not a valid comparison. Valid ones are: is | matches | is higher than | is higher than or equal to | is lower than | is lower than or equal to | contains | does not contain | is different from");
        }
    }

    /**
     * Check value stored in environment variable is empty or is not empty
     *
     * @param envVar    value to check
     * @param operation [is empty, is not empty]
     */
    @Then("^'(.*)' (is empty|is not empty)$")
    public void checkValue(String envVar, String operation) throws Exception {
        switch (operation.toLowerCase()) {
            case "is empty":
                Assertions.assertThat(envVar).as("Value is not empty.").isEmpty();
                break;
            case "is not empty":
                Assertions.assertThat(envVar).as("Value is empty.").isNotEmpty();
                break;
            default:
                throw new Exception("Not a valid comparison. Valid ones are: ['is empty', 'is not empty']");
        }
    }

    /**
     * Check if a variable is defined or not defined (ThreadProperty or System property).
     *
     * @param varName   variable name to check
     * @param operation [is defined, is not defined]
     */
    @Then("^the variable '(.+?)' (is defined|is not defined)$")
    public void checkVariableDefined(String varName, String operation) throws Exception {
        boolean isDefined = ThreadProperty.get(varName) != null || System.getProperty(varName) != null;

        switch (operation.toLowerCase()) {
            case "is defined":
                Assertions.assertThat(isDefined).as("Variable is not defined.").isTrue();
                break;
            case "is not defined":
                Assertions.assertThat(isDefined).as("Variable is defined.").isFalse();
                break;
            default:
                throw new Exception("Not a valid comparison. Valid ones are: ['is defined', 'is not defined']");
        }
    }

    /**
     * Validates that the JSON stored in a variable matches against a JSON schema.
     *
     * @param envVar      the environment variable name that holds the JSON string
     * @param schema_path the path to the JSON schema file
     */
    @Then("^I check that the json in '(.+?)' matches the json schema '(.+?)'$")
    public void checkJsonSchemaStep(String envVar, String schema_path) {
        String jsonString = ThreadProperty.get(envVar);
        checkJsonSchema(jsonString, schema_path);
    }

    /**
     * Validates that the JSON response matches against a JSON schema.
     *
     * @param schema_path the path to the JSON schema file
     */
    @Then("^I check that the response matches the json schema '(.+?)'$")
    public void checkResponseJsonSchemaStep(String schema_path) {
        String jsonString = commonspec.getResponse().getResponse();
        checkJsonSchema(jsonString, schema_path);
    }

    /**
     * Validates that the JSON stored in a file matches against a JSON schema.
     *
     * @param json_path   the path to the JSON file
     * @param schema_path the path to the JSON schema file
     */
    @Then("^I check that the json from file '(.+?)' matches the json schema '(.+?)'$")
    public void checkJsonFileSchemaStep(String json_path, String schema_path) {
        String jsonString = commonspec.retrieveData(json_path, "json");
        checkJsonSchema(jsonString, schema_path);
    }

    /**
     * Validates that the JSON string matches against a JSON schema.
     * The JSON schema can be generated with any web tool like: https://transform.tools/json-to-json-schema
     *
     * @param json        the JSON string
     * @param schema_path the path to the JSON schema file
     */
    private void checkJsonSchema(String json, String schema_path) {
        String schemaString = commonspec.retrieveData(schema_path, "json");

        try {
            JSONObject jsonSchema = new JSONObject(new JSONTokener(schemaString));
            JSONObject jsonSubject = new JSONObject(new JSONTokener(json));

            Schema schema = SchemaLoader.load(jsonSchema);
            schema.validate(jsonSubject);

            commonspec.getLogger().info("JSON is valid against the schema.");

        } catch (ValidationException e) {
            Assert.fail("JSON is not valid against the schema. Validation errors:\n\n" +
                    String.join("\n", e.getAllMessages()) + "\n");
        }
    }

    @Given("^I set tenant variables$")
    public void setTenantVariables() throws Exception {
        String ccTenant = System.getProperty("CC_TENANT", "NONE");
        String dcosTenant = System.getProperty("DCOS_TENANT");
        if (dcosTenant == null) {
            throw new Exception("DCOS_TENANT is null");
        }
        String zkTenant = System.getProperty("ZK_TENANT") != null ? System.getProperty("ZK_TENANT") : System.getProperty("DCOS_TENANT");
        String xdTenant = System.getProperty("XD_TENANT") != null ? System.getProperty("XD_TENANT") : System.getProperty("DCOS_TENANT");
        String pgTenant = System.getProperty("PG_TENANT") != null ? System.getProperty("PG_TENANT") : System.getProperty("DCOS_TENANT");
        String elasticTenant = System.getProperty("ELASTIC_TENANT") != null ? System.getProperty("ELASTIC_TENANT") : System.getProperty("DCOS_TENANT");
        String kafkaTenant = System.getProperty("KAFKA_TENANT") != null ? System.getProperty("KAFKA_TENANT") : System.getProperty("DCOS_TENANT");
        String sparkTenant = System.getProperty("SPARK_TENANT") != null ? System.getProperty("SPARK_TENANT") : System.getProperty("DCOS_TENANT");
        String pgXLTenant = System.getProperty("PGD_TENANT") != null ? System.getProperty("PGD_TENANT") : System.getProperty("DCOS_TENANT");
        String schemaRegistryTenant = System.getProperty("SCHEMA_REGISTRY_TENANT") != null ? System.getProperty("SCHEMA_REGISTRY_TENANT") : System.getProperty("DCOS_TENANT");
        String restProxyTenant = System.getProperty("REST_PROXY_TENANT") != null ? System.getProperty("REST_PROXY_TENANT") : System.getProperty("DCOS_TENANT");
        String govTenant = System.getProperty("GOV_TENANT") != null ? System.getProperty("GOV_TENANT") : System.getProperty("DCOS_TENANT");
        String cassandraTenant = System.getProperty("CASSANDRA_TENANT") != null ? System.getProperty("CASSANDRA_TENANT") : System.getProperty("DCOS_TENANT");
        String igniteTenant = System.getProperty("IGNITE_TENANT") != null ? System.getProperty("IGNITE_TENANT") : System.getProperty("DCOS_TENANT");
        String etcdTenant = System.getProperty("ETCD_TENANT") != null ? System.getProperty("ETCD_TENANT") : System.getProperty("DCOS_TENANT");
        String k8sTenant = System.getProperty("K8S_TENANT") != null ? System.getProperty("K8S_TENANT") : System.getProperty("DCOS_TENANT");
        String arangoTenant = System.getProperty("ARANGO_TENANT") != null ? System.getProperty("ARANGO_TENANT") : System.getProperty("DCOS_TENANT");
        String kibanaTenant = System.getProperty("KIBANA_TENANT") != null ? System.getProperty("KIBANA_TENANT") : System.getProperty("DCOS_TENANT");
        String hdfsTenant = System.getProperty("HDFS_TENANT") != null ? System.getProperty("HDFS_TENANT") : System.getProperty("DCOS_TENANT");
        String spartaTenant = System.getProperty("SPARTA_TENANT") != null ? System.getProperty("SPARTA_TENANT") : System.getProperty("DCOS_TENANT");
        ThreadProperty.set("CC_TENANT", ccTenant);
        ThreadProperty.set("XD_TENANT", xdTenant);
        ThreadProperty.set("ZK_TENANT", zkTenant);
        ThreadProperty.set("PG_TENANT", pgTenant);
        ThreadProperty.set("ELASTIC_TENANT", elasticTenant);
        ThreadProperty.set("KAFKA_TENANT", kafkaTenant);
        ThreadProperty.set("SPARK_TENANT", sparkTenant);
        ThreadProperty.set("PGD_TENANT", pgXLTenant);
        ThreadProperty.set("SCHEMA_REGISTRY_TENANT", schemaRegistryTenant);
        ThreadProperty.set("REST_PROXY_TENANT", restProxyTenant);
        ThreadProperty.set("GOV_TENANT", govTenant);
        ThreadProperty.set("CASSANDRA_TENANT", cassandraTenant);
        ThreadProperty.set("IGNITE_TENANT", igniteTenant);
        ThreadProperty.set("ETCD_TENANT", etcdTenant);
        ThreadProperty.set("K8S_TENANT", k8sTenant);
        ThreadProperty.set("ARANGO_TENANT", arangoTenant);
        ThreadProperty.set("KIBANA_TENANT", kibanaTenant);
        ThreadProperty.set("HDFS_TENANT", hdfsTenant);
        ThreadProperty.set("SPARTA_TENANT", spartaTenant);
        commonspec.getLogger().debug("Tenant variables: XD --> {}, ZK --> {}, CC --> {}, PG --> {}, ELASTIC --> {}, KAFKA --> {}, SPARK --> {}, PGD --> {}, SCHEMA REG --> {}, REST PROXY --> {}," +
                        "GOVERNANCE --> {}, CASSANDRA --> {}, IGNITE --> {}, ETCD --> {}, K8S --> {}, ARANGO --> {}, KIBANA --> {}, HDFS --> {}, SPARTA --> {}",
                xdTenant, zkTenant, ccTenant, pgTenant, elasticTenant, kafkaTenant, sparkTenant, pgXLTenant, schemaRegistryTenant, restProxyTenant, govTenant, cassandraTenant, igniteTenant,
                etcdTenant, k8sTenant, arangoTenant, kibanaTenant, hdfsTenant, spartaTenant);
    }

    @Given("^I set gosec API variables$")
    public void setGosecVariables() throws Exception {
        String gosecVersion = ThreadProperty.get("gosec-management_version");
        if (gosecVersion == null) {
            commonspec.getLogger().warn("gosec-management_version has not been defined");
        } else {
            String[] gosecVersionArray = gosecVersion.split("\\.");
            if (gosecVersionArray.length != 3) {
                commonspec.getLogger().warn("gosec-management_version must have X.X.X format");
            } else {
                if (Integer.parseInt(gosecVersionArray[0]) >= 1 && (Integer.parseInt(gosecVersionArray[1]) > 1 || (Integer.parseInt(gosecVersionArray[1]) == 1 && Integer.parseInt(gosecVersionArray[2]) >= 1))) { //Gosec version >= 1.1.1
                    ThreadProperty.set("API_USER", "/api/user?id=");
                    ThreadProperty.set("API_GROUP", "/api/group?id=");
                    ThreadProperty.set("API_POLICY", "/api/policy?id=");
                    ThreadProperty.set("API_TAG", "/api/policy/tag?id=");
                    ThreadProperty.set("API_USERS", "/api/users");
                    ThreadProperty.set("API_GROUPS", "/api/groups");
                    ThreadProperty.set("API_POLICIES", "/api/policies");
                    ThreadProperty.set("API_TAGS", "/api/policies/tags");
                    ThreadProperty.set("API_SERVICE", "/api/service");
                } else {
                    ThreadProperty.set("API_USER", "/api/user/");
                    ThreadProperty.set("API_GROUP", "/api/group/");
                    ThreadProperty.set("API_POLICY", "/api/policy/");
                    ThreadProperty.set("API_TAG", "/api/policy/tag/");
                    ThreadProperty.set("API_USERS", "/api/user");
                    ThreadProperty.set("API_GROUPS", "/api/group");
                    ThreadProperty.set("API_POLICIES", "/api/policy");
                    ThreadProperty.set("API_TAGS", "/api/policy/tag");
                    ThreadProperty.set("API_SERVICE", "/api/service");
                }
            }
        }
    }

    /**
     * Convert provided date into timestamp
     *
     * @param date     : provided date (2020-08-08, 2020-08-08 10:10:10, etc)
     * @param format   : provided format (yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, etc) - Format should match with provided date/time
     * @param timezone : Timezone GMT,GMT+1,GMT-1... (OPTIONAL) if not provided, UTC (GMT)
     * @param envVar   : environment variable where timestamp is saved
     * @throws Exception
     */
    @When("^I convert date '(.+?)' in format '(.+?)'( with timezone '(.+?)')? into timestamp and save the value in environment variable '(.+?)'$")
    public void convertDateToTimestamp(String date, String format, String timezone, String envVar) throws Exception {

        SimpleDateFormat isoFormat = new SimpleDateFormat(format);
        String tz = (timezone != null) ? timezone : "UTC";
        isoFormat.setTimeZone(TimeZone.getTimeZone(tz));
        try {
            Date d = isoFormat.parse(date);
            GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone(tz));
            calendar.setTime(d);
            ThreadProperty.set(envVar, Long.toString(calendar.getTimeInMillis()));
            commonspec.getLogger().debug("Your date in timestamp: {} with timezone: {}", calendar.getTimeInMillis(), tz);
        } catch (AssertionError e) {
            commonspec.getLogger().error("Error converting date {}, check date and format", date);
            throw e;
        }
    }

       /**
     * Get current date and time and save it into an environment variable, using the specified timezone.
     * If no timezone is provided, UTC will be used by default.
     *
     * @param envVar   environment variable where the date/time is saved
     * @param timezone (optional) timezone ID (e.g., "UTC", "Europe/Madrid"); if null or empty, uses UTC
     */
    @Given("^I get current date and time( with timezone '(.+?)')? and save the value in environment variable '(.+?)'$")
    public void getCurrentDateTime(String timezone, String envVar) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String tz = (timezone != null && !timezone.isEmpty()) ? timezone : "UTC";
        dateFormat.setTimeZone(TimeZone.getTimeZone(tz));
        Date date = new Date(); // now
        ThreadProperty.set(envVar, dateFormat.format(date));
        commonspec.getLogger().debug("Current Date: {} with timezone: {}", dateFormat.format(date), tz);
    }

    /**
     * Get current date and time plus the provided number of minutes and save it into an environment variable,
     * using the specified timezone. If no timezone is provided, UTC will be used by default.
     *
     * @param addMinuteTime number of minutes to add to the current date/time
     * @param timezone      (optional) timezone ID (e.g., "UTC", "Europe/Madrid"); if null or empty, uses UTC
     * @param envVar        environment variable where the date/time is saved
     */
    @Given("^I get date and time with '(.+?)' more minutes from now( with timezone '(.+?)')? and save the value in environment variable '(.+?)'$")
    public void getOneMoreMinute(int addMinuteTime, String timezone, String envVar) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String tz = (timezone != null && !timezone.isEmpty()) ? timezone : "UTC";
        dateFormat.setTimeZone(TimeZone.getTimeZone(tz));
        Date targetTime = DateUtils.addMinutes(new Date(), addMinuteTime);
        ThreadProperty.set(envVar, dateFormat.format(targetTime));
        commonspec.getLogger().debug("Date adding {} minutes : {} with timezone: {}", addMinuteTime, dateFormat.format(targetTime), tz);
    }

    /**
     * Get current date and time plus the provided number of seconds and save it into an environment variable,
     * using the specified timezone. If no timezone is provided, UTC will be used by default.
     *
     * @param addSecondsTime number of seconds to add to the current date/time
     * @param timezone       (optional) timezone ID (e.g., "UTC", "Europe/Madrid"); if null or empty, uses UTC
     * @param envVar         environment variable where the date/time is saved
     */
    @Given("^I get date and time with '(.+?)' more seconds from now( with timezone '(.+?)')? and save the value in environment variable '(.+?)'$")
    public void getOneMoreSecond(int addSecondsTime, String timezone, String envVar) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String tz = (timezone != null && !timezone.isEmpty()) ? timezone : "UTC";
        dateFormat.setTimeZone(TimeZone.getTimeZone(tz));
        Date targetTime = DateUtils.addSeconds(new Date(), addSecondsTime);
        ThreadProperty.set(envVar, dateFormat.format(targetTime));
        commonspec.getLogger().debug("Date adding {} seconds : {} with timezone: {}", addSecondsTime, dateFormat.format(targetTime), tz);
    }


    /**
     * Get JWT for the provided data
     *
     * @param tenant    : environment tenant
     * @param userId    : login userId
     * @param mail      : users email
     * @param groups    : List of groups
     * @param issuer    : Issuer to sign the token
     * @param secretKey : Vault secretKey to sign the token
     * @param envVar    : local variable where JWT will be saved
     */
    @Given("^I get JWT for tenant:'(.+?)',userId:'(.+?)',mail:'(.+?)'(,groups:'(.+?)')? with issuer:'(.+?)' and secretKey:'(.+?)' and save the value in environment variable '(.+?)'$")
    public void getJwtFromData(String tenant, String userId, String mail, String groups, String issuer, String secretKey, String envVar) {
        List<String> myList = groups == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(groups.split(",")));

        //Set JWT start and end dates
        Date startDate = new GregorianCalendar(2021, Calendar.JANUARY, 01).getTime();
        Date endDate = new GregorianCalendar(2099, Calendar.JANUARY, 01).getTime();

        //The JWT signature algorithm we will be using to sign the token
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        //secretKey should be given as Base64URL encoded
        Key signingKey = new SecretKeySpec(secretKey.getBytes(), signatureAlgorithm.getJcaName());

        try {
            JwtBuilder builder = Jwts.builder().setNotBefore(startDate)
                    .claim("cn", userId)
                    .claim("tenant", tenant)
                    .claim("groups", myList)
                    .claim("mail", mail)
                    .setExpiration(endDate)
                    .setIssuer(issuer)
                    .claim("uid", userId)
                    .signWith(signatureAlgorithm, signingKey);

            //Save token on env variable
            ThreadProperty.set(envVar, builder.compact());
            commonspec.getLogger().debug("Your JWT: {}", builder.compact());
        } catch (AssertionError e) {
            commonspec.getLogger().error("Error creating JWT, check data");
            throw e;
        }
    }

    /**
     * Get JWT for the provided data using privateKey
     *
     * @param tenant  : environment tenant
     * @param userId  : login userId
     * @param mail    : users email
     * @param groups  : List of groups
     * @param issuer  : Issuer to sign the token
     * @param privKey : Vault privateKey to sign the token
     * @param envVar  : local variable where JWT will be saved
     */
    @Given("^I get JWT for tenant:'(.+?)',userId:'(.+?)',mail:'(.+?)'(,groups:'(.+?)')? with issuer:'(.+?)' and privateKey:'(.+?)' and save the value in environment variable '(.+?)'$")
    public void getJwtUsingPrivateKey(String tenant, String userId, String mail, String groups, String issuer, String privKey, String envVar) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        List<String> myList = groups == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(groups.split(",")));

        //Set JWT start and end dates
        Date startDate = new GregorianCalendar(2021, Calendar.JANUARY, 01).getTime();
        Date endDate = new GregorianCalendar(2099, Calendar.JANUARY, 01).getTime();

        //The JWT signature algorithm we will be using to sign the token
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;

        //change text newline to new lines in provided String
        privKey = privKey.replaceAll("newline", "\n");

        // Read in the key into a String
        StringBuilder pkcs8Lines = new StringBuilder();
        BufferedReader rdr = new BufferedReader(new StringReader(privKey));
        String line;
        while ((line = rdr.readLine()) != null) {
            pkcs8Lines.append(line);
        }

        // Remove the "BEGIN" and "END" lines, as well as any whitespace
        String pkcs8Pem = pkcs8Lines.toString();
        pkcs8Pem = pkcs8Pem.replace("-----BEGIN PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replace("-----END PRIVATE KEY-----", "");
        pkcs8Pem = pkcs8Pem.replaceAll("\\s+", "");

        // Base64 decode the result
        byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(pkcs8Pem);

        // extract the private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(keySpec);

        try {
            JwtBuilder builder = Jwts.builder().setNotBefore(startDate)
                    .claim("cn", userId)
                    .claim("tenant", tenant)
                    .claim("groups", myList)
                    .claim("mail", mail)
                    .setExpiration(endDate)
                    .setIssuer(issuer)
                    .claim("uid", userId)
                    .signWith(signatureAlgorithm, privateKey);

            //Save token on env variable
            ThreadProperty.set(envVar, builder.compact());
            commonspec.getLogger().debug("Your JWT: {}", builder.compact());
        } catch (AssertionError e) {
            commonspec.getLogger().error("Error creating JWT, check data");
            throw e;
        }
    }

    /**
     * Decodes the JWT payload and saves it as a JSON object in a variable.
     * @param jwtVariableName The variable name containing the JWT token (e.g., 'genai-litellm-token')
     * @param outputVariableName The variable name where the decoded payload will be saved
     */
    @Given("^I decode JWT from variable '(.+?)' and save the payload in variable '(.+?)'$")
    public void decodeJwtPayload(String jwtVariableName, String outputVariableName) {
        String jwt = ThreadProperty.get(jwtVariableName);
        Assert.assertFalse(jwt == null || jwt.isEmpty(),
                "JWT variable '" + jwtVariableName + "' is empty or not set");

        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);

            ThreadProperty.set(outputVariableName, payloadJson);
        } catch (Exception e) {
            throw new AssertionError("Failed to decode JWT: " + e.getMessage(), e);
        }
    }

    /**
     * Saves a copy of a JWT with one claim of its payload set to another value and the original signature kept, so
     * the token is well formed but its signature no longer matches: what a service that checks it must refuse.
     *
     * @param jwtVariableName    the variable containing the JWT
     * @param claim              the claim to set
     * @param value              its new value, as a string
     * @param outputVariableName the variable where the tampered JWT is saved
     */
    @Given("^I tamper the JWT in variable '(.+?)' setting claim '(.+?)' to '(.+?)' and save it in '(.+?)'$")
    public void tamperJwt(String jwtVariableName, String claim, String value, String outputVariableName) {
        String jwt = ThreadProperty.get(jwtVariableName);
        assertThat(jwt).as("JWT in variable '%s'", jwtVariableName).isNotEmpty();
        String[] parts = jwt.split("\\.");
        assertThat(parts).as("Parts of the JWT in variable '%s'", jwtVariableName).hasSize(3);

        JSONObject payload = new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        payload.put(claim, value);
        String tampered = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        ThreadProperty.set(outputVariableName, String.join(".", parts[0], tampered, parts[2]));
    }

    /**
     * Saves the value of a query parameter of a URL, decoded. Fails when the URL does not carry it.
     *
     * @param parameter the query parameter
     * @param url       the URL
     * @param variable  the variable where its value is saved
     */
    @Given("^I extract query parameter '(.+?)' from URL '(.+?)' and save it in '(.+?)'$")
    public void extractQueryParameter(String parameter, String url, String variable) {
        Optional<String> value = URLEncodedUtils.parse(URI.create(url), StandardCharsets.UTF_8).stream()
                .filter(pair -> pair.getName().equals(parameter))
                .map(NameValuePair::getValue)
                .findFirst();
        assertThat(value).as("Query parameter '%s' of the URL", parameter).isPresent();
        ThreadProperty.set(variable, value.get());
    }

    @Then("^Excel file in path '(.+?)' contains value '(.+?)'( in sheet '(.+?)')?( with column number '(.+?)')?( with column name '(.+?)')?( with row number '(.+?)')?( with row name '(.+?)')?$")
    public void verifyExcelCellValue(String filePath, String expectedValue, String sheetName,
                                     String columnNumber, String columnName, String rowNumber, String rowName) throws Exception {
        // Set limits to prevent zip bombs
        int maxInflateRatio = 2000;
        ZipSecureFile.setMinInflateRatio(maxInflateRatio);

        try (FileInputStream fis = new FileInputStream(new File(filePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet;
            if (sheetName != null && !sheetName.isEmpty()) {
                sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    throw new IllegalArgumentException("Sheet with name '" + sheetName + "' not found in the Excel file.");
                }
            } else {
                sheet = workbook.getSheetAt(0); // Use the first sheet by default
            }

            Cell cell = findCellWithValue(sheet, expectedValue, columnNumber, columnName, rowNumber, rowName);
            if (cell != null) {
                commonspec.getLogger().debug("String {} found in cell: {}", expectedValue, cell.getStringCellValue());
            } else {
                throw new Exception("String " + expectedValue + " NOT found");
            }
        } catch (IOException e) {
            fail("Could not read the Excel file " + filePath + " -> " + e.getMessage());
        }
    }

    private Cell findCellWithValue(Sheet sheet, String expectedValue, String columnNumber, String columnName,
                                   String rowNumber, String rowName) {
        for (Row row : sheet) {
            if (rowMatchesCriteria(row, rowNumber, rowName)) {
                for (Cell cell : row) {
                    if (cellMatchesCriteria(cell, expectedValue, columnNumber, columnName)) {
                        return cell;
                    }
                }
            }
        }
        return null;
    }

    private boolean rowMatchesCriteria(Row row, String rowNumber, String rowName) {
        if (rowNumber != null && !rowNumber.isEmpty()) {
            return row.getRowNum() == Integer.parseInt(rowNumber) - 1;
        } else if (rowName != null && !rowName.isEmpty()) {
            Cell cell = row.getCell(0);
            return cell != null && cell.getCellType() == CellType.STRING && cell.getStringCellValue().equalsIgnoreCase(rowName);
        }
        return true; // No criteria specified, match any row
    }

    private boolean cellMatchesCriteria(Cell cell, String expectedValue, String columnNumber, String columnName) {
        if (columnNumber != null && !columnNumber.isEmpty()) {
            return cell.getColumnIndex() == Integer.parseInt(columnNumber) - 1 && cellMatchesValue(cell, expectedValue);
        } else if (columnName != null && !columnName.isEmpty()) {
            Cell columnHeaderCell = cell.getSheet().getRow(0).getCell(cell.getColumnIndex());
            return columnHeaderCell != null && columnHeaderCell.getCellType() == CellType.STRING &&
                    columnHeaderCell.getStringCellValue().equalsIgnoreCase(columnName) && cellMatchesValue(cell, expectedValue);
        }
        return cellMatchesValue(cell, expectedValue); // No criteria specified, match any cell
    }

    private boolean cellMatchesValue(Cell cell, String expectedValue) {
        return cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains(expectedValue);
    }

    /**
     * Logs the given message into Scenario step's output
     *
     * @param msg to print
     */
    public void logIntoScenario(String msg) {
        commonspec.getLogger().info(msg);
        if (scenario != null) {
            scenario.log(msg);
        }
    }

    /**
     * Check environment variable values
     * The allowed operations are: [is, matches, is higher than, is higher than or equal to, is lower than, is lower than or equal to, contains, does not contain, is different from]
     * Usage:
     * Then I check the following environment variable values:
     * | VAR1 | is               | 10     |
     * | VAR2 | is higher than   | 15     |
     * | VAR3 | does not contain | prueba |
     *
     * @param table  data table
     */
    @Then("^I check the following environment variable values:$")
    public void checkMultipleEnvironmentVariableValues(DataTable table) {
        ArrayList<String> errorList = new ArrayList<>();
        for (List<String> row : table.cells()) {
            String envVar = ThreadProperty.get(row.get(0));
            String condition = row.get(1);
            String expected_value = row.get(2);

            try {
                checkValue(envVar, condition, expected_value);
            } catch (Exception | Error e) {
                errorList.add(String.format("%s - %s", envVar, e.getMessage()));
            }
        }
        Assert.assertEquals(errorList.size(), 0, "\n" + String.join("\n", errorList) + "\n");
    }

    /**
     * Generates a random alphanumeric string with the specified length and character set.
     *
     * @param lengthStr  Length of the random string to generate (defaults to 10)
     * @param charset    Character set to use: "alpha" for letters only, "numeric" for numbers only,
     *                   "alphanumeric" (default) for letters and numbers, "hex" for hexadecimal
     * @param varName    Name of the environment variable to store the result (defaults to 'random')
     */
    @Given("^I generate a random string( with length '(\\d+)')?( using charset '(alpha|numeric|alphanumeric|hex)')?( and store it in variable '(.*?)')?$")
    public void generateRandomAlphanumericString(String lengthStr, String charset, String varName) {
        int length = (lengthStr != null) ? Integer.parseInt(lengthStr) : 10;
        String selectedCharset = (charset != null) ? charset : "alphanumeric";
        String variableName = (varName != null) ? varName : "random";

        String characters;
        switch (selectedCharset) {
            case "alpha":
                characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
                break;
            case "numeric":
                characters = "0123456789";
                break;
            case "hex":
                characters = "0123456789abcdef";
                break;
            case "alphanumeric":
            default:
                characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
                break;
        }

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(characters.length());
            sb.append(characters.charAt(randomIndex));
        }

        ThreadProperty.set(variableName, sb.toString());
        logIntoScenario(String.format("%s -> %s", variableName, sb));
    }

}
