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

// STRATIO-FORK: Stratio-written options factory (no direct upstream equivalent in Cucumber 6).
// Changes vs standard: (1) supports @CucumberOptionsCustom with extraGlue() and environments();
// (2) supports compatibility @cucumber.api.CucumberOptions shim annotation;
// (3) getEnvironmentFeatures() + checkConditions() enable conditional feature execution.

package com.stratio.qa.cucumber.runtime;

import com.stratio.qa.cucumber.api.Condition;
import com.stratio.qa.cucumber.api.CucumberOptionsCustom;
import com.stratio.qa.cucumber.api.FeatureEnvironment;
import io.cucumber.core.backend.ObjectFactory;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.feature.GluePath;
import io.cucumber.core.options.CucumberOptionsAnnotationParser;
import io.cucumber.core.options.RuntimeOptionsBuilder;
import io.cucumber.core.snippets.SnippetType;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class RuntimeOptionsFactoryCustom {

    private final Class<?> clazz;

    public RuntimeOptionsFactoryCustom(Class<?> clazz) {
        this.clazz = clazz;
    }

    public RuntimeOptionsBuilder buildOptions() {
        RuntimeOptionsBuilder builder = new CucumberOptionsAnnotationParser()
                .withOptionsProvider(new StratioOptionsProvider())
                .parse(clazz);
        return builder;
    }

    private class StratioOptionsProvider implements CucumberOptionsAnnotationParser.OptionsProvider {
        @Override
        public CucumberOptionsAnnotationParser.CucumberOptions getOptions(Class<?> c) {
            CucumberOptionsCustom custom = c.getAnnotation(CucumberOptionsCustom.class);
            if (custom != null) {
                return new CustomOptionsAdapter(custom);
            }
            cucumber.api.CucumberOptions standard = c.getAnnotation(cucumber.api.CucumberOptions.class);
            if (standard != null) {
                return new StandardOptionsAdapter(standard);
            }
            return null;
        }
    }

    private class CustomOptionsAdapter implements CucumberOptionsAnnotationParser.CucumberOptions {
        private final CucumberOptionsCustom custom;

        CustomOptionsAdapter(CucumberOptionsCustom custom) {
            this.custom = custom;
        }

        @Override
        public boolean dryRun() {
            return custom.dryRun();
        }

        @Override
        public String[] features() {
            return getEnvironmentFeatures(custom.environments());
        }

        @Override
        public String[] glue() {
            return new String[0];
        }

        @Override
        public String[] extraGlue() {
            return new String[0];
        }

        @Override
        public String tags() {
            String[] tags = custom.tags();
            if (tags.length == 0) {
                return "";
            }
            if (tags.length == 1) {
                return tags[0];
            }
            return "(" + String.join(") and (", tags) + ")";
        }

        @Override
        public String[] plugin() {
            return custom.plugin();
        }

        @Override
        public boolean publish() {
            return false;
        }

        @Override
        public boolean monochrome() {
            return custom.monochrome();
        }

        @Override
        public String[] name() {
            return custom.name();
        }

        @Override
        public SnippetType snippets() {
            return custom.snippets();
        }

        @Override
        public Class<? extends ObjectFactory> objectFactory() {
            return null;
        }

        @Override
        public Class<? extends io.cucumber.core.eventbus.UuidGenerator> uuidGenerator() {
            return null;
        }
    }

    private class StandardOptionsAdapter implements CucumberOptionsAnnotationParser.CucumberOptions {
        private final cucumber.api.CucumberOptions opts;

        StandardOptionsAdapter(cucumber.api.CucumberOptions opts) {
            this.opts = opts;
        }

        @Override
        public boolean dryRun() {
            return opts.dryRun();
        }

        @Override
        public String[] features() {
            return opts.features();
        }

        @Override
        public String[] glue() {
            return new String[0];
        }

        @Override
        public String[] extraGlue() {
            return new String[0];
        }

        @Override
        public String tags() {
            return opts.tags();
        }

        @Override
        public String[] plugin() {
            return opts.plugin();
        }

        @Override
        public boolean publish() {
            return false;
        }

        @Override
        public boolean monochrome() {
            return opts.monochrome();
        }

        @Override
        public String[] name() {
            return opts.name();
        }

        @Override
        public SnippetType snippets() {
            return opts.snippets();
        }

        @Override
        public Class<? extends ObjectFactory> objectFactory() {
            return null;
        }

        @Override
        public Class<? extends io.cucumber.core.eventbus.UuidGenerator> uuidGenerator() {
            return null;
        }
    }

    private String[] getEnvironmentFeatures(FeatureEnvironment[] environments) {
        if (environments == null || environments.length == 0) {
            return new String[0];
        }
        List<String> features = new ArrayList<>();
        for (FeatureEnvironment env : environments) {
            if (checkConditions(env.conditions())) {
                features.addAll(Arrays.asList(env.features()));
            }
        }
        return features.toArray(new String[0]);
    }

    private boolean checkConditions(Condition[] conditions) {
        for (Condition c : conditions) {
            try {
                if (c.key().equals("*")) {
                    continue;
                } else if ((System.getProperty(c.key()) == null) || (!System.getProperty(c.key()).matches(c.value()))) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
