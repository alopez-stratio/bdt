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
import com.stratio.qa.cucumber.api.FeatureExecutionOrder;
import com.stratio.qa.cucumber.runtime.FeaturePathFeatureSupplier;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import org.testng.annotations.Test;

/**
 * Verifies that PRESERVE_ORDER works when using @CucumberOptionsCustom + @FeatureEnvironment.
 * Test3.feature sets !{preserveOrderVar}='from_test3', Test2.feature asserts it.
 * Declared order: Test3 -> Test2. Alphabetical order would be Test2 -> Test3 (fails).
 * With PRESERVE_ORDER respected, Test3 runs first and Test2 assertion passes.
 */
@CucumberOptionsCustom(environments = {
        @FeatureEnvironment(conditions = {
                @Condition(key = "*", value = ".*")
        }, features = {
                "src/test/resources/features/Test3.feature",
                "src/test/resources/features/Test2.feature"
        })
}, plugin = "json:target/cucumber-custom-options-order.json")
@FeatureExecutionOrder(order = FeaturePathFeatureSupplier.PRESERVE_ORDER)
public class FeatureExecutionOrderCustomOptionsIT extends BaseGTest {

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
