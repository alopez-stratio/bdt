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
package com.stratio.qa.ATests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks how ${}/!{}/@{} placeholders reach the Cucumber report.
 * <p>
 * The step name must be the text the match was computed against — ${ENV} resolved,
 * !{VAR} untouched — so that the match argument offsets keep pointing inside it.
 * Whatever the name cannot show is added as step output: the raw gherkin text when
 * ${ENV} was resolved, and the fully resolved text when !{VAR} was used.
 */
@CucumberOptions(
        plugin = "json:target/cucumber-report-replacements.json",
        features = {"src/test/resources/features/reportReplacements.feature"}
)
public class ReportReplacementsIT extends BaseGTest {

    private static final String REPORT = "target/cucumber-report-replacements.json";

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }

    @AfterClass(alwaysRun = true)
    @Override
    public void afterGClass() throws Exception {
        // Writes the json report: MessagesToJsonWriter only flushes on TestRunFinished
        super.afterGClass();

        JsonNode features = new ObjectMapper().readTree(new File(REPORT));
        JsonNode scenario = features.get(0).get("elements").get(0);

        assertThat(scenario.get("name").asText())
                .as("Scenario name in the report still holds unresolved placeholders")
                .isEqualTo("Scenario name resolved to romeo");

        List<String> names = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (JsonNode step : scenario.get("steps")) {
            names.add(step.get("name").asText());
            for (JsonNode output : step.path("output")) {
                outputs.add(output.asText());
            }
        }

        assertMatchArgumentsPointInsideNames(scenario);

        assertThat(names)
                .as("Step names must be the matched text: ${ENV} resolved, !{VAR} untouched")
                .containsExactly(
                        "I save 'romeo' in variable 'reportCluster'",
                        "'!{reportCluster}' is 'romeo'",
                        "'!{reportCluster}' contains 'romeo'");

        assertThat(outputs)
                .as("Steps must carry as output whatever their name cannot show")
                .containsExactly(
                        // ${ENV} resolved in the name, so the raw text is the added value
                        "I save '${REPORT_CLUSTER:-romeo}' in variable 'reportCluster'",
                        // only !{VAR}, so the resolved text is the added value
                        "'romeo' is 'romeo'",
                        // both: raw text first, fully resolved second
                        "'!{reportCluster}' contains '${REPORT_CLUSTER:-romeo}'",
                        "'romeo' contains 'romeo'");
    }

    /**
     * QATM-55258: renderers index the step name with the match argument offsets.
     * net.masterthought cucumber-reporting does chars[offset + val.length() - 1] with
     * no bounds check, so an offset computed against a different string throws inside
     * a Velocity #set and the report prints "$_noescape_stepName" instead of the step.
     *
     * @param scenario scenario node of the json report
     */
    private void assertMatchArgumentsPointInsideNames(JsonNode scenario) {
        for (JsonNode step : scenario.get("steps")) {
            String name = step.get("name").asText();
            for (JsonNode argument : step.path("match").path("arguments")) {
                if (!argument.has("offset") || !argument.has("val")) {
                    continue;
                }
                int offset = argument.get("offset").asInt();
                String value = argument.get("val").asText();
                assertThat(offset + value.length())
                        .as("Argument '%s' at offset %s overflows step name '%s'", value, offset, name)
                        .isLessThanOrEqualTo(name.length());
                assertThat(name.substring(offset, offset + value.length()))
                        .as("Argument '%s' at offset %s does not match step name '%s'", value, offset, name)
                        .isEqualTo(value);
            }
        }
    }
}
