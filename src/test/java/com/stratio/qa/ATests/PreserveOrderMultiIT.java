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
 * Verifies PRESERVE_ORDER with multi-scenario features.
 *
 * Annotation order is the REVERSE of alphabetical:
 *   annotated: [Multi2, Multi1]
 *   alphabetical: [Multi1, Multi2]
 *
 * Multi2 (runs first when order is preserved) sets !{multiOrderVar}='from_multi2'.
 * Multi1 (runs second) checks that value. If PRESERVE_ORDER is broken and
 * alphabetical sort kicks in, Multi1 runs first, finds the variable unset, and fails.
 */
@CucumberOptions(
        plugin = "json:target/cucumber-preserve-order-multi.json",
        features = {
                "src/test/resources/features/preserveOrderMulti2.feature",
                "src/test/resources/features/preserveOrderMulti1.feature",
        }
)
@FeatureExecutionOrder(
        order = FeaturePathFeatureSupplier.PRESERVE_ORDER
)
public class PreserveOrderMultiIT extends BaseGTest {

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
