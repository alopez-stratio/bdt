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

package com.stratio.qa.utils;

import com.stratio.qa.cucumber.api.FeatureExecutionOrder;
import com.stratio.qa.cucumber.runtime.model.FeatureBuilder;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.CucumberRunner;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.*;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;

public abstract class BaseGTest {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    protected CucumberRunner cucumberRunner;

    protected String browser = "";

    /**
     * Method executed before a suite.
     *
     * @param context
     */
    @BeforeSuite(alwaysRun = true)
    public void beforeGSuite(ITestContext context) {
    }

    /**
     * Method executed after a suite.
     *
     * @param context
     */
    @AfterSuite(alwaysRun = true)
    public void afterGSuite(ITestContext context) {
        logger.info("Done executing this test-run.");
    }

    /**
     * Method executed before a test class.
     *
     * @param context
     */
    @BeforeClass(alwaysRun = true)
    public void beforeGClass(ITestContext context) throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());

        FeatureExecutionOrder annotation =  this.getClass().getAnnotation(FeatureExecutionOrder.class);
        System.setProperty(FeatureBuilder.FEATURE_EXECUTION_ORDER_KEY, annotation != null ? annotation.order() : "");

        cucumberRunner = new CucumberRunner(this.getClass());
    }

    /**
     * Returns two dimensional array of PickleEventWrapper scenarios with their associated CucumberFeatureWrapper feature.
     *
     * @return a two dimensional array of scenarios features.
     */
    @DataProvider
    public Object[][] scenarios() {
        if (cucumberRunner == null) {
            return new Object[0][0];
        }
        return cucumberRunner.provideScenarios();
    }

    /**
     * Method executed before a test method.
     *
     * @param method
     */
    @BeforeMethod(alwaysRun = true)
    public void beforeGMethod(Method method) {
        ThreadProperty.set("browser", this.browser);
    }

    /**
     * Method executed after a test method.
     *
     * @param method
     */
    @AfterMethod(alwaysRun = true)
    public void afterGMethod(Method method) {
    }

    /**
     * Method executed after a test class.
     */
    @AfterClass(alwaysRun = true)
    public void afterGClass() throws Exception {
        if (cucumberRunner == null) {
            return;
        }
        cucumberRunner.finish();
        // Close SSH connections
        if (RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().size() > 0) {
            Iterator it = RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                if (pair.getValue() != null) {
                    // Restore remote /etc/hosts and remove pending locks, if needed
                    ETCHOSTSManagementUtil.INSTANCE.getETCHOSTSManagementUtils().forceReleaseLock("in the ssh connection", pair.getKey().toString());

                    // Close ssh connection
                    logger.debug("Closing SSH remote connection with ID: " + pair.getKey());
                    ((RemoteSSHConnection) pair.getValue()).getSession().disconnect();
                }
                it.remove();
            }
        }

        // Restore local /etc/hosts and remove pending locks, if needed
        ETCHOSTSManagementUtil.INSTANCE.getETCHOSTSManagementUtils().forceReleaseLock(null, null);
        ETCHOSTSManagementUtil.INSTANCE.getETCHOSTSManagementUtils().removeK8sHost();

        // Close JDBC connection
        JDBCConnection.closeConnection();
    }

    /**
     * Run scenario
     *
     * @param pickleWrapper Wrapper to obtain scenario name
     * @param featureWrapper Wrapper to obtain feature name
     * @throws Throwable
     */
    public void runScenario(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        cucumberRunner.runScenario(pickleWrapper.getPickleEvent());
    }
}
