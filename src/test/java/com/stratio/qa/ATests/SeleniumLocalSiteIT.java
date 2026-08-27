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

import java.io.IOException;

import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.data.BrowsersDataProvider;
import com.stratio.qa.utils.BaseGTest;
import com.stratio.qa.utils.SeleniumTestSite;
import cucumber.api.CucumberOptions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 * Drives the Selenium steps against a page this suite serves itself.
 *
 * <p>The three feature files that used to cover this aimed at www.google.es and www.metabase.com,
 * had no runner pointing at them, and looked for markup those sites dropped years ago. Serving the
 * page from {@link SeleniumTestSite} keeps the browser container busy without depending on the
 * internet or on anyone else's HTML staying still.
 */
@CucumberOptions(plugin = "json:target/cucumber-selenium-local-site.json", features = {
        "src/test/resources/features/seleniumLocalSite.feature"
})
public class SeleniumLocalSiteIT extends BaseGTest {

    /** System property the feature reads through ${SELENIUM_TEST_SITE}. */
    private static final String SITE_PROPERTY = "SELENIUM_TEST_SITE";

    private static final SeleniumTestSite SITE = new SeleniumTestSite();

    @Factory(enabled = false, dataProviderClass = BrowsersDataProvider.class, dataProvider = "availableUniqueBrowsers")
    public SeleniumLocalSiteIT(String browser) {
        this.browser = browser;
    }

    @BeforeClass(alwaysRun = true)
    public void startSite() throws IOException {
        SITE.start();
        System.setProperty(SITE_PROPERTY, SITE.getAddress());
    }

    @AfterClass(alwaysRun = true)
    public void stopSite() {
        SITE.stop();
        System.clearProperty(SITE_PROPERTY);
    }

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }
}
