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

import com.stratio.qa.cucumber.testng.TestSourcesModelUtil;
import com.stratio.qa.exceptions.NonReplaceableException;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.specs.HookGSpec;
import com.stratio.qa.utils.ExceptionList;
import com.stratio.qa.utils.StepException;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.core.gherkin.Argument;
import io.cucumber.core.gherkin.DataTableArgument;
import io.cucumber.core.gherkin.DocStringArgument;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.gherkin.Step;
import io.cucumber.core.gherkin.StepType;
import io.cucumber.core.exception.CucumberException;
import io.cucumber.datatable.DataTable;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.StepArgument;
import io.cucumber.plugin.event.TestCase;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Aspect
public class ReplacementAspect {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    // thread-local: stepId -> ReplacedStepData (populated during matchStepToStepDefinition)
    private final ThreadLocal<Map<String, ReplacedStepData>> replacedStepData =
            ThreadLocal.withInitial(HashMap::new);

    // thread-locals for late-resolution of !{VAR} in step text (Cucumber 6 match-all-then-run-all)
    private final ThreadLocal<Object> currentRunner = new ThreadLocal<>();

    private final ThreadLocal<Pickle> currentPickle = new ThreadLocal<>();

    private final ThreadLocal<PickleStepTestStep> currentPickleStep = new ThreadLocal<>();

    // -----------------------------------------------------------------------
    // Pointcut 1: replace scenario name before runPickle
    // -----------------------------------------------------------------------

    @Pointcut("execution(void io.cucumber.core.runner.Runner.runPickle(..)) && args(pickle)")
    protected void replacementScenarios(Pickle pickle) {
    }

    @Around(value = "replacementScenarios(pickle)")
    public void aroundScenarios(ProceedingJoinPoint pjp, Pickle pickle) throws Throwable {
        String scenarioName = pickle.getName();
        String newScenarioName;
        try {
            newScenarioName = replacedElement(scenarioName, pjp);
        } catch (Exception e) {
            ExceptionList.INSTANCE.getExceptions().add(e);
            newScenarioName = scenarioName + " | Placeholder not replaced -- " + e.toString();
        }
        if (!newScenarioName.equals(scenarioName)) {
            pickle = new WrappedPickle(pickle, newScenarioName);
        }
        currentRunner.set(pjp.getTarget());
        currentPickle.set(pickle);
        try {
            pjp.proceed(new Object[]{pickle});
        } finally {
            currentRunner.remove();
            currentPickle.remove();
        }
    }

    // -----------------------------------------------------------------------
    // Pointcut 2: replace step text/args before step-definition matching
    // -----------------------------------------------------------------------

    @Pointcut("execution(private * io.cucumber.core.runner.Runner.matchStepToStepDefinition(..)) && args(pickle, step)")
    protected void replacementMatch(Pickle pickle, Step step) {
    }

    @Around(value = "replacementMatch(pickle, step)")
    public Object aroundReplacementMatch(ProceedingJoinPoint pjp, Pickle pickle, Step step) throws Throwable {
        String originalText = step.getText();
        String newText = originalText;
        String dataTableLog = "";

        try {
            // Step text: only ${ENV} at match time. !{VAR} and @{CODE} are deferred to
            // execute time (aroundReplacementExecute). !{VAR} because ThreadProperty values
            // set by prior steps are not yet available; @{CODE} because generated files
            // (e.g. testSOATtag.json) may not exist yet or may contain stale content.
            newText = replacedElementSkipReflection(originalText, pjp);
        } catch (Exception e) {
            ExceptionList.INSTANCE.getExceptions().add(e);
        }

        // Replace DataTable or DocString argument: apply only ${ENV} at match time.
        // @{JSON/FILE.*} and !{VAR} are deferred to aroundReplacementExecute so that
        // files created by earlier steps in the same scenario are accessible when resolved.
        Argument newArg = step.getArgument();
        StringBuilder sbDataTable = new StringBuilder();
        if (newArg instanceof DataTableArgument) {
            DataTableArgument dt = (DataTableArgument) newArg;
            List<List<String>> originalCells = dt.cells();
            List<List<String>> newCells = new ArrayList<>();
            for (List<String> row : originalCells) {
                List<String> newRow = new ArrayList<>();
                sbDataTable.append("| ");
                for (String cell : row) {
                    String newCell = cell;
                    try {
                        newCell = replacedElementSkipReflection(cell, pjp);
                    } catch (Exception e) {
                        ExceptionList.INSTANCE.getExceptions().add(e);
                    }
                    newRow.add(newCell);
                    sbDataTable.append(newCell).append(" | ");
                }
                newCells.add(newRow);
                sbDataTable.append("\n");
            }
            newArg = new ReplacedDataTableArgument(dt, newCells);
        } else if (newArg instanceof DocStringArgument) {
            DocStringArgument ds = (DocStringArgument) newArg;
            String newContent = ds.getContent();
            try {
                newContent = replacedElementSkipReflection(newContent, pjp);
            } catch (Exception e) {
                ExceptionList.INSTANCE.getExceptions().add(e);
            }
            sbDataTable.append(newContent).append("\n");
            newArg = new ReplacedDocStringArgument(ds, newContent);
        }
        dataTableLog = sbDataTable.toString();

        Step replacedStep = (!newText.equals(originalText) || newArg != step.getArgument())
                ? new ReplacedStep(step, newText, newArg)
                : step;

        // Store for logging in run()
        ReplacedStepData data = new ReplacedStepData(newText, dataTableLog, step.getKeyword());
        replacedStepData.get().put(step.getId(), data);

        // Also store in TestSourcesModel for external use
        TestSourcesModelUtil.INSTANCE.getTestSourcesModel().addReplacedStepText(
                pickle.getUri().toString(), step.getLocation().getLine(), newText);

        return pjp.proceed(new Object[]{pickle, replacedStep});
    }

    // -----------------------------------------------------------------------
    // Pointcut 3a: resolve !{VAR} in step args at execute time
    //
    // Cucumber 6 matches all steps before running any (match-all-then-run-all).
    // !{VAR} set by a prior step is not yet available at match time, so it
    // survives unreplaced as a literal string in the matched argument.
    // JavaStepDefinition.execute(Object[]) fires after prior steps have run,
    // so ThreadProperty has the value by then.
    // -----------------------------------------------------------------------

    @Pointcut("execution(* io.cucumber.java.JavaStepDefinition.execute(Object[]))")
    protected void replacementExecute() {
    }

    @Around("replacementExecute()")
    public Object aroundReplacementExecute(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = (Object[]) pjp.getArgs()[0];
        if (args != null) {
            boolean changed = false;
            Object[] newArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof String) {
                    String s = (String) args[i];
                    if (s.contains("!{") || s.contains("@{") || s.contains("${")) {
                        try {
                            String replaced = replacedElement(s, pjp);
                            // Normalize newlines to literal \n so multi-line ThreadProperty values
                            // don't break shell commands (Cucumber 4 did this before re-matching).
                            replaced = replaced.replaceAll("[\\r\\n]+", "\\\\n");
                            if (!replaced.equals(s)) {
                                newArgs[i] = replaced;
                                changed = true;
                            } else {
                                newArgs[i] = args[i];
                            }
                        } catch (NonReplaceableException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new NonReplaceableException(e.getMessage());
                        }
                    } else {
                        newArgs[i] = args[i];
                    }
                } else if (args[i] instanceof DataTable) {
                    DataTable dt = (DataTable) args[i];
                    List<List<String>> cells = dt.cells();
                    List<List<String>> resolvedCells = new ArrayList<>(cells.size());
                    boolean dtChanged = false;
                    for (List<String> row : cells) {
                        List<String> newRow = new ArrayList<>(row.size());
                        for (String cell : row) {
                            if (cell != null && (cell.contains("!{") || cell.contains("@{") || cell.contains("${"))) {
                                try {
                                    String resolved = replacedElement(cell, pjp);
                                    newRow.add(resolved);
                                    if (!resolved.equals(cell)) {
                                        dtChanged = true;
                                    }
                                } catch (NonReplaceableException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new NonReplaceableException(e.getMessage());
                                }
                            } else {
                                newRow.add(cell);
                            }
                        }
                        resolvedCells.add(newRow);
                    }
                    if (dtChanged) {
                        newArgs[i] = DataTable.create(resolvedCells, dt.getTableConverter());
                        changed = true;
                    } else {
                        newArgs[i] = args[i];
                    }
                } else {
                    newArgs[i] = args[i];
                }
            }
            if (changed) {
                return pjp.proceed(new Object[]{newArgs});
            }
        }
        return pjp.proceed();
    }

    // -----------------------------------------------------------------------
    // Pointcut 3: log step, handle @error tag, handle StepException skip
    // -----------------------------------------------------------------------

    @Pointcut("execution(* io.cucumber.core.runner.PickleStepTestStep.run(..))")
    protected void replacementStar() {
    }

    @Around(value = "replacementStar()")
    public Object aroundReplacementStar(ProceedingJoinPoint pjp) throws Throwable {
        PickleStepTestStep pickleStep = (PickleStepTestStep) pjp.getTarget();

        // Skip if a previous step already failed (bdt mechanism)
        if (StepException.INSTANCE.getException() != null) {
            return getSkipMode(pjp.getArgs()[3]);
        }

        // @error tag: check test case tags
        Object testCaseArg = pjp.getArgs()[0];
        if (testCaseArg instanceof TestCase) {
            TestCase tc = (TestCase) testCaseArg;
            if (tc.getTags().contains("@error")) {
                for (String tag : tc.getTags()) {
                    if (tag.contains("errorMessage")) {
                        String msg = tag.substring((tag.lastIndexOf("(") + 1), (tag.length()) - 1)
                                .replaceAll("__", " ");
                        logger.error("-> " + msg);
                        throw new CucumberException(msg);
                    }
                }
            }
        }

        // Log step with replaced text
        if (HookGSpec.loggerEnabled) {
            Step gherkinStep = (Step) pickleStep.getStep();
            String stepId = gherkinStep.getId();
            ReplacedStepData data = replacedStepData.get().get(stepId);
            String keyword = data != null ? data.keyword : pickleStep.getStep().getKeyword();
            String displayText = data != null ? data.replacedText : pickleStep.getStepText();
            if (displayText.contains("!{")) {
                try {
                    displayText = replaceReflectionPlaceholders(displayText, pjp);
                } catch (Exception ignored) {
                }
            }
            logger.info("   {}{}", keyword, displayText);
            if (data != null && !data.dataTableLog.isEmpty()) {
                String dataTableDisplay = data.dataTableLog;
                if (dataTableDisplay.contains("!{")) {
                    try {
                        dataTableDisplay = replaceReflectionPlaceholders(dataTableDisplay, pjp);
                    } catch (Exception ignored) {
                    }
                }
                logger.info("  {}", dataTableDisplay);
            }
        }

        currentPickleStep.set(pickleStep);
        try {
            return pjp.proceed();
        } catch (Exception e) {
            StepException.INSTANCE.setException(e);
            throw e;
        } finally {
            currentPickleStep.remove();
        }
    }

    // -----------------------------------------------------------------------
    // Pointcut 3b: late-resolve !{VAR} in step text when step was Undefined
    // because !{VAR} was unresolvable at Cucumber 6 match time.
    // -----------------------------------------------------------------------

    @Pointcut("execution(* io.cucumber.core.runner.UndefinedPickleStepDefinitionMatch.runStep(..))")
    protected void undefinedStepRun() {
    }

    @Around("undefinedStepRun()")
    public Object aroundUndefinedStepRun(ProceedingJoinPoint pjp) throws Throwable {
        PickleStepTestStep pickleStep = currentPickleStep.get();
        if (pickleStep == null) {
            return pjp.proceed();
        }

        String stepId = ((Step) pickleStep.getStep()).getId();
        ReplacedStepData data = replacedStepData.get().get(stepId);

        if (data == null || !data.replacedText.contains("!{")) {
            return pjp.proceed();
        }

        String resolved;
        try {
            resolved = replaceReflectionPlaceholders(data.replacedText, pjp);
        } catch (Exception e) {
            return pjp.proceed();
        }

        if (resolved.contains("!{")) {
            return pjp.proceed();
        }

        if (resolved.contains("@{")) {
            try {
                resolved = replaceCodePlaceholders(resolved, pjp);
            } catch (Exception e) {
                return pjp.proceed();
            }
        }

        Step originalStep = (Step) pickleStep.getStep();
        Step resolvedStep = new ReplacedStep(originalStep, resolved, originalStep.getArgument());

        Object runner = currentRunner.get();
        Pickle pickle = currentPickle.get();

        if (runner == null || pickle == null) {
            return pjp.proceed();
        }

        try {
            Method matchMethod = runner.getClass().getDeclaredMethod(
                    "matchStepToStepDefinition",
                    Pickle.class,
                    Step.class
            );
            matchMethod.setAccessible(true);
            Object match = matchMethod.invoke(runner, pickle, resolvedStep);

            if (match != null && !"io.cucumber.core.runner.UndefinedPickleStepDefinitionMatch".equals(
                    match.getClass().getName())) {
                io.cucumber.core.backend.TestCaseState state =
                        (io.cucumber.core.backend.TestCaseState) pjp.getArgs()[0];
                Method runStepMethod = match.getClass().getDeclaredMethod(
                        "runStep", io.cucumber.core.backend.TestCaseState.class);
                runStepMethod.setAccessible(true);
                runStepMethod.invoke(match, state);
                return null;
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            }
            throw e;
        } catch (Exception e) {
            logger.error("Late-resolution re-match failed for step: {}", data.replacedText, e);
        }

        return pjp.proceed();
    }

    // -----------------------------------------------------------------------
    // Replacement helpers
    // -----------------------------------------------------------------------

    protected String replacedElementSkipReflection(String el, JoinPoint jp) throws NonReplaceableException {
        try {
            if (el.contains("${")) {
                el = replaceEnvironmentPlaceholders(el, jp);
            }
            // !{VAR} and @{CODE} are deferred to execute time: !{} because ThreadProperty
            // values set by prior steps are unavailable at Cucumber 6 match time; @{} because
            // files like testSOATtag.json may not exist yet or may contain stale content.
            return el;
        } catch (Exception e) {
            if (e instanceof NonReplaceableException) {
                throw e;
            } else {
                throw new NonReplaceableException("Unreplaceable element: " + el + " - " + e);
            }
        }
    }

    protected String replacedElement(String el, JoinPoint jp) throws NonReplaceableException {
        try {
            if (el.contains("${")) {
                el = replaceEnvironmentPlaceholders(el, jp);
            }
            if (el.contains("!{")) {
                el = replaceReflectionPlaceholders(el, jp);
            }
            if (el.contains("@{")) {
                el = replaceCodePlaceholders(el, jp);
            }
            return el;
        } catch (Exception e) {
            if (e instanceof NonReplaceableException) {
                throw e;
            } else {
                throw new NonReplaceableException("Unreplaceable element: " + el + " - " + e);
            }
        }
    }

    protected String replaceCodePlaceholders(String element, JoinPoint pjp) throws NonReplaceableException {
        String newVal = element;
        while (newVal.contains("@{")) {
            String placeholder = newVal.substring(newVal.indexOf("@{"), newVal.indexOf("}", newVal.indexOf("@{")) + 1);
            String property = placeholder.substring(2, placeholder.length() - 1).toLowerCase();
            String subproperty = "";
            CommonG commonJson;
            if (placeholder.contains(".")) {
                property = placeholder.substring(2, placeholder.indexOf(".")).toLowerCase();
                subproperty = placeholder.substring(placeholder.indexOf(".") + 1, placeholder.length() - 1);
            } else {
                logger.error("{} -> {} placeholded element has not been replaced previously.", element, property);
                throw new NonReplaceableException("Unreplaceable placeholder: " + placeholder);
            }

            switch (property) {
                case "ip":
                    boolean found = false;
                    if (!subproperty.isEmpty()) {
                        Enumeration<InetAddress> ifs = null;
                        try {
                            ifs = NetworkInterface.getByName(subproperty).getInetAddresses();
                        } catch (SocketException e) {
                            this.logger.error(e.getMessage());
                        }
                        while (ifs != null && ifs.hasMoreElements() && !found) {
                            InetAddress itf = ifs.nextElement();
                            if (itf instanceof Inet4Address) {
                                String ip = itf.getHostAddress();
                                newVal = newVal.replace(placeholder, ip);
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        throw new NonReplaceableException("Interface " + subproperty + " not available");
                    }
                    break;
                case "json":
                case "file":
                    commonJson = new CommonG();
                    newVal = newVal.replace(placeholder, commonJson.retrieveData(subproperty, property));
                    break;
                default:
                    commonJson = new CommonG();
                    commonJson.getLogger().error("Replacement with an undefined option ({})", property);
                    newVal = newVal.replace(placeholder, "");
            }
        }
        return newVal;
    }

    protected String replaceReflectionPlaceholders(String element, JoinPoint pjp) throws NonReplaceableException {
        String newVal = element;
        while (newVal.contains("!{")) {
            String placeholder = newVal.substring(newVal.indexOf("!{"), newVal.indexOf("}", newVal.indexOf("!{")) + 1);
            String attribute = placeholder.substring(2, placeholder.length() - 1);
            String modifier = attribute.contains(".") ? attribute.substring(attribute.lastIndexOf(".") + 1) : "";

            String prop;
            switch (modifier) {
                case "toLower":
                    attribute = attribute.substring(0, attribute.length() - modifier.length() - 1);
                    prop = ThreadProperty.get(attribute).toLowerCase();
                    break;
                case "toUpper":
                    attribute = attribute.substring(0, attribute.length() - modifier.length() - 1);
                    prop = ThreadProperty.get(attribute).toUpperCase();
                    break;
                default:
                    prop = ThreadProperty.get(attribute);
            }

            if (prop == null) {
                logger.error("{} -> {} local var has not been saved correctly previously.", element, attribute);
                throw new NonReplaceableException("Un-replaceable placeholder: " + placeholder);
            } else {
                newVal = newVal.replace(placeholder, prop);
            }
        }
        return newVal;
    }

    protected String replaceEnvironmentPlaceholders(String element, JoinPoint jp) throws NonReplaceableException {
        String newVal = element;
        while (newVal.contains("${")) {
            String placeholder = newVal.substring(newVal.indexOf("${"),
                    newVal.indexOf("}", newVal.indexOf("${")) + 1);
            String modifier = "";
            String sysProp;
            String defaultValue = "";
            String prop;
            String placeholderAux = "";
            Boolean emptyDefault = false;

            if (placeholder.contains(":-")) {
                defaultValue = placeholder.substring(placeholder.indexOf(":-") + 2, placeholder.length() - 1);
                if ("''".equals(defaultValue)) {
                    emptyDefault = true;
                    defaultValue = "";
                }
                placeholderAux = placeholder.substring(0, placeholder.indexOf(":-")) + "}";
            }

            if (placeholderAux.contains(".")) {
                if (placeholder.contains(":-")) {
                    sysProp = placeholderAux.substring(2, placeholderAux.indexOf("."));
                    modifier = placeholderAux.substring(placeholderAux.indexOf(".") + 1, placeholderAux.length() - 1);
                } else {
                    sysProp = placeholder.substring(2, placeholder.indexOf("."));
                    modifier = placeholder.substring(placeholder.indexOf(".") + 1, placeholder.length() - 1);
                }
            } else {
                if (defaultValue.isEmpty() && !emptyDefault) {
                    if (placeholder.contains(".")) {
                        modifier = placeholder.substring(placeholder.indexOf(".") + 1, placeholder.length() - 1);
                        sysProp = placeholder.substring(2, placeholder.indexOf("."));
                    } else {
                        sysProp = placeholder.substring(2, placeholder.length() - 1);
                    }
                } else {
                    sysProp = placeholder.substring(2, placeholder.indexOf(":-"));
                }
            }

            if (defaultValue.isEmpty()) {
                if (emptyDefault) {
                    prop = System.getProperty(sysProp, defaultValue);
                } else {
                    prop = System.getProperty(sysProp);
                }
            } else {
                prop = System.getProperty(sysProp, defaultValue);
            }

            if (prop == null) {
                logger.error("{} -> {} env var has not been defined.", element, sysProp);
                throw new NonReplaceableException("Unreplaceable placeholder: " + placeholder);
            }

            if ("toLower".equals(modifier)) {
                prop = prop.toLowerCase();
            } else if ("toUpper".equals(modifier)) {
                prop = prop.toUpperCase();
            }
            newVal = newVal.replace(placeholder, prop);
        }

        if (newVal.equalsIgnoreCase("\"\"")) {
            newVal = "";
        }
        return newVal;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Object getSkipMode(Object incomingMode) {
        try {
            return Enum.valueOf((Class<Enum>) incomingMode.getClass(), "SKIP");
        } catch (Exception e) {
            return incomingMode;
        }
    }

    // -----------------------------------------------------------------------
    // Inner classes
    // -----------------------------------------------------------------------

    private static class ReplacedStepData {

        final String replacedText;

        final String dataTableLog;

        final String keyword;

        ReplacedStepData(String replacedText, String dataTableLog, String keyword) {
            this.replacedText = replacedText;
            this.dataTableLog = dataTableLog;
            this.keyword = keyword;
        }
    }

    private static class WrappedPickle implements Pickle {

        private final Pickle delegate;

        private final String name;

        WrappedPickle(Pickle delegate, String name) {
            this.delegate = delegate;
            this.name = name;
        }

        @Override
        public String getKeyword() {
            return delegate.getKeyword();
        }

        @Override
        public String getLanguage() {
            return delegate.getLanguage();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Location getLocation() {
            return delegate.getLocation();
        }

        @Override
        public Location getScenarioLocation() {
            return delegate.getScenarioLocation();
        }

        @Override
        public List<Step> getSteps() {
            return delegate.getSteps();
        }

        @Override
        public List<String> getTags() {
            return delegate.getTags();
        }

        @Override
        public URI getUri() {
            return delegate.getUri();
        }

        @Override
        public String getId() {
            return delegate.getId();
        }
    }

    private static class ReplacedStep implements Step {

        private final Step delegate;

        private final String text;

        private final Argument argument;

        ReplacedStep(Step delegate, String text, Argument argument) {
            this.delegate = delegate;
            this.text = text;
            this.argument = argument;
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
        public String getId() {
            return delegate.getId();
        }

        @Override
        public Argument getArgument() {
            return argument;
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
        public int getLine() {
            return delegate.getLine();
        }

        @Override
        public Location getLocation() {
            return delegate.getLocation();
        }
    }

    private static class ReplacedDataTableArgument implements DataTableArgument {

        private final DataTableArgument delegate;

        private final List<List<String>> cells;

        ReplacedDataTableArgument(DataTableArgument delegate, List<List<String>> cells) {
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

    private static class ReplacedDocStringArgument implements DocStringArgument {

        private final DocStringArgument delegate;

        private final String content;

        ReplacedDocStringArgument(DocStringArgument delegate, String content) {
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
