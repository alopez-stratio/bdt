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

import com.stratio.qa.cucumber.api.Condition;
import com.stratio.qa.cucumber.api.CucumberOptionsCustom;
import com.stratio.qa.cucumber.api.FeatureEnvironment;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import org.testng.annotations.Test;

/**
 * Verifies that -Dcucumber.filter.tags=<expression> filters scenario execution when using @CucumberOptionsCustom.
 *
 * Normal run (no system properties): all 3 scenarios run, @cucumber7tagA sets the var,
 * @cucumber7tagB @skipOnEnv asserts it.
 *
 * TDD run (red before fix, green after):
 *   -Dcucumber.filter.tags=@cucumber7tagB
 *   -DCUCUMBER7_TAG_FILTER_TEST=true
 * Before fix: @cucumber7tagA also runs (filter ignored) -> var set -> "not_set" assertion fails.
 * After fix:  only @cucumber7tagB scenarios run -> var not set -> "not_set" assertion passes.
 */
@CucumberOptionsCustom(
        plugin = "json:target/cucumber-cucumber7-tag-filter-property-custom.json",
        environments = {
                @FeatureEnvironment(conditions = {
                        @Condition(key = "*", value = ".*")
                }, features = {
                        "src/test/resources/features/cucumber7TagFilterTest.feature"
                })
        }
)
public class Cucumber7TagFilterPropertyCustomOptionsIT extends BaseGTest {

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
