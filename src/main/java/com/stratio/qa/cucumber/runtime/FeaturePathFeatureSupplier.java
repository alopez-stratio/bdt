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

// STRATIO-FORK of io.cucumber.core.runtime.FeaturePathFeatureSupplier (Cucumber 6.11.0)
// Changes: (1) preprocesses feature source via LoopIncludeTagAspect before Gherkin parsing;
//          (2) honours FEATURES_EXECUTION_ORDER/PRESERVE_ORDER system property for sort order;
//          (3) honours DUPLICATED_FEATURES/ALLOW_DUPLICATED system property for dedup.

package com.stratio.qa.cucumber.runtime;

import com.stratio.qa.aspects.LoopIncludeTagAspect;
import com.stratio.qa.exceptions.IncludeException;
import io.cucumber.core.exception.CucumberException;
import io.cucumber.core.feature.FeatureParser;
import io.cucumber.core.feature.Options;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.logging.Logger;
import io.cucumber.core.logging.LoggerFactory;
import io.cucumber.core.resource.Resource;
import io.cucumber.core.resource.ResourceScanner;
import io.cucumber.core.runtime.FeatureSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class FeaturePathFeatureSupplier implements FeatureSupplier {

    private static final Logger log = LoggerFactory.getLogger(FeaturePathFeatureSupplier.class);

    public static final String FEATURE_EXECUTION_ORDER_KEY = "FEATURES_EXECUTION_ORDER";

    public static final String PRESERVE_ORDER = "PRESERVE_ORDER";

    public static final String DUPLICATED_FEATURES_KEY = "DUPLICATED_FEATURES";

    public static final String ALLOW_DUPLICATED_FEATURES = "ALLOW_DUPLICATED";

    private final ResourceScanner<Feature> featureScanner;

    private final Options featureOptions;

    private final List<URI> explicitPaths;

    public FeaturePathFeatureSupplier(Supplier<ClassLoader> classLoaderSupplier, Options featureOptions, Supplier<UUID> idGenerator) {
        this(classLoaderSupplier, featureOptions, idGenerator, null);
    }

    public FeaturePathFeatureSupplier(Supplier<ClassLoader> classLoaderSupplier, Options featureOptions, Supplier<UUID> idGenerator, List<URI> explicitPaths) {
        FeatureParser baseParser = new FeatureParser(idGenerator);
        this.featureScanner = new ResourceScanner<>(
            classLoaderSupplier,
            path -> isFeatureFile(path),
            resource -> baseParser.parseResource(preprocess(resource))
        );
        this.featureOptions = featureOptions;
        this.explicitPaths = explicitPaths;
    }

    @Override
    public List<Feature> get() {
        List<URI> featurePaths = (explicitPaths != null && !explicitPaths.isEmpty())
                ? explicitPaths
                : featureOptions.getFeaturePaths();
        if (featurePaths.isEmpty()) {
            log.warn(() -> "Got no path to feature directory or feature file");
            return Collections.emptyList();
        }

        Map<URI, Feature> uriToFeature = new HashMap<>();
        List<Feature> features = new ArrayList<>();
        boolean allowDuplicated = ALLOW_DUPLICATED_FEATURES.equals(System.getProperty(DUPLICATED_FEATURES_KEY, ""));

        for (URI uri : featurePaths) {
            List<Feature> loaded = featureScanner.scanForResourcesUri(uri);
            for (Feature feature : loaded) {
                if (!allowDuplicated) {
                    Feature existing = uriToFeature.get(feature.getUri());
                    if (existing != null) {
                        log.warn(() -> "Duplicate feature ignored: " + feature.getUri());
                        continue;
                    }
                }
                uriToFeature.put(feature.getUri(), feature);
                features.add(feature);
            }
        }

        // Preserve insertion order when paths were explicitly provided (annotation or cucumber.features).
        // Sort alphabetically only when paths came from broad directory scanning (no explicit list).
        boolean explicitOrder = (explicitPaths != null && !explicitPaths.isEmpty())
                || PRESERVE_ORDER.equals(System.getProperty(FEATURE_EXECUTION_ORDER_KEY, ""));
        if (!explicitOrder) {
            features.sort((a, b) -> a.getUri().compareTo(b.getUri()));
        }

        return features;
    }

    private static boolean isFeatureFile(Path path) {
        return path.getFileName().toString().endsWith(".feature");
    }

    private static Resource preprocess(Resource resource) {
        try {
            byte[] bytes;
            try (InputStream is = resource.getInputStream()) {
                bytes = is.readAllBytes();
            }
            String content = new String(bytes, StandardCharsets.UTF_8);

            List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n")));

            String rawPath = resource.getUri().getRawSchemeSpecificPart();
            String dir = rawPath.contains("/") ? rawPath.substring(0, rawPath.lastIndexOf('/') + 1) : "";

            LoopIncludeTagAspect preprocessor = new LoopIncludeTagAspect();
            preprocessor.processBackground(lines);
            preprocessor.parseLines(lines, dir);

            final byte[] processed = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
            URI uri = resource.getUri();

            return new Resource() {
                @Override
                public URI getUri() {
                    return uri;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(processed);
                }
            };
        } catch (IncludeException e) {
            throw new CucumberException("Failed to preprocess feature: " + resource.getUri(), e);
        } catch (IOException e) {
            throw new CucumberException("Failed to read feature: " + resource.getUri(), e);
        }
    }
}
