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
import com.stratio.qa.utils.BaseGTest;
import cucumber.api.CucumberOptions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QATM-55256: the "Background:" / "Steps:" headers used to trail by one position the
 * step they belonged to.
 * <p>
 * The headers were printed by CukesGHooks on the TestStepStarted event, but that event
 * is emitted by TestStep.run, which PickleStepTestStep.run only reaches after the
 * before-step hooks — by then ReplacementAspect had already logged the step text. So
 * the first step of the Background was printed before the "Background:" header, and
 * every header ended up attached to the wrong step.
 * <p>
 * Both are now emitted from the same place, so this asserts the interleaving rather
 * than the presence of each line: nothing else can guarantee the order.
 */
@CucumberOptions(
        plugin = "json:target/cucumber-background-logger-order.json",
        features = {"src/test/resources/features/backgroundLoggerOrder.feature"}
)
public class BackgroundLoggerOrderIT extends BaseGTest {

    private static final String ASPECT_LOGGER = "com.stratio.qa.aspects.ReplacementAspect";

    /** Headers go to a child logger so log4j2.xml can colour them apart from the steps. */
    private static final String SECTION_LOGGER = ASPECT_LOGGER + ".section";

    private static final List<String> LOGGED = Collections.synchronizedList(new ArrayList<>());

    private CollectingAppender appender;

    private final List<LoggerConfig> patchedConfigs = new ArrayList<>();

    @BeforeClass(alwaysRun = true)
    @Override
    public void beforeGClass(ITestContext context) throws Exception {
        super.beforeGClass(context);
        LOGGED.clear();
        appender = new CollectingAppender();
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        // Both loggers, and de-duplicated: getLoggerConfig falls back to the closest
        // ancestor, so the two names resolve to the same config when the child logger
        // is not declared, and the appender would then collect every message twice.
        for (String name : new String[]{ASPECT_LOGGER, SECTION_LOGGER}) {
            LoggerConfig config = ctx.getConfiguration().getLoggerConfig(name);
            if (!patchedConfigs.contains(config)) {
                config.addAppender(appender, Level.INFO, null);
                patchedConfigs.add(config);
            }
        }
        ctx.updateLoggers();
    }

    @Test(dataProvider = "scenarios")
    public void run(PickleEventWrapper pickleWrapper, CucumberFeatureWrapper featureWrapper) throws Throwable {
        runScenario(pickleWrapper, featureWrapper);
    }

    @AfterClass(alwaysRun = true)
    @Override
    public void afterGClass() throws Exception {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        for (LoggerConfig config : patchedConfigs) {
            config.removeAppender(appender.getName());
        }
        ctx.updateLoggers();
        appender.stop();

        super.afterGClass();

        assertThat(LOGGED)
                .as("Background/Steps headers must precede the steps they introduce")
                .containsExactly(
                        "Background:",
                        "Given I wait '0' seconds",
                        "Steps:",
                        "Given I wait '0' seconds");
    }

    /**
     * Minimal log4j2 appender collecting the messages the aspect logs. log4j-core ships
     * a ListAppender but only in its test-jar, which is not a dependency here.
     */
    private static final class CollectingAppender extends AbstractAppender {

        private CollectingAppender() {
            super("collectingAppender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            LOGGED.add(event.getMessage().getFormattedMessage().trim());
        }
    }
}
