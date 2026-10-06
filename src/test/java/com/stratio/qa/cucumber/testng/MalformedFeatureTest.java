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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import io.cucumber.core.gherkin.FeatureParserException;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.Test;

/**
 * A feature that fails to parse must fail the build. Gherkin parse errors are
 * {@link FeatureParserException}, which is not a CucumberException: it used to escape the
 * "scenarios" data provider, and TestNG reports a data provider failure as a skip, so the run
 * ended with "Skipped: 1" and BUILD SUCCESS.
 */
public class MalformedFeatureTest {

    private static final String MALFORMED_FEATURE = "src/test/resources/malformedFeatures/inconsistentTableCells.feature";

    /**
     * Runner shaped like the ones in ATests. Its test is disabled so that surefire never runs it
     * on its own (with -Dtest it also picks up inner classes); only the TestNG run below enables it.
     */
    @CucumberOptions(features = MALFORMED_FEATURE)
    public static class MalformedFeatureRunner extends BaseGTest {

        @Test(dataProvider = "scenarios", enabled = false)
        public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
            runScenario(pickleWrapper, featureWrapper);
        }
    }

    @Test
    public void provideScenariosHandsTheParseErrorToTheTest() throws Exception {
        Object[][] scenarios = new CucumberRunner(MalformedFeatureRunner.class).provideScenarios();

        assertThat(scenarios).hasSize(1);
        PickleEventWrapper wrapper = (PickleEventWrapper) scenarios[0][0];
        assertThatThrownBy(wrapper::getPickleEvent)
                .isInstanceOf(FeatureParserException.class)
                .hasMessageContaining("Failed to parse resource")
                .hasMessageContaining("inconsistentTableCells.feature");
        assertThat(wrapper.toString()).contains("inconsistentTableCells.feature");
    }

    @Test
    public void malformedFeatureIsReportedAsAFailureNotASkip() {
        TestListenerAdapter listener = new TestListenerAdapter();
        TestNG testng = new TestNG(false);
        testng.setUseDefaultListeners(false);
        testng.setVerbose(0);
        testng.setTestClasses(new Class[]{MalformedFeatureRunner.class});
        testng.setAnnotationTransformer((annotation, testClass, testConstructor, testMethod) -> annotation.setEnabled(true));
        testng.addListener(listener);

        testng.run();

        assertThat(listener.getSkippedTests()).isEmpty();
        assertThat(listener.getFailedTests()).hasSize(1);
        ITestResult failed = listener.getFailedTests().get(0);
        assertThat(failed.getThrowable())
                .isInstanceOf(FeatureParserException.class)
                .hasMessageContaining("inconsistentTableCells.feature");
        assertThat(testng.hasFailure()).isTrue();
    }
}
