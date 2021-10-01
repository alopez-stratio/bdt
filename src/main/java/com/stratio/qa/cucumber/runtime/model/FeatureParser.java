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

import cucumber.runtime.CucumberException;
import cucumber.runtime.io.Resource;
import cucumber.util.Encoding;
import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.ParserException;
import gherkin.TokenMatcher;
import gherkin.ast.GherkinDocument;
import gherkin.events.PickleEvent;
import gherkin.pickles.Compiler;
import gherkin.pickles.Pickle;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class FeatureParser {
    private FeatureParser() {

    }

    public static CucumberFeature parseResource(Resource resource) {
        requireNonNull(resource);
        URI path = resource.getPath();
        String source = read(resource);

        try {
            Parser<GherkinDocument> parser = new Parser<>(new AstBuilder());
            TokenMatcher matcher = new TokenMatcher();
            GherkinDocument gherkinDocument = parser.parse(source, matcher);
            List<PickleEvent> pickleEvents = compilePickles(gherkinDocument, resource);
            return new CucumberFeature(gherkinDocument, path, source, pickleEvents);
        } catch (ParserException e) {
            throw new CucumberException("Failed to parse resource at: " + path.toString(), e);
        }
    }

    private static String read(Resource resource) {
        try {
            return Encoding.readFile(resource);
        } catch (IOException e) {
            throw new CucumberException("Failed to read resource:" + resource.getPath(), e);
        }
    }


    private static List<PickleEvent> compilePickles(GherkinDocument gherkinDocument, Resource resource) {
        if (gherkinDocument.getFeature() == null) {
            return Collections.emptyList();
        }
        List<PickleEvent> pickleEvents = new ArrayList<>();
        for (Pickle pickle : new Compiler().compile(gherkinDocument)) {
            pickleEvents.add(new PickleEvent(resource.getPath().toString(), pickle));
        }
        return pickleEvents;
    }
}
