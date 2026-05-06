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
import io.cucumber.core.gherkin.DataTableArgument;
import io.cucumber.core.gherkin.DocStringArgument;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.gherkin.Step;
import io.cucumber.core.gherkin.StepType;
import io.cucumber.plugin.event.Location;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class LoopConverter {

    public static String getDelegatePickleId(Pickle pickle) {
        if (pickle instanceof WrappedPickle) {
            return ((WrappedPickle) pickle).delegate.getId();
        }
        return pickle.getId();
    }

    public static String getDelegateStepId(Step step) {
        if (step instanceof WrappedStep) {
            return ((WrappedStep) step).delegate.getId();
        }
        return step.getId();
    }

    public static List<Pickle> transformPickleEventWithLoopTags(Pickle pickle) {
        List<Pickle> result = new ArrayList<>();
        for (String tag : pickle.getTags()) {
            if (tag.toUpperCase().matches("\\s*@LOOP.*")) {
                transformLoop(pickle, tag, result);
                break;
            }
            if (tag.toUpperCase().matches("\\s*@PROGLOOP.*")) {
                transformProgLoop(pickle, tag, result);
                break;
            }
            if (tag.toUpperCase().matches("\\s*@MULTILOOP.*")) {
                transformMultiLoop(pickle, tag, result);
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(pickle);
        }
        return result;
    }

    private static void transformLoop(Pickle pickle, String tag, List<Pickle> result) {
        String params = tag.substring(tag.lastIndexOf('(') + 1, tag.length() - 1);
        String[] parts = params.split(",");
        String listParam = parts[0];
        try {
            String value = System.getProperty(listParam, ThreadProperty.get(listParam));
            if (value == null) {
                throw new Exception("@errorMessage(Variable__" + listParam + "__is__not__defined.)");
            }
            String[] elems = value.split(",");
            String paramReplace = parts[1];
            int numElem = 0;
            for (String elem : elems) {
                result.add(generatePickle(pickle, new String[]{paramReplace}, new String[]{elem}, numElem));
                numElem++;
            }
        } catch (Exception e) {
            result.add(errorPickle(pickle, e));
        }
    }

    private static void transformProgLoop(Pickle pickle, String tag, List<Pickle> result) {
        String params = tag.substring(tag.lastIndexOf('(') + 1, tag.length() - 1);
        String[] parts = params.split(",");
        String listParam = parts[0];
        try {
            String elem = System.getProperty(listParam, ThreadProperty.get(listParam));
            if (elem == null) {
                throw new Exception("@errorMessage(Variable__" + listParam + "__is__not__defined.)");
            }
            int times = Integer.parseInt(elem);
            if (times < 1) {
                throw new Exception("@errorMessage(Variable__" + listParam + "__must__be__higher__than__0.)");
            }
            String paramReplace = parts[1];
            for (int i = 1; i <= times; i++) {
                result.add(generatePickle(pickle, new String[]{paramReplace}, new String[]{String.valueOf(i)}, i - 1));
            }
        } catch (NumberFormatException e) {
            result.add(errorPickle(pickle, new Exception("@errorMessage(Variable__" + listParam + "__is__not__an__integer.)")));
        } catch (Exception e) {
            result.add(errorPickle(pickle, e));
        }
    }

    private static void transformMultiLoop(Pickle pickle, String tag, List<Pickle> result) {
        String params = tag.substring(tag.lastIndexOf('(') + 1, tag.length() - 1);
        String[] elements = params.split(",");
        try {
            Map<String, String[]> paramMap = new HashMap<>();
            List<String> keys = new ArrayList<>();
            for (String element : elements) {
                String[] parts = element.split("=>");
                String listParam = parts[0];
                String paramName = parts[1];
                String value = System.getProperty(listParam, ThreadProperty.get(listParam));
                if (value == null) {
                    throw new Exception("@errorMessage(Variable__" + listParam + "__is__not__defined.)");
                }
                paramMap.put(paramName, value.split(","));
                keys.add(paramName);
            }

            int numScenarios = 1;
            for (String key : keys) {
                numScenarios *= paramMap.get(key).length;
            }

            List<String[]> combinations = buildCombinations(keys, paramMap, numScenarios);
            String[] keyArray = keys.toArray(new String[0]);
            int idx = 0;
            for (String[] values : combinations) {
                result.add(generatePickle(pickle, keyArray, values, idx++));
            }
        } catch (Exception e) {
            result.add(errorPickle(pickle, e));
        }
    }

    private static List<String[]> buildCombinations(List<String> keys, Map<String, String[]> paramMap, int total) {
        List<String[]> combos = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            combos.add(new String[keys.size()]);
        }
        // Row-major: first key varies slowest, last key varies fastest.
        // period[i] = product of cardinalities of keys i+1..n-1
        int[] period = new int[keys.size()];
        period[keys.size() - 1] = 1;
        for (int ki = keys.size() - 2; ki >= 0; ki--) {
            period[ki] = period[ki + 1] * paramMap.get(keys.get(ki + 1)).length;
        }
        for (int i = 0; i < total; i++) {
            for (int ki = 0; ki < keys.size(); ki++) {
                String[] vals = paramMap.get(keys.get(ki));
                combos.get(i)[ki] = vals[(i / period[ki]) % vals.length];
            }
        }
        return combos;
    }

    private static Pickle generatePickle(Pickle original, String[] keys, String[] values, int index) {
        String newName = replaceAll(original.getName(), keys, values, index);
        List<Step> newSteps = original.getSteps().stream()
                .map(step -> replaceStep(step, keys, values, index))
                .collect(Collectors.toList());
        return new WrappedPickle(original, newName, newSteps, original.getTags());
    }

    private static Pickle errorPickle(Pickle original, Exception e) {
        String message = e.getMessage();
        if (message == null) {
            message = "@errorMessage(Unknown__error.)";
        }
        if (!message.contains("@errorMessage")) {
            message = "@errorMessage(" + message.replaceAll(" ", "__") + ".)";
        }
        List<String> newTags = new ArrayList<>(original.getTags());
        newTags.add("@error");
        newTags.add(message);
        return new WrappedPickle(original, original.getName(), original.getSteps(), newTags);
    }

    private static Step replaceStep(Step step, String[] keys, String[] values, int index) {
        String newText = replaceAll(step.getText(), keys, values, index);
        Argument newArg = replaceArgument(step.getArgument(), keys, values, index);
        return new WrappedStep(step, newText, newArg);
    }

    private static Argument replaceArgument(Argument arg, String[] keys, String[] values, int index) {
        if (arg instanceof DataTableArgument) {
            DataTableArgument dt = (DataTableArgument) arg;
            List<List<String>> newCells = dt.cells().stream()
                    .map(row -> row.stream()
                            .map(cell -> replaceAll(cell, keys, values, index))
                            .collect(Collectors.toList()))
                    .collect(Collectors.toList());
            return new WrappedDataTableArgument(dt, newCells);
        }
        if (arg instanceof DocStringArgument) {
            DocStringArgument ds = (DocStringArgument) arg;
            String newContent = replaceAll(ds.getContent(), keys, values, index);
            return new WrappedDocStringArgument(ds, newContent);
        }
        return arg;
    }

    private static String replaceAll(String text, String[] keys, String[] values, int index) {
        for (int i = 0; i < keys.length; i++) {
            text = text.replaceAll("<" + keys[i] + ">", values[i])
                       .replaceAll("<" + keys[i] + "\\.id>", String.valueOf(index));
        }
        return text;
    }

    // ---- Inner wrapper classes ----

    static class WrappedPickle implements Pickle {

        private final Pickle delegate;

        private final String name;

        private final List<Step> steps;

        private final List<String> tags;

        private final String id;

        WrappedPickle(Pickle delegate, String name, List<Step> steps, List<String> tags) {
            this.delegate = delegate;
            this.name = name;
            this.steps = Collections.unmodifiableList(steps);
            this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
            this.id = UUID.randomUUID().toString();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return delegate.getUri();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getLanguage() {
            return delegate.getLanguage();
        }

        @Override
        public String getKeyword() {
            return delegate.getKeyword();
        }

        @Override
        public List<Step> getSteps() {
            return steps;
        }

        @Override
        public List<String> getTags() {
            return tags;
        }

        @Override
        public Location getLocation() {
            return delegate.getLocation();
        }

        @Override
        public Location getScenarioLocation() {
            return delegate.getScenarioLocation();
        }
    }

    static class WrappedStep implements Step {

        private final Step delegate;

        private final String text;

        private final Argument argument;

        private final String id;

        WrappedStep(Step delegate, String text, Argument argument) {
            this.delegate = delegate;
            this.text = text;
            this.argument = argument;
            this.id = UUID.randomUUID().toString();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public String getKeyword() {
            return delegate.getKeyword();
        }

        @Override
        public StepType getType() {
            return delegate.getType();
        }

        @Override
        public String getPreviousGivenWhenThenKeyword() {
            return delegate.getPreviousGivenWhenThenKeyword();
        }

        @Override
        public Argument getArgument() {
            return argument;
        }

        @Override
        public Location getLocation() {
            return delegate.getLocation();
        }

        @Override
        public int getLine() {
            return delegate.getLine();
        }
    }

    static class WrappedDataTableArgument implements DataTableArgument {

        private final DataTableArgument delegate;

        private final List<List<String>> cells;

        WrappedDataTableArgument(DataTableArgument delegate, List<List<String>> cells) {
            this.delegate = delegate;
            this.cells = cells;
        }

        @Override
        public List<List<String>> cells() {
            return cells;
        }

        @Override
        public int getLine() {
            return delegate.getLine();
        }
    }

    static class WrappedDocStringArgument implements DocStringArgument {

        private final DocStringArgument delegate;

        private final String content;

        WrappedDocStringArgument(DocStringArgument delegate, String content) {
            this.delegate = delegate;
            this.content = content;
        }

        @Override
        public String getContent() {
            return content;
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public String getMediaType() {
            return delegate.getMediaType();
        }

        @Override
        public int getLine() {
            return delegate.getLine();
        }
    }
}
