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

package com.stratio.qa.cucumber.runtime.model;

import cucumber.runtime.io.Resource;
import io.cucumber.core.logging.Logger;
import io.cucumber.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureBuilder {

    private final Logger log = LoggerFactory.getLogger(cucumber.runtime.model.FeatureBuilder.class);

    private final Map<String, CucumberFeature> sourceToFeature = new HashMap<>();

    //----STRATIO----
    private final List<CucumberFeature> features = new ArrayList<>();

    //----STRATIO----
    public static final String FEATURE_EXECUTION_ORDER_KEY = "FEATURES_EXECUTION_ORDER";

    //----STRATIO----
    public static final String PRESERVE_ORDER = "PRESERVE_ORDER";

    //----STRATIO----
    public static final String DUPLICATED_FEATURES_KEY = "DUPLICATED_FEATURES";

    //----STRATIO----
    public static final String ALLOW_DUPLICATED_FEATURES = "ALLOW_DUPLICATED";

    public List<CucumberFeature> build() {
        //----STRATIO----
        switch (System.getProperty(FeatureBuilder.FEATURE_EXECUTION_ORDER_KEY, "")) {
            case FeatureBuilder.PRESERVE_ORDER:
                log.debug("Executing cucumber features by definition order: ");
                break;
            default:
                log.debug("Executing cucumber features by default order: ");
                Collections.sort(features, new CucumberFeature.CucumberFeatureUriComparator());
                break;
        }
        features.forEach(f -> log.debug("\t" + f.getUri().getPath()));
        return features;
    }

    public void parse(Resource resource) {
        CucumberFeature parsedFeature = FeatureParser.parseResource(resource);

        switch (System.getProperty(FeatureBuilder.DUPLICATED_FEATURES_KEY, "")) {
            case FeatureBuilder.ALLOW_DUPLICATED_FEATURES:
                log.debug("Allow duplicated features.");
                break;
            default:
                log.debug("Duplicated features not allowed.");
                CucumberFeature existingFeature = sourceToFeature.get(parsedFeature.getSource());
                if (existingFeature != null) {
                    log.warn("Duplicate feature ignored. " + parsedFeature.getUri() + " was identical to " + existingFeature.getUri());
                    return;
                }
                break;
        }
        sourceToFeature.put(parsedFeature.getSource(), parsedFeature);
        //----STRATIO----
        features.add(parsedFeature);
    }
}