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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;


public class IncludeTagAspectTest {
    public LoopIncludeTagAspect inctag = new LoopIncludeTagAspect();

    @Test
    public void testGetFeature() {
        assertThat("test.feature").as("Test feature name is extracted correctly").isEqualTo(inctag.getFeatureName("@include(feature: test.feature,scenario: To copy)"));
    }

    @Test
    public void testGetScenario() {
        assertThat("To copy").as("Test scenario name is extracted correctly").isEqualTo(inctag.getScenName("@include(feature: test.feature,scenario: To copy)"));
    }

    @Test
    public void testGetScenarioParams() {
        assertThat("To copy").as("Test scenario name is extracted correctly").isEqualTo(inctag.getScenName("@include(feature: test.feature,scenario: To copy,params:param1=1)"));
    }

    @Test
    public void testGetParams() {
        assertThat(4).as("Test that the number of keys and values are correctly calculated for params").isEqualTo(inctag.getParams("@include(feature: test.feature,scenario: To copy,params: [time1:9, time2:9])").length);
    }

    @Test
    public void testDoReplaceKeys() throws IncludeException {
        String keysNotReplaced = "Given that <time1> is not equal to <time2> into a step";
        String[] keys = {"<time1>", "9", "<time2>", "8"};
        assertThat("Given that 9 is not equal to 8 into a step").as("Test that keys are correctly replaced at scenario outlines").isEqualTo(inctag.doReplaceKeys(keysNotReplaced, keys));
    }

    @Test
    public void testDoReplaceKeysException() {
        String keysNotReplaced = "Given that <time1> is not equal to <time3> into a step";
        String[] keys = {"<time1>", "9", "<time2>", "8"};
        assertThatExceptionOfType(IncludeException.class).isThrownBy(() -> inctag.doReplaceKeys(keysNotReplaced, keys));
    }

    @Test
    public void testCheckParams() throws IncludeException {
        String lineOfParams = "| hey | ho |";
        String[] keys = {"<time1>", "9", "<time2>", "8"};
        String[] tonsOfKeys = {"<time1>", "9", "<time2>", "23", "33", "32", "10"};
        assertThat(inctag.checkParams(lineOfParams, keys)).as("Test that include parameters match the number of them at the scenario outline included").isTrue();
        assertThat(inctag.checkParams(lineOfParams, tonsOfKeys)).as("Test that include parameters match the number of them at the scenario outline included").isFalse();
    }

    @Test
    public void testTagIterationSkip() throws Exception {
        String path = "";
        List<String> lines = new ArrayList<>();
        lines.add("@include(testCheckParams)");

        assertThatExceptionOfType(Exception.class).isThrownBy(() -> inctag.parseLines(lines, path));
    }

    @Test
    public void testParseLines() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                 "@include(feature:logger.feature,scenario:Some simple request)",
                 "Scenario: Scenario with include",
                 "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testParseLinesMultipleIncludes() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                 "@include(feature:logger.feature,scenario:Some simple request)",
                 "@include(feature:logger.feature,scenario:Some simple request)",
                 "Scenario: Scenario with include",
                 "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to first include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testParseLinesParams() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                        "@include(feature:includeTemplate.feature,scenario:includeAspect with params, params:[paramtest:${pepe}])",
                        "Scenario: Scenario with include",
                        "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to first include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testIncludeWithRunOnEnv() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                 "@include(feature:runOnEnvTag.feature,scenario:RunOnEnv with param defined.)",
                 "Scenario: Scenario with include",
                 "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines.get(2)).as("Test that array doesn't contains @runOnEnv tag").doesNotContain("@runOnEnv");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testIncludeBeforeOtherTags() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                 "@include(feature:logger.feature,scenario:Some simple request)",
                 "@runOnEnv(TEST)",
                 "Scenario: Scenario with include",
                 "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that runOnEnv tag was moved before Scenario").isEqualTo("@runOnEnv(TEST)\nScenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testIncludeBeforeOtherTagsMultiple() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                 "@include(feature:logger.feature,scenario:Some simple request)",
                 "@runOnEnv(TEST)",
                 "@ignore @manual",
                 "Scenario: Scenario with include",
                 "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that runOnEnv tag and ignore tag was moved before Scenario and scenario was moved after other scenario tags").isEqualTo("@runOnEnv(TEST)\n@ignore @manual\nScenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testParseLinesScenarioWithBrackets() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                        "@include(feature:includeTemplate.feature,scenario:testInclude (test) blablabla)",
                        "Scenario: Scenario with include",
                        "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to first include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testParseLinesScenarioWithBracketsAndParams() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                        "@include(feature:includeTemplate.feature,scenario:testInclude (test) blablabla withParams, params:[paramtest:2])",
                        "Scenario: Scenario with include",
                        "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to first include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testParseLinesScenarioWithBracketsAndParamsdos() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                        "@include(feature:includeTemplate.feature,scenario:prueba test include con comas y params, dos, params:[paramtest:2])",
                        "Scenario: Scenario with include",
                        "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to first include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    @Test
    public void testParseLinesScenarioWithBracketsAndMoreParamsdos() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                        "@include(feature:includeTemplate.feature,scenario:prueba test include con comas y params, dos, params:[paramtest:2,paramtest2:3])",
                        "Scenario: Scenario with include",
                        "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(1)).as("Test that Scenario line was moved to first include line").isEqualTo("Scenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    // -------------------------------------------------------------------------
    // Bug 3: plain Scenario: whose NAME contains "outline" was misdetected as
    // Scenario Outline: because the check used contains("OUTLINE") on the full
    // header line instead of checking only the keyword prefix.
    // Fix: use startsWith("SCENARIO OUTLINE:") on the trimmed line.
    // -------------------------------------------------------------------------

    @Test
    public void testFeatureStepConverterPlainScenarioNameContainsOutline() throws IncludeException {
        // [005] name has "outline" in it — must extract steps, not throw.
        String extracted = inctag.featureStepConverter(
                "src/test/resources/features/includeOutlineTest.feature",
                "[005] Setup for outline context verification",
                null);
        assertThat(extracted)
                .as("Plain Scenario: with 'outline' in name must not be treated as Scenario Outline:")
                .contains("outline_done");
    }

    // -------------------------------------------------------------------------
    // Bug 2: split("(Scenario:|Scenario Outline:)")[1] without a bounds check
    // crashed with ArrayIndexOutOfBoundsException on any line that contains the
    // word "Scenario" without the keyword+colon pattern (e.g. a Feature title
    // whose text includes "Scenario" as a plain word, not as a Gherkin keyword).
    // Fix: guard with scenarioParts.length > 1 before accessing index 1.
    // -------------------------------------------------------------------------

    @Test
    public void testFeatureStepConverterDoesNotCrashOnScenarioWordInNonKeywordLine() throws IncludeException {
        // Feature title "Scenario keyword appears in Feature title without colon"
        // contains "Scenario" but not "Scenario:" → split returns length 1 → AIOOB without fix.
        String extracted = inctag.featureStepConverter(
                "src/test/resources/features/includeOutlineBug2.feature",
                "[001] Plain scenario in bug2 fixture",
                null);
        assertThat(extracted)
                .as("Must not crash when 'Scenario' appears in Feature title without keyword colon")
                .contains("bug2_done");
    }

    // -------------------------------------------------------------------------
    // Bug 1: featureStepConverter() stop condition used contains("SCENARIO:")
    // which does NOT match "SCENARIO OUTLINE:", so the loop overread into the
    // next Scenario Outline and captured its header + <param> steps.
    // Fix: use trim().toUpperCase().startsWith("SCENARIO") as stop condition.
    // -------------------------------------------------------------------------

    @Test
    public void testFeatureStepConverterDoesNotOverreadIntoFollowingScenarioOutline() throws IncludeException {
        String extracted = inctag.featureStepConverter(
                "src/test/resources/features/includeOutlineTest.feature",
                "[001] Pre-include plain scenario",
                null);
        assertThat(extracted)
                .as("Must extract [001]'s own step")
                .contains("setup_done");
        assertThat(extracted)
                .as("Must NOT overread into the following Scenario Outline: header")
                .doesNotContain("Scenario Outline:");
        assertThat(extracted)
                .as("Must NOT capture <testParam> from the following outline")
                .doesNotContain("<testParam>");
        assertThat(extracted)
                .as("Must NOT capture steps from the following outline")
                .doesNotContain("testParamResolved");
    }

    @Test
    public void testParseLinesPreservesScenarioOutlineBeforeInclude() throws IncludeException {
        // Integration path: parseLines() calls featureStepConverter() internally.
        // The Scenario Outline that precedes the @include target must survive intact.
        String[] originalFeature = new String[]{
                "Feature: Include Outline Regression Test",
                "Scenario: [001] Pre-include plain scenario",
                "  Given I save 'setup_done' in variable 'setupDoneVar'",
                "",
                "Scenario Outline: [002] Outline immediately after plain scenario",
                "  Given I save '<testParam>' in variable 'testParamResolved'",
                "  Then '!{testParamResolved}' is '<expected>'",
                "  Examples:",
                "    | testParam        | expected         |",
                "    | value_alpha      | value_alpha      |",
                "    | value_beta       | value_beta       |",
                "",
                "@include(feature:includeOutlineTest.feature,scenario:[001] Pre-include plain scenario)",
                "Scenario: [003] Include of [001]"
        };
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        String nwsource = inctag.parseLines(lines, "src/test/resources/features/");

        assertThat(nwsource).as("Assembled source must contain Scenario Outline [002] header")
                .contains("Scenario Outline: [002]");
        assertThat(nwsource).as("Assembled source must contain <testParam> placeholder")
                .contains("<testParam>");
        assertThat(nwsource).as("Assembled source must contain Examples: table")
                .contains("Examples:");
        assertThat(nwsource).as("Assembled source must contain Examples data rows")
                .contains("value_alpha");

        long outlineHeaderCount = nwsource.lines()
                .filter(l -> l.trim().startsWith("Scenario Outline:"))
                .count();
        assertThat(outlineHeaderCount)
                .as("Only one Scenario Outline: [002] must exist — @include must not inject a duplicate")
                .isEqualTo(1L);
    }

    @AfterMethod
    public void cleanupSystemProperties() {
        System.clearProperty("BGVAR");
        System.clearProperty("BGVAR2");
    }

    // --- @background ---

    @Test
    public void testProcessBackgroundVarDefined() {
        System.setProperty("BGVAR", "true");
        List<String> lines = new ArrayList<>(Arrays.asList(
                "  @BACKGROUND(BGVAR)",
                "    Given I run some step",
                "  @/BACKGROUND",
                "  Scenario: test"));
        inctag.processBackground(lines);
        assertThat(lines).doesNotContain("  @BACKGROUND(BGVAR)");
        assertThat(lines).doesNotContain("  @/BACKGROUND");
        assertThat(lines).contains("    Given I run some step");
    }

    @Test
    public void testProcessBackgroundVarNotDefined() {
        List<String> lines = new ArrayList<>(Arrays.asList(
                "  @BACKGROUND(BGVAR)",
                "    Given I run some step",
                "  @/BACKGROUND",
                "  Scenario: test"));
        inctag.processBackground(lines);
        assertThat(lines).doesNotContain("  @BACKGROUND(BGVAR)");
        assertThat(lines).doesNotContain("  @/BACKGROUND");
        assertThat(lines).doesNotContain("    Given I run some step");
        assertThat(lines).contains("  Scenario: test");
    }

    @Test
    public void testProcessBackgroundMultipleBlocks() {
        System.setProperty("BGVAR", "true");
        List<String> lines = new ArrayList<>(Arrays.asList(
                "  @BACKGROUND(BGVAR)",
                "    Given keep this",
                "  @/BACKGROUND",
                "  @BACKGROUND(BGVAR2)",
                "    Given remove this",
                "  @/BACKGROUND",
                "    Given always step"));
        inctag.processBackground(lines);
        assertThat(lines).contains("    Given keep this");
        assertThat(lines).doesNotContain("    Given remove this");
        assertThat(lines).contains("    Given always step");
        assertThat(lines).doesNotContain("  @BACKGROUND(BGVAR)");
        assertThat(lines).doesNotContain("  @BACKGROUND(BGVAR2)");
        assertThat(lines).doesNotContain("  @/BACKGROUND");
    }

    @Test
    public void testProcessBackgroundNoTags() {
        List<String> lines = new ArrayList<>(Arrays.asList(
                "Feature: Test",
                "  Scenario: test",
                "    Given some step"));
        List<String> original = new ArrayList<>(lines);
        inctag.processBackground(lines);
        assertThat(lines).isEqualTo(original);
    }

    @Test
    public void testIncludeKeos() throws IncludeException {
        String[] originalFeature = new String[]
                {"Feature: Test",
                        "@keos",
                        "@include(feature:includeTemplate.feature,scenario:prueba test include con comas y params, dos, params:[paramtest:2,paramtest2:3])",
                        "@include(feature:includeTemplate.feature,scenario:testInclude (test) blablabla withParams, params:[paramtest:2])",
                        "@test",
                        "Scenario: Scenario with include",
                        "Given I run 'echo 1' locally"};
        List<String> lines = new ArrayList<>(Arrays.asList(originalFeature));
        inctag.parseLines(lines, "src/test/resources/features/");
        assertThat(lines.get(2)).as("Test that Scenario line was moved to first include line with tags").isEqualTo("@test\nScenario: Scenario with include");
        assertThat(lines).as("Test that array doesn't contains @include tag").doesNotContain("@include");
    }

    // -------------------------------------------------------------------------
    // @include resolves by scenario name, and until now the three ways that can
    // go wrong were all silent or unhelpful:
    //   - a name that is not there threw an exception naming only the name, with
    //     no clue about what the feature does declare;
    //   - a name declared twice matched both and appended both bodies, producing
    //     a scenario nobody wrote;
    //   - a name that is only inside a comment still matched, so the including
    //     feature got the commented lines and nothing else.
    // The first is still an error, now with the file, the closest name and the
    // list of what is declared. The other two keep resolving the way they did,
    // because consumer repositories rely on it, but they are logged and a
    // duplicated name now copies only the first body.
    // -------------------------------------------------------------------------

    private static final String DIAGNOSTICS_FEATURE = "src/test/resources/features/includeDiagnostics.feature";

    @Test
    public void testMissingScenarioErrorNamesTheFileAndWhatIsDeclared() {
        assertThatExceptionOfType(IncludeException.class)
                .isThrownBy(() -> inctag.featureStepConverter(DIAGNOSTICS_FEATURE, "[999] Nothing like this", null))
                .withMessageContaining(DIAGNOSTICS_FEATURE)
                .withMessageContaining("[001] Unique scenario")
                .withMessageContaining("[005] Last scenario");
    }

    @Test
    public void testMissingScenarioErrorSuggestsTheClosestName() {
        assertThatExceptionOfType(IncludeException.class)
                .isThrownBy(() -> inctag.featureStepConverter(DIAGNOSTICS_FEATURE, "[001] Unique scenari", null))
                .withMessageContaining("Did you mean '[001] Unique scenario'?");
    }

    @Test
    public void testMissingScenarioErrorSkipsTheSuggestionWhenNothingIsClose() {
        assertThatExceptionOfType(IncludeException.class)
                .isThrownBy(() -> inctag.featureStepConverter(DIAGNOSTICS_FEATURE, "zzzzzzzzzzzzzzzzzzzz", null))
                .withMessageNotContaining("Did you mean");
    }

    @Test
    public void testDuplicatedScenarioNameCopiesOnlyTheFirstBody() throws IncludeException {
        String extracted = inctag.featureStepConverter(DIAGNOSTICS_FEATURE, "[002] Duplicated scenario", null);
        assertThat(extracted).as("The first body of the duplicated name is the one copied").contains("first_body");
        assertThat(extracted).as("The second body of the duplicated name is not appended too").doesNotContain("second_body");
    }

    @Test
    public void testCommentedOutScenarioIsNotReportedAsMissing() throws IncludeException {
        // It keeps resolving, as it does today, and yields nothing but the commented lines.
        String extracted = inctag.featureStepConverter(DIAGNOSTICS_FEATURE, "[004] Commented out scenario", null);
        assertThat(extracted).as("A commented out scenario contributes no step").doesNotContain("Given I save");
    }

    @Test
    public void testMissingFeatureFileIsStillReportedAsSuch() {
        assertThatExceptionOfType(IncludeException.class)
                .isThrownBy(() -> inctag.featureStepConverter("src/test/resources/features/nope.feature", "whatever", null))
                .withMessageContaining("Feature file were not found");
    }

    @Test
    public void testScenarioNameOf() {
        assertThat(inctag.scenarioNameOf("  Scenario: Plain name  ")).as("Name of a Scenario line").isEqualTo("Plain name");
        assertThat(inctag.scenarioNameOf("  Scenario Outline: Outline name")).as("Name of a Scenario Outline line").isEqualTo("Outline name");
        assertThat(inctag.scenarioNameOf("  Given I do something")).as("A step line declares no scenario").isNull();
        assertThat(inctag.scenarioNameOf("Feature: Scenario without the colon")).as("A Feature title is not a declaration").isNull();
    }

    @Test
    public void testIsCommentedOut() {
        assertThat(inctag.isCommentedOut("#  Scenario: Disabled")).as("A commented declaration").isTrue();
        assertThat(inctag.isCommentedOut("   #  Scenario: Indented and commented")).as("An indented commented declaration").isTrue();
        assertThat(inctag.isCommentedOut("  Scenario: Live one")).as("A live declaration").isFalse();
    }

    @Test
    public void testClosestName() {
        java.util.List<String> declared = java.util.Arrays.asList("Create the table", "Drop the table", "Query it");
        assertThat(inctag.closestName("Create the tabl", declared)).as("One character away").isEqualTo("Create the table");
        assertThat(inctag.closestName("Query it", declared)).as("An exact name is its own closest").isEqualTo("Query it");
        assertThat(inctag.closestName("something entirely different", declared)).as("Too far to be a typo").isNull();
        assertThat(inctag.closestName("anything", java.util.Collections.emptyList())).as("Nothing declared, nothing to suggest").isNull();
    }

    @Test
    public void testEditDistance() {
        assertThat(LoopIncludeTagAspect.editDistance("", "")).as("Two empty strings").isZero();
        assertThat(LoopIncludeTagAspect.editDistance("abc", "abc")).as("Two equal strings").isZero();
        assertThat(LoopIncludeTagAspect.editDistance("", "abc")).as("From empty, one insert per character").isEqualTo(3);
        assertThat(LoopIncludeTagAspect.editDistance("kitten", "sitting")).as("The textbook case").isEqualTo(3);
        assertThat(LoopIncludeTagAspect.editDistance("abc", "acb")).as("A transposition costs two substitutions").isEqualTo(2);
    }
}
