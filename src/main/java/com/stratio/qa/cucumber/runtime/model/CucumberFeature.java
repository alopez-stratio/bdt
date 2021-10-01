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

import cucumber.api.event.TestSourceRead;
import cucumber.runner.EventBus;
import gherkin.ast.GherkinDocument;
import gherkin.events.PickleEvent;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CucumberFeature {
    private final URI uri;

    private final List<PickleEvent> pickles;

    private GherkinDocument gherkinDocument;

    private String gherkinSource;


    public CucumberFeature(GherkinDocument gherkinDocument, URI uri, String gherkinSource, List<PickleEvent> pickles) {
        this.gherkinDocument = gherkinDocument;
        this.uri = uri;
        this.gherkinSource = gherkinSource;
        this.pickles = pickles;
    }

    public List<PickleEvent> getPickles() {
        return pickles;
    }

    public String getName() {
        return gherkinDocument.getFeature().getName();
    }

    public GherkinDocument getGherkinFeature() {
        return gherkinDocument;
    }

    public URI getUri() {
        return uri;
    }

    public void sendTestSourceRead(EventBus bus) {
        bus.send(new TestSourceRead(bus.getTime(), bus.getTimeMillis(), getUri().toString(), gherkinSource));
    }

    String getSource() {
        return gherkinSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CucumberFeature that = (CucumberFeature) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    public static class CucumberFeatureUriComparator implements Comparator<CucumberFeature> {
        @Override
        public int compare(CucumberFeature a, CucumberFeature b) {
            return a.getUri().compareTo(b.getUri());
        }
    }
}
