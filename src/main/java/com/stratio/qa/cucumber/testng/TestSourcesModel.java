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

package com.stratio.qa.cucumber.testng;

import io.cucumber.plugin.event.TestSourceRead;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TestSourcesModel {

    private final Map<String, TestSourceRead> pathToReadEventMap = new HashMap<>();

    private final Map<String, String> pathToFeatureNameMap = new HashMap<>();

    private final Map<String, Set<Integer>> pathToBackgroundLinesMap = new HashMap<>();

    private final Map<String, Map<Integer, String>> replacedStepTextsMap = new HashMap<>();

    // pickleId -> scenario name with ${}/!{}/@{} already resolved. Written by
    // ReplacementAspect while the scenario runs, read by CucumberRunner to re-emit
    // the Pickle message so the report shows the resolved name. Concurrent because
    // this model is a process-wide singleton and scenarios may run in parallel.
    private final Map<String, String> replacedScenarioNamesMap = new ConcurrentHashMap<>();

    public void addTestSourceReadEvent(String path, TestSourceRead event) {
        pathToReadEventMap.put(path, event);
    }

    public String getFeatureName(String uri) {
        if (!pathToFeatureNameMap.containsKey(uri)) {
            parseSource(uri);
        }
        return pathToFeatureNameMap.getOrDefault(uri, "");
    }

    public boolean isBackgroundStep(String path, int stepLine) {
        if (!pathToBackgroundLinesMap.containsKey(path)) {
            parseSource(path);
        }
        Set<Integer> bgLines = pathToBackgroundLinesMap.get(path);
        return bgLines != null && bgLines.contains(stepLine);
    }

    public void addReplacedStepText(String feature, int stepLine, String replacedText) {
        replacedStepTextsMap.computeIfAbsent(feature, k -> new HashMap<>()).put(stepLine, replacedText);
    }

    public String getReplacedStepText(String feature, int stepLine) {
        Map<Integer, String> m = replacedStepTextsMap.get(feature);
        return m != null ? m.get(stepLine) : null;
    }

    public void addReplacedScenarioName(String pickleId, String replacedName) {
        replacedScenarioNamesMap.put(pickleId, replacedName);
    }

    public String getReplacedScenarioName(String pickleId) {
        return replacedScenarioNamesMap.get(pickleId);
    }

    private void parseSource(String path) {
        TestSourceRead event = pathToReadEventMap.get(path);
        if (event == null) {
            pathToFeatureNameMap.put(path, "");
            pathToBackgroundLinesMap.put(path, Collections.emptySet());
            return;
        }

        String featureName = "";
        Set<Integer> backgroundLines = new HashSet<>();
        boolean inBackground = false;

        String[] lines = event.getSource().split("\n");
        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;
            String trimmed = lines[i].trim();

            if (trimmed.startsWith("Feature:")) {
                featureName = trimmed.substring("Feature:".length()).trim();
            } else if (trimmed.startsWith("Background:")) {
                inBackground = true;
                backgroundLines.add(lineNum);
            } else if (inBackground) {
                if (trimmed.startsWith("Scenario") || trimmed.startsWith("Rule:") || trimmed.startsWith("Examples:")) {
                    inBackground = false;
                } else if (!trimmed.isEmpty()) {
                    backgroundLines.add(lineNum);
                }
            }
        }

        pathToFeatureNameMap.put(path, featureName);
        pathToBackgroundLinesMap.put(path, backgroundLines);
    }
}
