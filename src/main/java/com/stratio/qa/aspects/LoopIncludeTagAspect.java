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

package com.stratio.qa.aspects;

import com.stratio.qa.exceptions.IncludeException;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// TODO: rename to FeaturePreprocessor and remove @Aspect once all consumer repos drop this class from their aop.xml.
// @Aspect is kept solely for backward compat: external repos still list this class in their aop.xml and AspectJ
// errors on registration if the type is not an aspect. No pointcuts are defined — this class does no weaving.
@Aspect
public class LoopIncludeTagAspect {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    public void processBackground(List<String> lines) {
        String listParams;
        for (int s = 0; s < lines.size(); s++) {
            if (lines.get(s).toUpperCase().matches("\\s*@BACKGROUND.*")) {
                listParams = lines.get(s).substring((lines.get(s).lastIndexOf("(") + 1), (lines.get(s).length()) - 1);
                if (System.getProperty(listParams) != null) {
                    lines.remove(s);
                    while (!lines.get(s).toUpperCase().contains("/BACKGROUND")) {
                        s++;
                    }
                    lines.remove(s--);
                } else {
                    lines.remove(s);
                    while (!lines.get(s).toUpperCase().contains("SCENARIO") && !lines.get(s).matches("^\\s[^\\n|\\r]@.*") && !lines.get(s).toUpperCase().contains("/BACKGROUND")) {
                        lines.remove(s);
                    }
                    if (lines.get(s).toUpperCase().contains("@/BACKGROUND")) {
                        lines.remove(s--);
                    }
                }
            }
        }
    }

    public String parseLines(List<String> lines, String path) throws IncludeException {
        String featureName;
        String scenarioName;
        String[] params;
        boolean marked = false;
        String nwsource = "";


        for (int lineOriginalFeature = 0; lineOriginalFeature < lines.size(); lineOriginalFeature++) {
            if (lines.get(lineOriginalFeature).matches("\\s*@include.*")) {
                String lineToinclude = "";
                for (int linesWithInclude = lineOriginalFeature; linesWithInclude < lines.size() && lines.get(linesWithInclude).contains("@include"); linesWithInclude++) {
                    featureName = getFeatureName(lines.get(linesWithInclude));
                    scenarioName = getScenName(lines.get(linesWithInclude));
                    params = getParams(lines.get(linesWithInclude));
                    lines.set(linesWithInclude, "");
                    lineToinclude += featureStepConverter(path + featureName, scenarioName, params);
                }

                //TODO: Simplify code
                StringBuilder sb = new StringBuilder();
                for (int lineAfterInclude = lineOriginalFeature; lineAfterInclude < lines.size(); lineAfterInclude++) {
                    if (lines.get(lineAfterInclude).toUpperCase().contains("FEATURE:") && !lines.get(lineAfterInclude).toUpperCase().contains("@INCLUDE")) {
                        for (int indexForBackground = lineAfterInclude; indexForBackground < lines.size(); indexForBackground++) {
                            if (lines.get(lineAfterInclude).toUpperCase().contains("BACKGROUND:") && !marked) {
                                lines.set(lineOriginalFeature, lines.get(lineAfterInclude));
                                lines.set(lineAfterInclude, lines.get(indexForBackground));
                                lines.set(indexForBackground, lineToinclude);
                                marked = true;
                                lineToinclude = "";
                                break;
                            }
                        }
                        if (!marked) {
                            lines.set(lineOriginalFeature, lines.get(lineAfterInclude));
                            lines.set(lineAfterInclude, "\nBackground:\n" + lineToinclude);
                            lineToinclude = "";
                            break;
                        }
                    }
                    if ((lines.get(lineAfterInclude).toUpperCase().contains("SCENARIO:") || lines.get(lineAfterInclude).toUpperCase().contains("OUTLINE:")) && !lines.get(lineAfterInclude).toUpperCase().contains("@INCLUDE")) {
                        lines.set(lineOriginalFeature, sb + lines.get(lineAfterInclude));
                        lines.set(lineAfterInclude, lineToinclude);
                        lineToinclude = lines.get(lineOriginalFeature);
                        for (int lineAux = lineOriginalFeature + 1; lineAux < lineAfterInclude; lineAux++) {
                            if (lines.get(lineAux).matches("\\s*@[^{].+")) {
                                lines.set(lineAux - 1, lines.get(lineAux));
                                lines.set(lineAux, lineToinclude);
                            }
                        }
                        break;
                    }
                    if (!lines.get(lineAfterInclude).isEmpty()) {
                        sb.append(lines.get(lineAfterInclude)).append("\n");
                        lines.set(lineAfterInclude, "");
                    }
                }

            }
            nwsource += lines.get(lineOriginalFeature) + "\n";
        }
        return nwsource;
    }

    /**
     * @param s A string with the tag line that will be filtered and trimmed to get exactly the name of the feature
     * @return String Feature name
     */
    public String getFeatureName(String s) {

        String feature = s.substring((s.lastIndexOf("feature:") + "feature:".length()));
        feature = feature.substring(0, feature.indexOf(","));

        return feature.trim();
    }

    public String getScenName(String s) {

        String scenName = s.substring((s.lastIndexOf("scenario:") + "scenario:".length()));
        if (s.contains("params:")) {
            scenName = scenName.split("(,\\s?params)")[0];
        } else {
            scenName = scenName.substring(0, scenName.lastIndexOf(")"));
        }

        return scenName.trim();
    }

    public String[] getParams(String s) {
        String[] vals = null;
        if (s.contains("params:")) {
            String[] pairs = s.substring((s.lastIndexOf("[") + 1), (s.length()) - 2).split(",");
            vals = new String[(pairs.length) * 2];
            int index = 0;
            for (int iterator = 0; iterator < pairs.length; iterator++) {
                vals[index] = "<" + pairs[iterator].split(":")[0].trim() + ">";  //key
                index++;
                vals[index] = pairs[iterator].split(":")[1].trim();           //value
                index++;
            }
        }
        return vals;
    }

    public String doReplaceKeys(String parsedFeature, String[] params) throws IncludeException {
        for (int i = 0; i < params.length; i++) {
            //Scape '$' because it is a especial character
            if (params[i + 1].startsWith("$")) {
                params[i + 1] = "\\" + params[i + 1];
            }
            parsedFeature = parsedFeature.replaceAll(params[i], params[i + 1]);
            i++;
        }
        if (parsedFeature.contains("<")) {
            throw new IncludeException("-> Error while parsing keys, check your params");
        }
        return parsedFeature;
    }

    public String featureStepConverter(String feature, String scenarioName, String[] params) throws IncludeException {
        // Resolve the reference before extracting anything. @include matches by scenario name, so a name that
        // is missing, duplicated or commented out has to be reported as such: otherwise the include either
        // fails with no hint of what the feature does declare, or copies a body nobody asked for.
        checkScenarioIsResolvable(feature, scenarioName);

        boolean scenarioexists = false;
        BufferedReader bufferedFeature = null;
        String parsedFeature = "";
        String sCurrentLine;


        try {

            bufferedFeature = new BufferedReader(new FileReader(feature));
            while ((sCurrentLine = bufferedFeature.readLine()) != null) {
                String[] scenarioParts = sCurrentLine.split("(Scenario:|Scenario Outline:)");
                // Only the first match is copied. When the name is declared more than once the loop used to
                // append every body one after another, which silently produced a scenario nobody wrote.
                if (!scenarioexists && scenarioParts.length > 1 && scenarioParts[1].trim().equals(scenarioName)) {
                    scenarioexists = true;
                    if (sCurrentLine.trim().toUpperCase().startsWith("SCENARIO OUTLINE:") && params == null) {
                        throw new IncludeException("->  Parameters were not given for this scenario outline.");
                    } else if (sCurrentLine.trim().toUpperCase().startsWith("SCENARIO OUTLINE:")) {
                        BufferedReader auxBufferedFeature = bufferedFeature;
                        String sParamline;
                        while ((sParamline = auxBufferedFeature.readLine()) != null && !sParamline.toUpperCase().contains("SCENARIO")) {
                            if (sParamline.contains("|")) {
                                if (!checkParams(sParamline, params)) {
                                    throw new IncludeException("-> Wrong number of parameters.");
                                }
                            } else if (!sParamline.toUpperCase().contains("EXAMPLES:")) {
                                parsedFeature = parsedFeature + sParamline + "\n";
                            }
                        }
                    } else if (sCurrentLine.trim().toUpperCase().startsWith("SCENARIO:")) {
                        while ((sCurrentLine = bufferedFeature.readLine()) != null && !sCurrentLine.trim().toUpperCase().startsWith("SCENARIO") && !sCurrentLine.toUpperCase().contains("EXAMPLES:") && !sCurrentLine.matches("\\s*@[^{].+")) {
                            parsedFeature = parsedFeature + sCurrentLine + "\n";
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("-> Feature file were not found: " + feature);
            throw new IncludeException("-> Feature file were not found: " + feature);
        } catch (IOException e) {
            logger.warn("-> An I/O error appeared [featureStepConverter].");
            throw new IncludeException("-> An I/O error appeared.");
        } finally {
            try {
                if (bufferedFeature != null) {
                    bufferedFeature.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        if (params != null) {
            parsedFeature = doReplaceKeys(parsedFeature, params);
        }

        return parsedFeature;

    }

    /** Names that the error message lists before giving up, so a big feature does not flood the log. */
    private static final int NAMES_IN_ERROR = 15;

    /** Above this edit distance two names are too far apart to be worth suggesting as a typo. */
    private static final int SUGGESTION_MAX_DISTANCE = 8;

    /**
     * Says what is wrong with the reference before any body is extracted.
     *
     * A name that is not there is an error, as it already was, but the message now names the file and lists
     * what it does declare. A name that is declared more than once, or only inside a comment, keeps resolving
     * the way it does today and is logged as a warning: those two cases exist in consumer repositories, so
     * turning them into errors here would break builds that this change has no business breaking. What does
     * change is that a duplicated name no longer copies every body it matches, only the first one.
     */
    private void checkScenarioIsResolvable(String feature, String scenarioName) throws IncludeException {
        List<String> declared = new ArrayList<>();
        List<String> commentedOut = new ArrayList<>();
        int matches = 0;

        for (String line : readFeature(feature)) {
            String name = scenarioNameOf(line);
            if (name == null) {
                continue;
            }
            if (isCommentedOut(line)) {
                commentedOut.add(name);
                continue;
            }
            declared.add(name);
            if (name.equals(scenarioName)) {
                matches++;
            }
        }

        if (matches > 1) {
            logger.warn("-> Scenario name is not unique at the given feature: '{}' is declared {} times at {}."
                    + " @include resolves by name and cannot tell the copies apart, so only the first one is"
                    + " copied. Rename them or reference a name that is unique.", scenarioName, matches, feature);
            return;
        }

        if (matches == 1) {
            return;
        }

        if (commentedOut.contains(scenarioName)) {
            logger.warn("-> Scenario is commented out at the given feature: '{}' at {}. @include still resolves"
                    + " it, so the including feature gets nothing but the commented lines.", scenarioName, feature);
            return;
        }

        StringBuilder message = new StringBuilder("-> Scenario not present at the given feature: '")
                .append(scenarioName).append("' at ").append(feature).append(".");
        String closest = closestName(scenarioName, declared);
        if (closest != null) {
            message.append(" Did you mean '").append(closest).append("'?");
        }
        message.append(" Scenarios declared there: ").append(nameList(declared));
        logger.warn(message.toString());
        throw new IncludeException(message.toString());
    }

    private List<String> readFeature(String feature) throws IncludeException {
        try {
            return Files.readAllLines(Paths.get(feature), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            logger.warn("-> Feature file were not found: " + feature);
            throw new IncludeException("-> Feature file were not found: " + feature);
        } catch (IOException e) {
            logger.warn("-> An I/O error appeared [checkScenarioIsResolvable].");
            throw new IncludeException("-> An I/O error appeared.");
        }
    }

    /** The scenario name a line declares, or null when the line does not declare one. */
    String scenarioNameOf(String line) {
        String[] parts = line.split("(Scenario:|Scenario Outline:)");
        return parts.length > 1 ? parts[1].trim() : null;
    }

    boolean isCommentedOut(String line) {
        return line.trim().startsWith("#");
    }

    private String nameList(List<String> declared) {
        if (declared.isEmpty()) {
            return "none.";
        }
        List<String> distinct = declared.stream().distinct().collect(Collectors.toList());
        String listed = distinct.stream().limit(NAMES_IN_ERROR).map(n -> "'" + n + "'").collect(Collectors.joining(", "));
        return distinct.size() > NAMES_IN_ERROR
                ? listed + " and " + (distinct.size() - NAMES_IN_ERROR) + " more."
                : listed + ".";
    }

    /** The declared name closest to the requested one, or null when none is close enough to be a typo. */
    String closestName(String scenarioName, List<String> declared) {
        String closest = null;
        int best = Integer.MAX_VALUE;
        for (String candidate : declared) {
            int distance = editDistance(scenarioName, candidate);
            if (distance < best) {
                best = distance;
                closest = candidate;
            }
        }
        return best <= SUGGESTION_MAX_DISTANCE ? closest : null;
    }

    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    public boolean checkParams(String sCurrentLine, String[] params) {
        int paramcounter = 0;
        boolean checker = false;
        if (sCurrentLine.contains("|")) {

            for (int paramLineIndex = 0; paramLineIndex < sCurrentLine.length(); paramLineIndex++) {
                if (sCurrentLine.charAt(paramLineIndex) == '|') {
                    paramcounter++;
                }
                checker = true;

            }

            if ((params != null) && (paramcounter - 1) != (params.length / 2)) {
                checker = false;
            }
        }

        return checker;
    }
}
