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
import com.stratio.qa.cucumber.api.FeatureExecutionParams;
import com.stratio.qa.cucumber.runtime.FeaturePathFeatureSupplier;
import com.stratio.qa.cucumber.testng.CucumberFeatureWrapper;
import com.stratio.qa.cucumber.testng.CucumberRunner;
import com.stratio.qa.cucumber.testng.PickleEventWrapper;
import com.stratio.qa.specs.CommandExecutionSpec;
import com.stratio.qa.specs.CommonG;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.*;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

        FeatureExecutionOrder annotationOrder =  this.getClass().getAnnotation(FeatureExecutionOrder.class);
        FeatureExecutionParams annotationParams  =  this.getClass().getAnnotation(FeatureExecutionParams.class);
        System.setProperty(FeaturePathFeatureSupplier.DUPLICATED_FEATURES_KEY, annotationParams != null ? annotationParams.allowDuplicates() : "");
        System.setProperty(FeaturePathFeatureSupplier.FEATURE_EXECUTION_ORDER_KEY, annotationParams != null ? annotationParams.order() : (annotationOrder != null ? annotationOrder.order() : ""));

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

        // Close JDBC connection
        JDBCConnection.closeConnection();

        //Remove local keos-installer docker
        if (ThreadProperty.get("localDocker") != null) {
            logger.debug("Cleaning local keos-installer docker container");
            CommandExecutionSpec commandSpec = new CommandExecutionSpec(new CommonG());
            commandSpec.executeLocalCommand("docker stop " + ThreadProperty.get("localDocker"), null, null, null);
            commandSpec.executeLocalCommand("docker rm " + ThreadProperty.get("localDocker"), null, null, null);
            commandSpec.executeLocalCommand("sudo rm -R target/test-classes/workspaceKeosInstaller", null, null, null);
        }

        // Restore AWS config
        restoreAWSConfig();
    }

    private void restoreAWSConfig() {
        File awsPath = new File(System.getProperty("user.home") + "/.aws/");
        File credentialsFile = new File(awsPath.getAbsolutePath() + "/credentials");
        File configFile = new File(awsPath.getAbsolutePath() + "/config");
        File credentialsFileBackup = new File(awsPath.getAbsolutePath() + "/credentials.bdt.backup");
        File configFileBackup = new File(awsPath.getAbsolutePath() + "/config.bdt.backup");
        try {
            // Check if file exists and backup it
            if (credentialsFileBackup.exists()) {
                Files.copy(credentialsFileBackup.toPath(), credentialsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                credentialsFileBackup.delete();
            }
            if (configFileBackup.exists()) {
                Files.copy(configFileBackup.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                configFileBackup.delete();
            }
        } catch (Exception e) {
            logger.error("Error restoring AWS config", e);
        }
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
