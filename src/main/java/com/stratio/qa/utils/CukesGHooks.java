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

import com.stratio.qa.cucumber.testng.TestSourcesModel;
import com.stratio.qa.cucumber.testng.TestSourcesModelUtil;
import com.stratio.qa.specs.BaseGSpec;
import com.stratio.qa.specs.HookGSpec;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import io.cucumber.plugin.event.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CukesGHooks extends BaseGSpec implements ConcurrentEventListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    private String currentFeatureFile = null;

    private int exampleNumber = 1;

    private String previousTestCaseName = "";

    public CukesGHooks() {
    }

    private EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
        @Override
        public void receive(TestSourceRead event) {
            handleTestSourceRead(event);
        }
    };

    private EventHandler<TestCaseStarted> caseStartedHandler = new EventHandler<TestCaseStarted>() {
        @Override
        public void receive(TestCaseStarted event) {
            handleTestCaseStarted(event);
        }
    };

    private EventHandler<TestStepFinished> stepFinishedHandler = new EventHandler<TestStepFinished>() {
        @Override
        public void receive(TestStepFinished event) {
            handleTestStepFinished(event);
        }
    };

    private EventHandler<TestCaseFinished> caseFinishedHandler = new EventHandler<TestCaseFinished>() {
        @Override
        public void receive(TestCaseFinished event) {
            handleTestCaseFinished(event);
        }
    };

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestSourceRead.class, testSourceReadHandler);
        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestCaseFinished.class, caseFinishedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);
    }

    private void handleTestSourceRead(TestSourceRead event) {
        TestSourcesModelUtil.INSTANCE.getTestSourcesModel().addTestSourceReadEvent(event.getUri().toString(), event);
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        String uri = event.getTestCase().getUri().toString();
        if (currentFeatureFile == null || !currentFeatureFile.equals(uri)) {
            currentFeatureFile = uri;
        }
        TestCase tc = event.getTestCase();
        logger.info("Feature/Scenario: {}/{} ", TestSourcesModelUtil.INSTANCE.getTestSourcesModel().getFeatureName(currentFeatureFile), tc.getName());
        ThreadProperty.set("feature", TestSourcesModelUtil.INSTANCE.getTestSourcesModel().getFeatureName(currentFeatureFile));
        ThreadProperty.set("scenario", calculateElementName(tc));
    }

    private void handleTestStepFinished(TestStepFinished event) {
        if (event.getResult().getStatus() == Status.FAILED) {
            StringBuilder stepFailedText = new StringBuilder();
            Throwable error = event.getResult().getError();
            stepFailedText.append("STEP FAILED!!! (" + (error != null ? error.getMessage() : "") + ")");
            if (StepException.INSTANCE.getException() != null) {
                stepFailedText.append(" - ").append(StepException.INSTANCE.getException().getClass().getCanonicalName());
                if (StepException.INSTANCE.getException().getMessage() != null) {
                    stepFailedText.append(": ").append(StepException.INSTANCE.getException().getMessage());
                }
                try {
                    StackTraceElement[] elements = StepException.INSTANCE.getException().getStackTrace();
                    stepFailedText.append(" | ").append(elements[0]);
                } catch (Exception ignore) {
                }
            } else {
                StepException.INSTANCE.setException(new Exception("FAILED SCENARIO"));
            }
            logger.error(stepFailedText.toString());
        }
    }

    private void handleTestCaseFinished(TestCaseFinished event) {
        if (HookGSpec.loggerEnabled) {
            logger.info(""); //empty line to split scenarios
        }
    }

    public String calculateElementName(TestCase testCase) {
        String testCaseName = testCase.getName();
        if (testCaseName.equals(previousTestCaseName)) {
            exampleNumber++;
            ThreadProperty.set("dataSet", String.valueOf(exampleNumber));
            return testCaseName + (testCaseName.contains(" ") ? " " : "_") + exampleNumber;
        } else {
            ThreadProperty.set("dataSet", "");
            previousTestCaseName = testCase.getName();
            exampleNumber = 1;
            return testCaseName;
        }
    }
}