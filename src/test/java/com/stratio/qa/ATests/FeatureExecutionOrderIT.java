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

import com.stratio.qa.cucumber.api.FeatureExecutionOrder;
import com.stratio.qa.cucumber.runtime.model.FeatureBuilder;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@CucumberOptions(features = {
        "src/test/resources/features/Test.feature",
        "src/test/resources/features/Test3.feature",
        "src/test/resources/features/Test1.feature",
        "src/test/resources/features/Test2.feature",
})
@FeatureExecutionOrder(
        order = FeatureBuilder.PRESERVE_ORDER
)
public class FeatureExecutionOrderIT extends BaseGTest {

    @Test
    public void checkExecutionOrder() {
        List<String> reference = Arrays.asList("file:src/test/resources/features/Test.feature", "file:src/test/resources/features/Test3.feature",
                "file:src/test/resources/features/Test1.feature", "file:src/test/resources/features/Test2.feature");

        Object[][] scenarios = cucumberRunner.provideScenarios();
        List<String> uris = Arrays.stream(scenarios).map(scenario -> ((PickleEventWrapper)scenario[0]).getPickleEvent().uri).collect(Collectors.toList());

        assertThat(reference.equals(uris)).as("Feature execution order does not match with the expected one.").isTrue();
    }

    @AfterClass(alwaysRun = true)
    public void afterGClass() throws Exception {
        if (cucumberRunner == null) {
            return;
        }
        cucumberRunner.finish();
    }
}
