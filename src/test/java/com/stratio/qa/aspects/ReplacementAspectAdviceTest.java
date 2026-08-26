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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.stratio.qa.cucumber.testng.TestSourcesModelUtil;
import com.stratio.qa.specs.HookGSpec;
import com.stratio.qa.utils.ExceptionList;
import com.stratio.qa.utils.StepException;
import com.stratio.qa.utils.ThreadProperty;

import io.cucumber.core.backend.TestCaseState;
import io.cucumber.core.exception.CucumberException;
import io.cucumber.core.gherkin.Argument;
import io.cucumber.core.gherkin.DataTableArgument;
import io.cucumber.core.gherkin.DocStringArgument;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.gherkin.Step;
import io.cucumber.core.gherkin.StepType;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;

/**
 * Covers the advice methods of {@link ReplacementAspect} — the ones AspectJ normally drives —
 * by calling them directly with a mocked {@link ProceedingJoinPoint}.
 * <p>
 * The placeholder grammar itself is exercised in {@link ReplacementAspectTest}; what is tested
 * here is the surrounding behaviour: what the aspect does when a placeholder cannot be resolved,
 * how {@code @error} tags abort a step, how a step whose text carries a {@code !{VAR}} is
 * re-matched at execute time, and that the wrappers the aspect substitutes into Cucumber delegate
 * faithfully to the objects they replace.
 */
public class ReplacementAspectAdviceTest {

    private static final URI FEATURE_URI = URI.create("file:///features/advice.feature");

    /** Stand-in for Cucumber's package-private {@code ExecutionMode}, which the aspect only touches by name. */
    private enum FakeExecutionMode { RUN, SKIP, DRY_RUN }

    /** Same, minus the constant {@code getSkipMode} looks for. */
    private enum FakeModeWithoutSkip { RUN }

    private boolean loggerEnabledBackup;

    @BeforeMethod
    public void setUp() {
        loggerEnabledBackup = HookGSpec.loggerEnabled;
        StepException.INSTANCE.setException(null);
        ThreadProperty.set("class", this.getClass().getCanonicalName());
    }

    @AfterMethod
    public void tearDown() {
        HookGSpec.loggerEnabled = loggerEnabledBackup;
        StepException.INSTANCE.setException(null);
        // ExceptionList is a global singleton that ExceptionListTest asserts is empty, so the
        // failures these tests record have to be cleaned up or that test breaks depending on the
        // order the suite happens to run in.
        ExceptionList.INSTANCE.getExceptions().clear();
    }

    // -----------------------------------------------------------------------
    // aroundScenarios
    // -----------------------------------------------------------------------

    @Test
    public void unresolvableScenarioNameIsAnnotatedInsteadOfAbortingTheScenario() throws Throwable {
        System.clearProperty("BDDTEST_SCENARIO_UNDEFINED");
        ReplacementAspect aspect = new ReplacementAspect();
        Pickle delegate = mock(Pickle.class);
        when(delegate.getName()).thenReturn("check ${BDDTEST_SCENARIO_UNDEFINED}");
        when(delegate.getId()).thenReturn("pickle-annotated");

        int exceptionsBefore = ExceptionList.INSTANCE.getExceptions().size();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(new Object());

        aspect.aroundScenarios(pjp, delegate);

        Pickle replaced = (Pickle) proceededArgs(pjp)[0];
        assertThat(replaced.getName())
                .as("the original name must be kept, with the failure appended")
                .startsWith("check ${BDDTEST_SCENARIO_UNDEFINED}")
                .contains("Placeholder not replaced");
        assertThat(ExceptionList.INSTANCE.getExceptions())
                .as("the failure must still be reported at the end of the run")
                .hasSize(exceptionsBefore + 1);
        assertThat(TestSourcesModelUtil.INSTANCE.getTestSourcesModel().getReplacedScenarioName("pickle-annotated"))
                .as("the report reads the name from the model, not from the pickle")
                .isEqualTo(replaced.getName());
    }

    @Test
    public void scenarioWithoutPlaceholdersIsPassedThroughUnwrapped() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        Pickle delegate = mock(Pickle.class);
        when(delegate.getName()).thenReturn("a plain scenario name");
        when(delegate.getId()).thenReturn("pickle-plain");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(new Object());

        aspect.aroundScenarios(pjp, delegate);

        assertThat(proceededArgs(pjp)[0])
                .as("nothing to replace means no wrapper")
                .isSameAs(delegate);
    }

    @Test
    public void wrappedPickleDelegatesEverythingExceptTheName() throws Throwable {
        System.setProperty("BDDTEST_SCENARIO_NAME", "resolved");
        ReplacementAspect aspect = new ReplacementAspect();

        Location location = new Location(7, 1);
        Location scenarioLocation = new Location(5, 1);
        List<Step> steps = Collections.singletonList(mock(Step.class));
        List<String> tags = Arrays.asList("@one", "@two");

        Pickle delegate = mock(Pickle.class);
        when(delegate.getName()).thenReturn("scenario ${BDDTEST_SCENARIO_NAME}");
        when(delegate.getId()).thenReturn("pickle-delegating");
        when(delegate.getKeyword()).thenReturn("Scenario");
        when(delegate.getLanguage()).thenReturn("en");
        when(delegate.getLocation()).thenReturn(location);
        when(delegate.getScenarioLocation()).thenReturn(scenarioLocation);
        when(delegate.getSteps()).thenReturn(steps);
        when(delegate.getTags()).thenReturn(tags);
        when(delegate.getUri()).thenReturn(FEATURE_URI);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(new Object());

        aspect.aroundScenarios(pjp, delegate);

        Pickle wrapped = (Pickle) proceededArgs(pjp)[0];
        assertThat(wrapped).as("a replaced name must produce a wrapper").isNotSameAs(delegate);
        assertThat(wrapped.getName()).isEqualTo("scenario resolved");
        assertThat(wrapped.getKeyword()).isEqualTo("Scenario");
        assertThat(wrapped.getLanguage()).isEqualTo("en");
        assertThat(wrapped.getLocation()).isSameAs(location);
        assertThat(wrapped.getScenarioLocation()).isSameAs(scenarioLocation);
        assertThat(wrapped.getSteps()).isSameAs(steps);
        assertThat(wrapped.getTags()).isSameAs(tags);
        assertThat(wrapped.getUri()).isSameAs(FEATURE_URI);
        assertThat(wrapped.getId()).isEqualTo("pickle-delegating");
    }

    @Test
    public void theStepDataOfAScenarioDoesNotOutliveIt() throws Throwable {
        System.setProperty("BDDTEST_SCENARIO_SCOPED", "resolved");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-scoped", "a step with ${BDDTEST_SCENARIO_SCOPED}", null);

        Pickle pickle = mock(Pickle.class);
        when(pickle.getName()).thenReturn("a scenario");
        when(pickle.getId()).thenReturn("pickle-scoped");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(new Object());
        when(pjp.proceed(org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenAnswer(invocation -> runMatch(aspect, step));

        aspect.aroundScenarios(pjp, pickle);

        // Cucumber reuses threads across scenarios, so anything the aspect stashes per step has to
        // go when the scenario does. The next scenario on this thread must not inherit it: with the
        // map still populated, resolveStepDisplayText would answer with the previous scenario's
        // text for a step that shares its id.
        ProceedingJoinPoint next = starJoinPoint(step, mock(TestCase.class), FakeExecutionMode.RUN);
        TestCaseState state = mock(TestCaseState.class);
        when(next.proceed()).thenAnswer(invocation -> {
            aspect.writeReplacedStepToReport(mock(JoinPoint.class), state);
            return null;
        });

        aspect.aroundReplacementStar(next);

        verify(state, never()).log(org.mockito.ArgumentMatchers.anyString());
    }

    // -----------------------------------------------------------------------
    // aroundReplacementMatch
    // -----------------------------------------------------------------------

    @Test
    public void unresolvableStepTextIsRecordedAndLeftUntouchedAtMatchTime() throws Throwable {
        System.clearProperty("BDDTEST_STEP_UNDEFINED");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-untouched", "a step with ${BDDTEST_STEP_UNDEFINED}", null);

        int exceptionsBefore = ExceptionList.INSTANCE.getExceptions().size();
        Step matched = runMatch(aspect, step);

        assertThat(matched)
                .as("nothing could be replaced, so the step must be handed over untouched")
                .isSameAs(step);
        assertThat(ExceptionList.INSTANCE.getExceptions()).hasSize(exceptionsBefore + 1);
    }

    @Test
    public void unresolvableDataTableCellIsRecordedAndLeftUntouchedAtMatchTime() throws Throwable {
        System.clearProperty("BDDTEST_CELL_UNDEFINED");
        System.setProperty("BDDTEST_CELL_DEFINED", "ok");
        ReplacementAspect aspect = new ReplacementAspect();

        DataTableArgument dataTable = mock(DataTableArgument.class);
        when(dataTable.cells()).thenReturn(Arrays.asList(
                Arrays.asList("${BDDTEST_CELL_DEFINED}", "${BDDTEST_CELL_UNDEFINED}")));
        when(dataTable.getLine()).thenReturn(12);

        Step step = stepMock("step-datatable", "a step with a data table", dataTable);

        int exceptionsBefore = ExceptionList.INSTANCE.getExceptions().size();
        Step matched = runMatch(aspect, step);

        DataTableArgument replaced = (DataTableArgument) matched.getArgument();
        assertThat(replaced.cells()).containsExactly(Arrays.asList("ok", "${BDDTEST_CELL_UNDEFINED}"));
        assertThat(replaced.getLine()).as("the line must be delegated").isEqualTo(12);
        assertThat(ExceptionList.INSTANCE.getExceptions())
                .as("one failing cell, one recorded exception")
                .hasSize(exceptionsBefore + 1);
    }

    @Test
    public void docStringIsReplacedAtMatchTimeAndTheWrapperDelegatesTheRest() throws Throwable {
        System.setProperty("BDDTEST_DOCSTRING", "SELECT 1");
        ReplacementAspect aspect = new ReplacementAspect();

        DocStringArgument docString = mock(DocStringArgument.class);
        when(docString.getContent()).thenReturn("${BDDTEST_DOCSTRING}");
        when(docString.getContentType()).thenReturn("sql");
        when(docString.getMediaType()).thenReturn("text/x-sql");
        when(docString.getLine()).thenReturn(21);

        Step step = stepMock("step-docstring", "a step with a doc string", docString);

        Step matched = runMatch(aspect, step);

        DocStringArgument replaced = (DocStringArgument) matched.getArgument();
        assertThat(replaced.getContent()).isEqualTo("SELECT 1");
        assertThat(replaced.getContentType()).isEqualTo("sql");
        assertThat(replaced.getMediaType()).isEqualTo("text/x-sql");
        assertThat(replaced.getLine()).isEqualTo(21);
    }

    @Test
    public void unresolvableDocStringIsRecordedAndLeftUntouchedAtMatchTime() throws Throwable {
        System.clearProperty("BDDTEST_DOCSTRING_UNDEFINED");
        ReplacementAspect aspect = new ReplacementAspect();

        DocStringArgument docString = mock(DocStringArgument.class);
        when(docString.getContent()).thenReturn("${BDDTEST_DOCSTRING_UNDEFINED}");

        Step step = stepMock("step-docstring-ko", "a step with a doc string", docString);

        int exceptionsBefore = ExceptionList.INSTANCE.getExceptions().size();
        Step matched = runMatch(aspect, step);

        assertThat(((DocStringArgument) matched.getArgument()).getContent())
                .isEqualTo("${BDDTEST_DOCSTRING_UNDEFINED}");
        assertThat(ExceptionList.INSTANCE.getExceptions()).hasSize(exceptionsBefore + 1);
    }

    @Test
    public void replacedStepDelegatesEverythingExceptTextAndArgument() throws Throwable {
        System.setProperty("BDDTEST_STEP_TEXT", "resolved");
        ReplacementAspect aspect = new ReplacementAspect();

        Location location = new Location(9, 3);
        Step step = mock(Step.class);
        when(step.getId()).thenReturn("step-delegating");
        when(step.getText()).thenReturn("a step with ${BDDTEST_STEP_TEXT}");
        when(step.getKeyword()).thenReturn("When ");
        when(step.getArgument()).thenReturn(null);
        when(step.getType()).thenReturn(StepType.WHEN);
        when(step.getPreviousGivenWhenThenKeyword()).thenReturn("Given ");
        when(step.getLine()).thenReturn(9);
        when(step.getLocation()).thenReturn(location);

        Step matched = runMatch(aspect, step);

        assertThat(matched).isNotSameAs(step);
        assertThat(matched.getText()).isEqualTo("a step with resolved");
        assertThat(matched.getArgument()).isNull();
        assertThat(matched.getId()).isEqualTo("step-delegating");
        assertThat(matched.getKeyword()).isEqualTo("When ");
        assertThat(matched.getType()).isEqualTo(StepType.WHEN);
        assertThat(matched.getPreviousGivenWhenThenKeyword()).isEqualTo("Given ");
        assertThat(matched.getLine()).isEqualTo(9);
        assertThat(matched.getLocation()).isSameAs(location);
        assertThat(TestSourcesModelUtil.INSTANCE.getTestSourcesModel().getReplacedStepText("step-delegating"))
                .as("the report must be told what the match was computed against")
                .isEqualTo("a step with resolved");
    }

    // -----------------------------------------------------------------------
    // aroundReplacementStar
    // -----------------------------------------------------------------------

    @Test
    public void stepIsSkippedWhenAnEarlierStepAlreadyFailed() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        StepException.INSTANCE.setException(new IllegalStateException("an earlier step blew up"));

        ProceedingJoinPoint pjp = starJoinPoint(stepMock("step-skipped", "any step", null),
                mock(TestCase.class), FakeExecutionMode.RUN);

        assertThat(aspect.aroundReplacementStar(pjp)).isEqualTo(FakeExecutionMode.SKIP);
        verify(pjp, never()).proceed();
    }

    @Test
    public void skipModeFallsBackToTheIncomingModeWhenThereIsNoSkipConstant() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        StepException.INSTANCE.setException(new IllegalStateException("an earlier step blew up"));

        ProceedingJoinPoint pjp = starJoinPoint(stepMock("step-noskip", "any step", null),
                mock(TestCase.class), FakeModeWithoutSkip.RUN);

        assertThat(aspect.aroundReplacementStar(pjp))
                .as("an enum with no SKIP constant must be handed back as-is")
                .isEqualTo(FakeModeWithoutSkip.RUN);
        verify(pjp, never()).proceed();
    }

    @Test
    public void errorMessageTagAbortsTheStepWithItsOwnMessage() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        TestCase testCase = mock(TestCase.class);
        when(testCase.getTags()).thenReturn(Arrays.asList("@error", "@errorMessage(table__not__found)"));

        ProceedingJoinPoint pjp = starJoinPoint(stepMock("step-error", "any step", null),
                testCase, FakeExecutionMode.RUN);

        assertThatExceptionOfType(CucumberException.class)
                .isThrownBy(() -> aspect.aroundReplacementStar(pjp))
                .withMessage("table not found");
        verify(pjp, never()).proceed();
    }

    @Test
    public void errorTagWithoutAnErrorMessageLetsTheStepRun() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        TestCase testCase = mock(TestCase.class);
        when(testCase.getTags()).thenReturn(Arrays.asList("@error", "@something-else"));

        ProceedingJoinPoint pjp = starJoinPoint(stepMock("step-error-bare", "any step", null),
                testCase, FakeExecutionMode.RUN);

        aspect.aroundReplacementStar(pjp);

        verify(pjp).proceed();
    }

    @Test
    public void aFailingStepIsRecordedSoTheFollowingOnesAreSkipped() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        IllegalStateException failure = new IllegalStateException("step blew up");

        ProceedingJoinPoint pjp = starJoinPoint(stepMock("step-failing", "any step", null),
                mock(TestCase.class), FakeExecutionMode.RUN);
        when(pjp.proceed()).thenThrow(failure);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> aspect.aroundReplacementStar(pjp));

        assertThat(StepException.INSTANCE.getException()).isSameAs(failure);
    }

    @Test
    public void unresolvablePlaceholdersAreLeftAsTheyAreWhenLoggingTheStep() throws Throwable {
        System.clearProperty("BDDTEST_LOG_UNDEFINED");
        HookGSpec.loggerEnabled = true;
        ReplacementAspect aspect = new ReplacementAspect();

        String text = "a step with !{BDDTEST_LOG_UNRESOLVED} and @{NODOT}";
        Step step = stepMock("step-logged", text, null);
        runMatch(aspect, step);

        ProceedingJoinPoint pjp = starJoinPoint(step, mock(TestCase.class), FakeExecutionMode.RUN);

        aspect.aroundReplacementStar(pjp);

        verify(pjp).proceed();
    }

    @Test
    public void aDataTableStillCarryingRuntimeVarsIsLoggedWithoutFailing() throws Throwable {
        HookGSpec.loggerEnabled = true;
        ReplacementAspect aspect = new ReplacementAspect();

        DataTableArgument dataTable = mock(DataTableArgument.class);
        when(dataTable.cells()).thenReturn(Arrays.asList(Arrays.asList("!{BDDTEST_LOG_DT_UNRESOLVED}")));
        Step step = stepMock("step-logged-datatable", "a step with a data table", dataTable);
        runMatch(aspect, step);

        ProceedingJoinPoint pjp = starJoinPoint(step, mock(TestCase.class), FakeExecutionMode.RUN);

        aspect.aroundReplacementStar(pjp);

        verify(pjp).proceed();
    }

    // -----------------------------------------------------------------------
    // aroundUndefinedStepRun
    // -----------------------------------------------------------------------

    @Test
    public void undefinedStepRunProceedsWhenNoStepIsBeingTracked() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);

        aspect.aroundUndefinedStepRun(pjp);

        verify(pjp).proceed();
    }

    @Test
    public void undefinedStepRunProceedsWhenTheStepCarriesNoRuntimeVar() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-no-runtime-var", "a step with nothing to resolve", null);
        runMatch(aspect, step);

        ProceedingJoinPoint undefinedPjp = mock(ProceedingJoinPoint.class);
        runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));

        verify(undefinedPjp).proceed();
    }

    @Test
    public void undefinedStepRunProceedsWhenTheRuntimeVarIsStillUnresolvable() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-unresolvable-runtime-var", "a step with !{BDDTEST_UNDEF_RUNTIME}", null);
        runMatch(aspect, step);

        ProceedingJoinPoint undefinedPjp = mock(ProceedingJoinPoint.class);
        runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));

        verify(undefinedPjp).proceed();
    }

    @Test
    public void undefinedStepRunProceedsWhenTheScenarioRunnerIsUnknown() throws Throwable {
        ThreadProperty.set("BDDTEST_KNOWN_RUNTIME", "known");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-no-runner", "a step with !{BDDTEST_KNOWN_RUNTIME}", null);
        runMatch(aspect, step);

        ProceedingJoinPoint undefinedPjp = mock(ProceedingJoinPoint.class);
        runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));

        verify(undefinedPjp).proceed();
    }

    @Test
    public void undefinedStepRunAlsoResolvesCodePlaceholdersOnceTheRuntimeVarIsKnown() throws Throwable {
        ThreadProperty.set("BDDTEST_CODE_RUNTIME", "known");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-code-placeholder",
                "a step with !{BDDTEST_CODE_RUNTIME} and @{JSON.schemas/simple1.json}", null);
        runMatch(aspect, step);

        ProceedingJoinPoint undefinedPjp = mock(ProceedingJoinPoint.class);
        runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));

        verify(undefinedPjp).proceed();
    }

    @Test
    public void undefinedStepRunProceedsWhenTheCodePlaceholderCannotBeResolved() throws Throwable {
        ThreadProperty.set("BDDTEST_CODE_RUNTIME_KO", "known");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-code-placeholder-ko",
                "a step with !{BDDTEST_CODE_RUNTIME_KO} and @{NOSUBPROPERTY}", null);
        runMatch(aspect, step);

        ProceedingJoinPoint undefinedPjp = mock(ProceedingJoinPoint.class);
        runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));

        verify(undefinedPjp).proceed();
    }

    @Test
    public void aStepThatOnlyMatchesOnceItsRuntimeVarIsResolvedIsReMatchedAndRun() throws Throwable {
        ThreadProperty.set("BDDTEST_REMATCH", "42");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-rematched", "the counter is '!{BDDTEST_REMATCH}'", null);

        RecordingMatch match = new RecordingMatch(null);
        FakeRunner runner = new FakeRunner(match);
        TestCaseState state = mock(TestCaseState.class);
        ProceedingJoinPoint undefinedPjp = undefinedJoinPoint(state);

        Object[] result = new Object[1];
        runScenarioThen(aspect, runner, () -> {
            runMatch(aspect, step);
            runStarThen(aspect, step, () -> {
                result[0] = aspect.aroundUndefinedStepRun(undefinedPjp);
                return null;
            });
            return null;
        });

        assertThat(runner.matchedStepText)
                .as("the re-match must be computed against the resolved text")
                .isEqualTo("the counter is '42'");
        assertThat(match.ranWith).as("the matched step must actually be run").isSameAs(state);
        assertThat(result[0]).isNull();
        verify(undefinedPjp, never()).proceed();
    }

    @Test
    public void aFailureInsideTheReMatchedStepIsReportedAsItsOwnCause() throws Throwable {
        ThreadProperty.set("BDDTEST_REMATCH_KO", "42");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-rematched-ko", "the counter is '!{BDDTEST_REMATCH_KO}'", null);

        IllegalStateException failure = new IllegalStateException("the step body blew up");
        FakeRunner runner = new FakeRunner(new RecordingMatch(failure));
        ProceedingJoinPoint undefinedPjp = undefinedJoinPoint(mock(TestCaseState.class));

        assertThatExceptionOfType(IllegalStateException.class)
                .as("the reflective invocation must not leak as InvocationTargetException")
                .isThrownBy(() -> runScenarioThen(aspect, runner, () -> {
                    runMatch(aspect, step);
                    runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));
                    return null;
                }))
                .isSameAs(failure);
    }

    @Test
    public void anUnusableRunnerLeavesTheStepUndefinedInsteadOfBlowingUp() throws Throwable {
        ThreadProperty.set("BDDTEST_REMATCH_NO_RUNNER", "42");
        ReplacementAspect aspect = new ReplacementAspect();
        Step step = stepMock("step-rematch-no-method", "the counter is '!{BDDTEST_REMATCH_NO_RUNNER}'", null);

        ProceedingJoinPoint undefinedPjp = undefinedJoinPoint(mock(TestCaseState.class));

        // A runner with no matchStepToStepDefinition at all: the aspect must fall back to the
        // normal undefined-step handling rather than propagate the reflection failure.
        runScenarioThen(aspect, new Object(), () -> {
            runMatch(aspect, step);
            runStarThen(aspect, step, () -> aspect.aroundUndefinedStepRun(undefinedPjp));
            return null;
        });

        verify(undefinedPjp).proceed();
    }

    // -----------------------------------------------------------------------
    // writeReplacedStepToReport
    // -----------------------------------------------------------------------

    @Test
    public void nothingIsWrittenToTheReportWhenNoStepIsBeingTracked() {
        ReplacementAspect aspect = new ReplacementAspect();
        TestCaseState state = mock(TestCaseState.class);

        aspect.writeReplacedStepToReport(mock(JoinPoint.class), state);

        verify(state, never()).log(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void theRawAndResolvedStepTextsAreWrittenToTheReport() throws Throwable {
        System.setProperty("BDDTEST_REPORT_ENV", "envValue");
        ThreadProperty.set("BDDTEST_REPORT_THREAD", "threadValue");
        ReplacementAspect aspect = new ReplacementAspect();

        String raw = "a step with ${BDDTEST_REPORT_ENV} and !{BDDTEST_REPORT_THREAD}";
        Step step = stepMock("step-reported", raw, null);
        runMatch(aspect, step);

        TestCaseState state = mock(TestCaseState.class);
        runStarThen(aspect, step, () -> {
            aspect.writeReplacedStepToReport(mock(JoinPoint.class), state);
            return null;
        });

        verify(state).log(raw);
        verify(state).log("a step with envValue and threadValue");
    }

    @Test
    public void reportWriteFailuresAreSwallowed() throws Throwable {
        System.setProperty("BDDTEST_REPORT_ENV2", "envValue");
        ReplacementAspect aspect = new ReplacementAspect();

        String raw = "a step with ${BDDTEST_REPORT_ENV2}";
        Step step = stepMock("step-report-ko", raw, null);
        runMatch(aspect, step);

        TestCaseState state = mock(TestCaseState.class);
        doThrow(new IllegalStateException("no active test step")).when(state)
                .log(org.mockito.ArgumentMatchers.anyString());

        runStarThen(aspect, step, () -> {
            aspect.writeReplacedStepToReport(mock(JoinPoint.class), state);
            return null;
        });

        verify(state).log(raw);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private interface ThrowingSupplier {
        Object get() throws Throwable;
    }

    /**
     * Stand-in for {@code io.cucumber.core.runner.Runner}. The aspect reaches it reflectively by
     * method name, so any class with the right signature will do.
     */
    public static class FakeRunner {

        private final Object match;

        private String matchedStepText;

        FakeRunner(Object match) {
            this.match = match;
        }

        @SuppressWarnings("unused")
        private Object matchStepToStepDefinition(Pickle pickle, Step step) {
            this.matchedStepText = step.getText();
            return match;
        }
    }

    /** Stand-in for a {@code PickleStepDefinitionMatch}, likewise reached by method name. */
    public static class RecordingMatch {

        private final RuntimeException failure;

        private TestCaseState ranWith;

        RecordingMatch(RuntimeException failure) {
            this.failure = failure;
        }

        @SuppressWarnings("unused")
        private void runStep(TestCaseState state) {
            this.ranWith = state;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private Step stepMock(String id, String text, Argument argument) {
        Step step = mock(Step.class);
        when(step.getId()).thenReturn(id);
        when(step.getText()).thenReturn(text);
        when(step.getKeyword()).thenReturn("Given ");
        when(step.getArgument()).thenReturn(argument);
        return step;
    }

    private PickleStepTestStep pickleStepMock(Step step) {
        // Resolved before the stubbing chain starts: calling a mock inside when(...) leaves the
        // outer stubbing unfinished and Mockito rejects it.
        String rawText = step.getText();
        PickleStepTestStep pickleStep = mock(PickleStepTestStep.class);
        when(pickleStep.getStep()).thenReturn(step);
        when(pickleStep.getStepText()).thenReturn(rawText);
        when(pickleStep.getUri()).thenReturn(FEATURE_URI);
        when(pickleStep.getStepLine()).thenReturn(3);
        return pickleStep;
    }

    private ProceedingJoinPoint starJoinPoint(Step step, TestCase testCase, Object executionMode) {
        PickleStepTestStep pickleStep = pickleStepMock(step);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(pickleStep);
        when(pjp.getArgs()).thenReturn(new Object[]{testCase, null, null, executionMode});
        return pjp;
    }

    /**
     * Runs {@code body} from inside {@code aroundReplacementStar}, which is the only place that
     * populates the {@code currentPickleStep} thread-local the later advices read.
     */
    private void runStarThen(ReplacementAspect aspect, Step step, ThrowingSupplier body) throws Throwable {
        ProceedingJoinPoint pjp = starJoinPoint(step, mock(TestCase.class), FakeExecutionMode.RUN);
        when(pjp.proceed()).thenAnswer(invocation -> body.get());
        aspect.aroundReplacementStar(pjp);
    }

    private ProceedingJoinPoint undefinedJoinPoint(TestCaseState state) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{state});
        return pjp;
    }

    /**
     * Runs {@code body} from inside {@code aroundScenarios}, the only place that publishes the
     * scenario runner the late re-match reflects on.
     */
    private void runScenarioThen(ReplacementAspect aspect, Object runner, ThrowingSupplier body) throws Throwable {
        Pickle pickle = mock(Pickle.class);
        when(pickle.getName()).thenReturn("a scenario with nothing to replace");
        when(pickle.getId()).thenReturn("pickle-runner");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getTarget()).thenReturn(runner);
        when(pjp.proceed(org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenAnswer(invocation -> body.get());

        aspect.aroundScenarios(pjp, pickle);
    }

    private Step runMatch(ReplacementAspect aspect, Step step) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        aspect.aroundReplacementMatch(pjp, mock(Pickle.class), step);
        return (Step) proceededArgs(pjp)[1];
    }

    private Object[] proceededArgs(ProceedingJoinPoint pjp) throws Throwable {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(pjp).proceed(captor.capture());
        return captor.getValue();
    }
}
