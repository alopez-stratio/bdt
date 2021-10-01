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


package com.stratio.qa.cucumber.runtime;

import com.stratio.qa.cucumber.runtime.model.CucumberFeature;
import com.stratio.qa.cucumber.runtime.model.FeatureLoader;
import cucumber.util.FixJava;
import io.cucumber.core.logging.Logger;
import io.cucumber.core.logging.LoggerFactory;
import io.cucumber.core.options.FeatureOptions;

import java.net.URI;
import java.util.List;

/**
 * Supplies a list of features found on the the feature path provided to RuntimeOptions.
 */
public class FeaturePathFeatureSupplier implements FeatureSupplier {

    private static final Logger log = LoggerFactory.getLogger(cucumber.runtime.FeaturePathFeatureSupplier.class);

    private final FeatureLoader featureLoader;

    private final FeatureOptions featureOptions;

    public FeaturePathFeatureSupplier(FeatureLoader featureLoader, FeatureOptions featureOptions) {
        this.featureLoader = featureLoader;
        this.featureOptions = featureOptions;
    }

    @Override
    public List<CucumberFeature> get() {
        List<URI> featurePaths = featureOptions.getFeaturePaths();

        log.debug("Loading features from " + FixJava.join(featurePaths, ", "));
        List<CucumberFeature> cucumberFeatures = featureLoader.load(featurePaths);

        if (cucumberFeatures.isEmpty()) {
            if (featurePaths.isEmpty()) {
                log.warn("Got no path to feature directory or feature file");
            } else {
                log.warn("No features found at " + FixJava.join(featurePaths, ", "));
            }
        }

        return cucumberFeatures;
    }
}
