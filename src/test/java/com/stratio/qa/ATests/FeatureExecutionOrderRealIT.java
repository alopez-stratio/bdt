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
import com.stratio.qa.cucumber.runtime.FeaturePathFeatureSupplier;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.testng.annotations.Test;

/**
 * Verifies PRESERVE_ORDER through real scenario execution:
 * Test3.feature sets !{preserveOrderVar}='from_test3', Test2.feature asserts it.
 * If feature order is wrong (alphabetical: Test, Test1, Test2, Test3),
 * Test2 runs before Test3 and the assertion fails because the variable is not set.
 */
@CucumberOptions(
        plugin = "json:target/cucumber-feature-execution-order-real.json",
        features = {
                "src/test/resources/features/Test.feature",
                "src/test/resources/features/Test3.feature",
                "src/test/resources/features/Test1.feature",
                "src/test/resources/features/Test2.feature",
        }
)
@FeatureExecutionOrder(
        order = FeaturePathFeatureSupplier.PRESERVE_ORDER
)
public class FeatureExecutionOrderRealIT extends BaseGTest {

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
