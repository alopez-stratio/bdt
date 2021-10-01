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
import cucumber.runtime.io.ResourceLoader;
import io.cucumber.core.model.FeatureIdentifier;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

public final class FeatureLoader {

    private static final String FEATURE_SUFFIX = ".feature";

    private final ResourceLoader resourceLoader;

    public FeatureLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<CucumberFeature> load(List<URI> featurePaths) {
        final FeatureBuilder builder = new FeatureBuilder();
        for (URI featurePath : featurePaths) {
            loadFromFeaturePath(builder, featurePath);
        }
        return builder.build();
    }

    private void loadFromFeaturePath(FeatureBuilder builder, URI featurePath) {
        Iterable<Resource> resources = resourceLoader.resources(featurePath, FEATURE_SUFFIX);

        Iterator<Resource> iterator = resources.iterator();
        if (FeatureIdentifier.isFeature(featurePath) && !iterator.hasNext()) {
            throw new IllegalArgumentException("Feature not found: " + featurePath);
        }
        while (iterator.hasNext()) {
            builder.parse(iterator.next());
        }
    }

}
