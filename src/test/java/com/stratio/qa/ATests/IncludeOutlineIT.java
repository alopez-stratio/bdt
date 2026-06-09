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

import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.data.BrowsersDataProvider;
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 * Regression test for QATM-55235:
 * Scenario Outline placeholders (<param>) arrive literal when the outline immediately
 * follows a plain scenario that is @include-d later in the same feature file.
 */
@CucumberOptions(plugin = "json:target/cucumber-include-outline.json", features = {
        "src/test/resources/features/includeOutlineIT.feature"
})
public class IncludeOutlineIT extends BaseGTest {

    @Factory(enabled = false, dataProviderClass = BrowsersDataProvider.class, dataProvider = "availableUniqueBrowsers")
    public IncludeOutlineIT(String browser) {
        this.browser = browser;
    }

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
