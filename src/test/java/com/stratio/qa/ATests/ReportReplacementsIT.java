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
 * Checks that the Cucumber report shows the resolved values of ${}/!{}/@{} placeholders:
 * the scenario name is rewritten, and every step carries its replaced text as output.
 * The report itself keeps the original step names, so the output entries are the only
 * place where the executed values are visible.
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

        List<String> outputs = new ArrayList<>();
        for (JsonNode step : scenario.get("steps")) {
            for (JsonNode output : step.path("output")) {
                outputs.add(output.asText());
            }
        }
        assertThat(outputs)
                .as("Steps in the report do not carry their replaced text as output")
                .containsExactly(
                        "I save 'romeo' in variable 'reportCluster'",
                        "'romeo' is 'romeo'");

        assertThat(scenario.get("name").asText())
                .as("Scenario name in the report still holds unresolved placeholders")
                .isEqualTo("Scenario name resolved to romeo");
    }
}
