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

import io.cucumber.core.exception.CucumberException;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import org.testng.SkipException;

class TestCaseResultListener {

    static final String UNDEFINED_MESSAGE = "There are undefined steps";

    static final String SKIPPED_MESSAGE = "This scenario is skipped";

    private final EventPublisher bus;

    private final boolean strict;

    private Result result;

    private final EventHandler<TestCaseFinished> handler = event -> receiveResult(event.getResult());

    TestCaseResultListener(EventPublisher bus, boolean strict) {
        this.strict = strict;
        this.bus = bus;
        bus.registerHandlerFor(TestCaseFinished.class, handler);
    }

    void finishExecutionUnit() {
        bus.removeHandlerFor(TestCaseFinished.class, handler);
    }

    private void receiveResult(Result r) {
        this.result = r;
    }

    boolean isPassed() {
        return result == null || result.getStatus() == Status.PASSED;
    }

    Throwable getError() {
        if (result == null) {
            return null;
        }
        switch (result.getStatus()) {
            case FAILED:
            case AMBIGUOUS:
                return result.getError();
            case PENDING:
                if (strict) {
                    return result.getError();
                } else {
                    return new SkipException("Pending step", result.getError());
                }
            case UNDEFINED:
                if (strict) {
                    return new CucumberException(UNDEFINED_MESSAGE);
                } else {
                    return new SkipException(UNDEFINED_MESSAGE);
                }
            case SKIPPED:
                Throwable error = result.getError();
                if (error != null) {
                    return error instanceof SkipException ? error : new SkipException("Skipped step", error);
                } else {
                    return new SkipException(SKIPPED_MESSAGE);
                }
            case PASSED:
                return null;
            default:
                throw new IllegalStateException("Unexpected result status: " + result.getStatus());
        }
    }
}
