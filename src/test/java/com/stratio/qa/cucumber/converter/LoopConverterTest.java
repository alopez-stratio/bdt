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
package com.stratio.qa.cucumber.converter;

import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.core.gherkin.Argument;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.gherkin.Step;
import io.cucumber.core.gherkin.StepType;
import io.cucumber.plugin.event.Location;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class LoopConverterTest {

    @AfterMethod
    public void cleanup() {
        System.clearProperty("LOOP_VAR");
        System.clearProperty("PROG_VAR");
        ThreadProperty.remove("LOOP_VAR");
        ThreadProperty.remove("PROG_VAR");
        ThreadProperty.remove("LOCAL_LIST1");
        ThreadProperty.remove("LOCAL_LIST2");
    }

    private Pickle buildPickle(String tagName, String stepText) {
        return buildPickle(tagName, stepText, "scenario <PARAM>");
    }

    private Pickle buildPickle(String tagName, String stepText, String scenarioName) {
        List<String> tags = new ArrayList<>(Collections.singletonList(tagName));
        Step step = new TestStep(stepText);
        List<Step> steps = new ArrayList<>(Collections.singletonList(step));
        return new TestPickle(scenarioName, tags, steps);
    }

    private List<String> tagNames(Pickle p) {
        return p.getTags();
    }

    // --- @loop ---

    @Test
    public void testLoopThreadProperty() {
        ThreadProperty.set("LOOP_VAR", "a,b,c");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@loop(LOOP_VAR,PARAM)", "I run <PARAM> locally"));
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSteps().get(0).getText()).isEqualTo("I run a locally");
        assertThat(result.get(1).getSteps().get(0).getText()).isEqualTo("I run b locally");
        assertThat(result.get(2).getSteps().get(0).getText()).isEqualTo("I run c locally");
    }

    @Test
    public void testLoopSystemProperty() {
        System.setProperty("LOOP_VAR", "x,y");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@loop(LOOP_VAR,PARAM)", "I run <PARAM> locally"));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSteps().get(0).getText()).isEqualTo("I run x locally");
        assertThat(result.get(1).getSteps().get(0).getText()).isEqualTo("I run y locally");
    }

    @Test
    public void testLoopUndefinedVar() {
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@loop(LOOP_VAR,PARAM)", "I run <PARAM> locally"));
        assertThat(result).hasSize(1);
        assertThat(tagNames(result.get(0))).contains("@error");
        assertThat(tagNames(result.get(0)).stream().anyMatch(n -> n.contains("LOOP_VAR__is__not__defined"))).isTrue();
    }

    @Test
    public void testLoopIdReplacement() {
        ThreadProperty.set("LOOP_VAR", "alpha,beta");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@loop(LOOP_VAR,PARAM)", "step <PARAM> id <PARAM.id>"));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSteps().get(0).getText()).isEqualTo("step alpha id 0");
        assertThat(result.get(1).getSteps().get(0).getText()).isEqualTo("step beta id 1");
    }

    @Test
    public void testLoopScenarioNameReplacement() {
        ThreadProperty.set("LOOP_VAR", "foo,bar");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@loop(LOOP_VAR,PARAM)", "step"));
        assertThat(result.get(0).getName()).isEqualTo("scenario foo");
        assertThat(result.get(1).getName()).isEqualTo("scenario bar");
    }

    // --- @progloop ---

    @Test
    public void testProgLoopNormal() {
        ThreadProperty.set("PROG_VAR", "3");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@progloop(PROG_VAR,ITER)", "iteration <ITER>"));
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSteps().get(0).getText()).isEqualTo("iteration 1");
        assertThat(result.get(1).getSteps().get(0).getText()).isEqualTo("iteration 2");
        assertThat(result.get(2).getSteps().get(0).getText()).isEqualTo("iteration 3");
    }

    @Test
    public void testProgLoopUndefinedVar() {
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@progloop(PROG_VAR,ITER)", "iteration <ITER>"));
        assertThat(result).hasSize(1);
        assertThat(tagNames(result.get(0))).contains("@error");
        assertThat(tagNames(result.get(0)).stream().anyMatch(n -> n.contains("PROG_VAR__is__not__defined"))).isTrue();
    }

    @Test
    public void testProgLoopNonInteger() {
        ThreadProperty.set("PROG_VAR", "notanumber");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@progloop(PROG_VAR,ITER)", "iteration <ITER>"));
        assertThat(result).hasSize(1);
        assertThat(tagNames(result.get(0))).contains("@error");
        assertThat(tagNames(result.get(0)).stream().anyMatch(n -> n.contains("not__an__integer"))).isTrue();
    }

    @Test
    public void testProgLoopZero() {
        ThreadProperty.set("PROG_VAR", "0");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(
                buildPickle("@progloop(PROG_VAR,ITER)", "iteration <ITER>"));
        assertThat(result).hasSize(1);
        assertThat(tagNames(result.get(0))).contains("@error");
        assertThat(tagNames(result.get(0)).stream().anyMatch(n -> n.contains("must__be__higher__than__0"))).isTrue();
    }

    // --- @multiloop ---

    @Test
    public void testMultiLoopTwoParams() {
        ThreadProperty.set("LOCAL_LIST1", "1,2");
        ThreadProperty.set("LOCAL_LIST2", "a,b");
        Pickle pickle = buildPickle("@multiloop(LOCAL_LIST1=>P1,LOCAL_LIST2=>P2)", "<P1>-<P2>", "<P1>-<P2>");

        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(pickle);
        assertThat(result).hasSize(4);
        List<String> stepTexts = result.stream()
                .map(r -> r.getSteps().get(0).getText())
                .collect(Collectors.toList());
        assertThat(stepTexts).containsExactlyInAnyOrder("1-a", "1-b", "2-a", "2-b");
    }

    @Test
    public void testMultiLoopUndefinedVar() {
        ThreadProperty.set("LOCAL_LIST2", "a,b");
        // LOCAL_LIST1 not set
        Pickle pickle = buildPickle("@multiloop(LOCAL_LIST1=>P1,LOCAL_LIST2=>P2)", "<P1>-<P2>", "<P1>-<P2>");

        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(pickle);
        assertThat(result).hasSize(1);
        assertThat(tagNames(result.get(0))).contains("@error");
    }

    // --- no loop tags ---

    @Test
    public void testNoLoopTags() {
        Pickle pickle = buildPickle("@runOnEnv(SOME_VAR)", "I run something", "regular scenario");
        List<Pickle> result = LoopConverter.transformPickleEventWithLoopTags(pickle);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isSameAs(pickle);
    }

    // ---- minimal test-only Pickle / Step impls ----

    static class TestPickle implements Pickle {
        private final String name;
        private final List<String> tags;
        private final List<Step> steps;
        private final String id = UUID.randomUUID().toString();
        private static final URI TEST_URI = URI.create("file:test.feature");

        TestPickle(String name, List<String> tags, List<Step> steps) {
            this.name = name;
            this.tags = tags;
            this.steps = steps;
        }

        @Override public String getId() { return id; }
        @Override public URI getUri() { return TEST_URI; }
        @Override public String getName() { return name; }
        @Override public String getLanguage() { return "en"; }
        @Override public String getKeyword() { return "Scenario"; }
        @Override public List<Step> getSteps() { return steps; }
        @Override public List<String> getTags() { return tags; }
        @Override public Location getLocation() { return new Location(1, 0); }
        @Override public Location getScenarioLocation() { return new Location(1, 0); }
    }

    static class TestStep implements Step {
        private final String text;
        private final String id = UUID.randomUUID().toString();

        TestStep(String text) { this.text = text; }

        @Override public String getId() { return id; }
        @Override public String getText() { return text; }
        @Override public String getKeyword() { return "Given"; }
        @Override public StepType getType() { return StepType.GIVEN; }
        @Override public String getPreviousGivenWhenThenKeyword() { return "Given"; }
        @Override public Argument getArgument() { return null; }
        @Override public Location getLocation() { return new Location(2, 0); }
        @Override public int getLine() { return 2; }
    }
}
