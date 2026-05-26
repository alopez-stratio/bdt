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
package com.stratio.qa.cucumber.testng;

import static org.assertj.core.api.Assertions.assertThat;

import com.stratio.qa.ATests.Cucumber7FeaturesPropertyCustomOptionsIT;
import com.stratio.qa.ATests.Cucumber7FeaturesPropertyIT;
import com.stratio.qa.ATests.Cucumber7TagFilterPropertyCustomOptionsIT;
import com.stratio.qa.ATests.Cucumber7TagFilterPropertyIT;
import com.stratio.qa.cucumber.runtime.FeaturePathFeatureSupplier;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CucumberRunnerTest {

    private static final String FEAT_CHECKER = "src/test/resources/features/cucumber7FeatProp2Checker.feature";
    private static final String FEAT_SETTER  = "src/test/resources/features/cucumber7FeatProp1Setter.feature";

    @BeforeMethod
    @AfterMethod
    public void clearCucumberSystemProperties() {
        System.clearProperty("cucumber.features");
        System.clearProperty("cucumber.filter.tags");
        System.clearProperty(FeaturePathFeatureSupplier.FEATURE_EXECUTION_ORDER_KEY);
    }

    @Test
    public void cucumberFeaturesPropertyPreservesNonAlphabeticalOrder() throws Exception {
        // Checker (cucumber7FeatProp2) listed before Setter (cucumber7FeatProp1) — reverse alphabetical.
        // Without fix: RuntimeOptions.getFeaturePaths() sorts internally → setter (1) first.
        // With fix: parsed directly from system property → checker (2) first as specified.
        System.setProperty("cucumber.features", FEAT_CHECKER + "," + FEAT_SETTER);
        CucumberRunner runner = new CucumberRunner(Cucumber7FeaturesPropertyIT.class);
        Object[][] scenarios = runner.provideScenarios();
        assertThat(((PickleEventWrapper) scenarios[0][0]).getPickleEvent().getUri().toString())
                .contains("cucumber7FeatProp2Checker");
    }

    @Test
    public void cucumberFeaturesPropertyPreservesNonAlphabeticalOrderWithCustomOptions() throws Exception {
        System.setProperty("cucumber.features", FEAT_CHECKER + "," + FEAT_SETTER);
        CucumberRunner runner = new CucumberRunner(Cucumber7FeaturesPropertyCustomOptionsIT.class);
        Object[][] scenarios = runner.provideScenarios();
        assertThat(((PickleEventWrapper) scenarios[0][0]).getPickleEvent().getUri().toString())
                .contains("cucumber7FeatProp2Checker");
    }

    @Test
    public void cucumberFeaturesPropertyFiltersToSpecifiedFeature() throws Exception {
        System.setProperty("cucumber.features",
                "src/test/resources/features/cucumber7FeatProp2Checker.feature");
        CucumberRunner runner = new CucumberRunner(Cucumber7FeaturesPropertyIT.class);
        Object[][] scenarios = runner.provideScenarios();
        // Without fix: setter(1) + checker(2) = 3 scenarios. With fix: checker only = 2.
        assertThat(scenarios).hasSize(2);
        for (Object[] scenario : scenarios) {
            assertThat(((PickleEventWrapper) scenario[0]).getPickleEvent().getUri().toString())
                    .contains("cucumber7FeatProp2Checker");
        }
    }

    @Test
    public void cucumberFeaturesPropertyFiltersToSpecifiedFeatureWithCustomOptions() throws Exception {
        System.setProperty("cucumber.features",
                "src/test/resources/features/cucumber7FeatProp2Checker.feature");
        CucumberRunner runner = new CucumberRunner(Cucumber7FeaturesPropertyCustomOptionsIT.class);
        Object[][] scenarios = runner.provideScenarios();
        // Without fix: setter(1) + checker(2) = 3 scenarios. With fix: checker only = 2.
        assertThat(scenarios).hasSize(2);
        for (Object[] scenario : scenarios) {
            assertThat(((PickleEventWrapper) scenario[0]).getPickleEvent().getUri().toString())
                    .contains("cucumber7FeatProp2Checker");
        }
    }

    @Test
    public void cucumberFilterTagsPropertyFiltersToMatchingScenarios() throws Exception {
        System.setProperty("cucumber.filter.tags", "@cucumber7tagB");
        CucumberRunner runner = new CucumberRunner(Cucumber7TagFilterPropertyIT.class);
        Object[][] scenarios = runner.provideScenarios();
        // Without fix: all 3 scenarios (tagA + tagB×2). With fix: tagB only = 2.
        assertThat(scenarios).hasSize(2);
        for (Object[] scenario : scenarios) {
            assertThat(((PickleEventWrapper) scenario[0]).getPickleEvent().getTags())
                    .contains("@cucumber7tagB");
        }
    }

    @Test
    public void cucumberFilterTagsPropertyFiltersToMatchingScenariosWithCustomOptions() throws Exception {
        System.setProperty("cucumber.filter.tags", "@cucumber7tagB");
        CucumberRunner runner = new CucumberRunner(Cucumber7TagFilterPropertyCustomOptionsIT.class);
        Object[][] scenarios = runner.provideScenarios();
        // Without fix: all 3 scenarios (tagA + tagB×2). With fix: tagB only = 2.
        assertThat(scenarios).hasSize(2);
        for (Object[] scenario : scenarios) {
            assertThat(((PickleEventWrapper) scenario[0]).getPickleEvent().getTags())
                    .contains("@cucumber7tagB");
        }
    }
}
