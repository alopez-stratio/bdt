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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.mockito.ArgumentCaptor;
import org.testng.SkipException;
import org.testng.annotations.Test;

import com.stratio.qa.exceptions.NonReplaceableException;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.core.gherkin.Step;
import io.cucumber.datatable.DataTable;

public class ReplacementAspectTest {

    @Test
    public void replaceEmptyPlaceholdersTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        assertThat(repAspect.replaceEnvironmentPlaceholders("", pjp)).as("Replacing an empty placeholded string should not modify it").isEqualTo("");
    }

    @Test
    public void replaceSinglePlaceholdersTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        System.setProperty("STRATIOBDD_ENV1", "33");
        System.setProperty("STRATIOBDD_ENV2", "aa");

        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}", pjp))
                .as("Unexpected replacement").isEqualTo("33");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2}", pjp))
                .as("Unexpected replacement").isEqualTo("33aa");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}:${STRATIOBDD_ENV2}", pjp))
                .as("Unexpected replacement").isEqualTo("33:aa");
        assertThat(repAspect.replaceEnvironmentPlaceholders("|${STRATIOBDD_ENV1}|:|${STRATIOBDD_ENV2}|", pjp))
                .as("Unexpected replacement").isEqualTo("|33|:|aa|");
    }

    @Test
    public void replaceSinglePlaceholderCaseTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        System.setProperty("STRATIOBDD_ENV1", "33");
        System.setProperty("STRATIOBDD_ENV2", "aA");

        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1.toUpper}", pjp)).as("Unexpected replacement").isEqualTo("33");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1.toLower}", pjp)).as("Unexpected replacement").isEqualTo("33");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2.toUpper}", pjp)).as("Unexpected replacement").isEqualTo("AA");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2.toLower}", pjp)).as("Unexpected replacement").isEqualTo("aa");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2.toLower}", pjp)).as("Unexpected replacement").isEqualTo("33aa");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}:${STRATIOBDD_ENV2.toUpper}", pjp)).as("Unexpected replacement").isEqualTo("33:AA");
        assertThat(repAspect.replaceEnvironmentPlaceholders("|${STRATIOBDD_ENV2}.toUpper", pjp)).as("Unexpected replacement").isEqualTo("|aA.toUpper");
    }

    @Test
    public void replaceElementPlaceholderCaseTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        System.setProperty("STRATIOBDD_ENV4", "33");
        System.setProperty("STRATIOBDD_ENV5", "aA");
        ThreadProperty.set("STRATIOBDD_ENV6", "44");
        ThreadProperty.set("STRATIOBDD_ENV7", "cC");

        assertThat(repAspect.replacedElement("${STRATIOBDD_ENV4}", pjp)).isEqualTo("33");
        assertThat(repAspect.replacedElement("${STRATIOBDD_ENV5.toLower}", pjp)).isEqualTo("aa");
        assertThat(repAspect.replacedElement("${STRATIOBDD_ENV5.toUpper}", pjp)).isEqualTo("AA");
        assertThat(repAspect.replacedElement("${STRATIOBDD_ENV5}", pjp)).isEqualTo("aA");
        assertThat(repAspect.replacedElement("${STRATIOBDD_ENV4}${STRATIOBDD_ENV5}", pjp)).isEqualTo("33aA");
        assertThat(repAspect.replacedElement("${STRATIOBDD_ENV4}:${STRATIOBDD_ENV5}", pjp)).isEqualTo("33:aA");

        assertThat(repAspect.replacedElement("!{STRATIOBDD_ENV6}", pjp)).isEqualTo("44");
        assertThat(repAspect.replacedElement("!{STRATIOBDD_ENV7}", pjp)).isEqualTo("cC");
        assertThat(repAspect.replacedElement("!{STRATIOBDD_ENV6}!{STRATIOBDD_ENV7}", pjp)).isEqualTo("44cC");
        assertThat(repAspect.replacedElement("!{STRATIOBDD_ENV6}:!{STRATIOBDD_ENV7}", pjp)).isEqualTo("44:cC");

        assertThat(repAspect.replacedElement("@{JSON.schemas/simple1.json}", pjp)).isEqualTo("{\"a\":true}");
    }

    @Test
    public void replaceReflectionPlaceholderCaseTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        ThreadProperty.set("test", "aA");
        ThreadProperty.set("test.dot", "bB");
        ThreadProperty.set("test.dot.dot", "cC");
        ThreadProperty.set("test.dot.final.", "dD");
        ThreadProperty.set("test-hyphen", "eE");
        ThreadProperty.set("test_lower_hyphen", "fF");
        ThreadProperty.set("test/slash", "gG");
        ThreadProperty.set("test space", "hH");
        ThreadProperty.set("testUPPER", "iI");


        assertThat(repAspect.replacedElement("!{test}", pjp)).isEqualTo("aA");
        assertThat(repAspect.replacedElement("!{test.dot}", pjp)).isEqualTo("bB");
        assertThat(repAspect.replacedElement("!{test.dot.dot}", pjp)).isEqualTo("cC");
        assertThat(repAspect.replacedElement("!{test.dot.final.}", pjp)).isEqualTo("dD");
        assertThat(repAspect.replacedElement("!{test-hyphen}", pjp)).isEqualTo("eE");
        assertThat(repAspect.replacedElement("!{test_lower_hyphen}", pjp)).isEqualTo("fF");
        assertThat(repAspect.replacedElement("!{test/slash}", pjp)).isEqualTo("gG");
        assertThat(repAspect.replacedElement("!{test space}", pjp)).isEqualTo("hH");
        assertThat(repAspect.replacedElement("!{testUPPER}", pjp)).isEqualTo("iI");
    }

    @Test
    public void replaceReflectionPlaceholderCaseNoValueTest() {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;

        assertThatExceptionOfType(Exception.class).isThrownBy(() -> repAspect.replaceReflectionPlaceholders("!{NO_VAL}", pjp));
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> repAspect.replaceReflectionPlaceholders("!{NO_VAL.toUpper}", pjp));
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> repAspect.replaceReflectionPlaceholders("!{NO_VAL.toLower}", pjp));
    }

    @Test
    public void replaceReflectionPlaceholderCaseSensitiveTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        ThreadProperty.set("test", "aA");
        ThreadProperty.set("Test", "bB");
        ThreadProperty.set("TEST", "cC");

        assertThat(repAspect.replacedElement("!{test}", pjp)).isEqualTo("aA");
        assertThat(repAspect.replacedElement("!{Test}", pjp)).isEqualTo("bB");
        assertThat(repAspect.replacedElement("!{TEST}", pjp)).isEqualTo("cC");
    }

    @Test
    public void replaceReflectionPlaceholderCaseToUpperModifierTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        ThreadProperty.set("test", "aA");
        ThreadProperty.set("test.dot", "bB");
        ThreadProperty.set("test.dot.dot", "cC");
        ThreadProperty.set("test.dot.final.", "dD");
        ThreadProperty.set("test-hyphen", "eE");
        ThreadProperty.set("test_lower_hyphen", "fF");
        ThreadProperty.set("test/slash", "gG");
        ThreadProperty.set("test space", "hH");
        ThreadProperty.set("testUPPER", "iI");


        assertThat(repAspect.replacedElement("!{test.toUpper}", pjp)).isEqualTo("AA");
        assertThat(repAspect.replacedElement("!{test.dot.toUpper}", pjp)).isEqualTo("BB");
        assertThat(repAspect.replacedElement("!{test.dot.dot.toUpper}", pjp)).isEqualTo("CC");
        assertThat(repAspect.replacedElement("!{test.dot.final..toUpper}", pjp)).isEqualTo("DD");
        assertThat(repAspect.replacedElement("!{test-hyphen.toUpper}", pjp)).isEqualTo("EE");
        assertThat(repAspect.replacedElement("!{test_lower_hyphen.toUpper}", pjp)).isEqualTo("FF");
        assertThat(repAspect.replacedElement("!{test/slash.toUpper}", pjp)).isEqualTo("GG");
        assertThat(repAspect.replacedElement("!{test space.toUpper}", pjp)).isEqualTo("HH");
        assertThat(repAspect.replacedElement("!{testUPPER.toUpper}", pjp)).isEqualTo("II");
    }
    
    @Test
    public void replaceReflectionPlaceholderCaseToLowerModifierTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;
        ThreadProperty.set("test", "aA");
        ThreadProperty.set("test.dot", "bB");
        ThreadProperty.set("test.dot.dot", "cC");
        ThreadProperty.set("test.dot.final.", "dD");
        ThreadProperty.set("test-hyphen", "eE");
        ThreadProperty.set("test_lower_hyphen", "fF");
        ThreadProperty.set("test/slash", "gG");
        ThreadProperty.set("test space", "hH");
        ThreadProperty.set("testUPPER", "iI");


        assertThat(repAspect.replacedElement("!{test.toLower}", pjp)).isEqualTo("aa");
        assertThat(repAspect.replacedElement("!{test.dot.toLower}", pjp)).isEqualTo("bb");
        assertThat(repAspect.replacedElement("!{test.dot.dot.toLower}", pjp)).isEqualTo("cc");
        assertThat(repAspect.replacedElement("!{test.dot.final..toLower}", pjp)).isEqualTo("dd");
        assertThat(repAspect.replacedElement("!{test-hyphen.toLower}", pjp)).isEqualTo("ee");
        assertThat(repAspect.replacedElement("!{test_lower_hyphen.toLower}", pjp)).isEqualTo("ff");
        assertThat(repAspect.replacedElement("!{test/slash.toLower}", pjp)).isEqualTo("gg");
        assertThat(repAspect.replacedElement("!{test space.toLower}", pjp)).isEqualTo("hh");
        assertThat(repAspect.replacedElement("!{testUPPER.toLower}", pjp)).isEqualTo("ii");
    }

    // -----------------------------------------------------------------------
    // QATM-55224: aroundReplacementExecute must throw instead of silently
    // passing unresolved literals through to the step body.
    // Tests below FAIL before the fix and PASS after it.
    // -----------------------------------------------------------------------

    @Test
    public void executeTimeReplacementThrowsForUndefinedThreadVar() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        Object[] stepArgs = new Object[]{"value-!{BDDTEST_EXEC_UNDEFINED_THREAD_VAR}-end"};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{stepArgs});

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> aspect.aroundReplacementExecute(pjp));

        verify(pjp, never()).proceed();
        verify(pjp, never()).proceed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void executeTimeReplacementThrowsForUndefinedEnvVar() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        System.clearProperty("BDDTEST_EXEC_UNDEFINED_ENV_VAR");
        Object[] stepArgs = new Object[]{"value-${BDDTEST_EXEC_UNDEFINED_ENV_VAR}-end"};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{stepArgs});

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> aspect.aroundReplacementExecute(pjp));

        verify(pjp, never()).proceed();
        verify(pjp, never()).proceed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void executeTimeReplacementThrowsForUndefinedThreadVarInDataTable() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        List<List<String>> cells = Arrays.asList(
                Arrays.asList("$.key", "ADD", "!{BDDTEST_EXEC_UNDEFINED_DT_THREAD_VAR}", "string")
        );
        DataTable dataTable = DataTable.create(cells);
        Object[] stepArgs = new Object[]{dataTable};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{stepArgs});

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> aspect.aroundReplacementExecute(pjp));

        verify(pjp, never()).proceed();
        verify(pjp, never()).proceed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void executeTimeReplacementThrowsForUndefinedEnvVarInDataTable() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        System.clearProperty("BDDTEST_EXEC_UNDEFINED_DT_ENV_VAR");
        List<List<String>> cells = Arrays.asList(
                Arrays.asList("$.key", "ADD", "${BDDTEST_EXEC_UNDEFINED_DT_ENV_VAR}", "string")
        );
        DataTable dataTable = DataTable.create(cells);
        Object[] stepArgs = new Object[]{dataTable};
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{stepArgs});

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> aspect.aroundReplacementExecute(pjp));

        verify(pjp, never()).proceed();
        verify(pjp, never()).proceed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void replaceCodePlaceholderCaseTest() throws NonReplaceableException {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ReplacementAspect repAspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = null;

        assertThat(repAspect.replaceCodePlaceholders("@{schemas/simple1.json}", pjp)).isEqualTo("");
        assertThat(repAspect.replaceCodePlaceholders("@{JSON.schemas/simple1.json}", pjp)).isEqualTo("{\"a\":true}");
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> repAspect.replaceCodePlaceholders("@{IP.10.10.10.10}", pjp));
    }

    @Test
    public void replaceMixedPlaceholdersTest() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ThreadProperty.set("STRATIOBDD_LOCAL1", "LOCAL");
        ProceedingJoinPoint pjp = null;
        ReplacementAspect repAspect = new ReplacementAspect();
        System.setProperty("STRATIOBDD_ENV2", "aa");

        assertThat(repAspect.replaceReflectionPlaceholders(repAspect.replaceEnvironmentPlaceholders("!{STRATIOBDD_LOCAL1}:${STRATIOBDD_ENV2}", pjp), pjp))
                .as("Unexpected replacement").isEqualTo("LOCAL:aa");
        assertThat(repAspect.replaceReflectionPlaceholders(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2}:!{STRATIOBDD_LOCAL1}", pjp), pjp))
                .as("Unexpected replacement").isEqualTo("aa:LOCAL");
    }

    @Test
    public void replaceDefaultValue() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ProceedingJoinPoint pjp = null;
        ReplacementAspect repAspect = new ReplacementAspect();
        System.setProperty("STRATIOBDD_ENV1", "aa");
        System.setProperty("STRATIOBDD_ENV3", "cc");

        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1:-bb}", pjp)).as("Unexpected replacement").isEqualTo("aa");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2:-bb}", pjp)).as("Unexpected replacement").isEqualTo("bb");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV4:-dd}", pjp)).as("Unexpected replacement").isEqualTo("bbdd");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV1}", pjp)).as("Unexpected replacement").isEqualTo("bbaa");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2:-bb}", pjp)).as("Unexpected replacement").isEqualTo("aabb");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV3}", pjp)).as("Unexpected replacement").isEqualTo("aabbcc");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1.toUpper}${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV3}", pjp)).as("Unexpected replacement").isEqualTo("AAbbcc");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2.toUpper:-bb}${STRATIOBDD_ENV3}", pjp)).as("Unexpected replacement").isEqualTo("aaBBcc");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV3:-aa}", pjp)).as("Unexpected replacement").isEqualTo("aabbcc");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV3.toUpper:-aa}", pjp)).as("Unexpected replacement").isEqualTo("aabbCC");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2:-bb.bb}${STRATIOBDD_ENV3:-aa}", pjp)).as("Unexpected replacement").isEqualTo("aabb.bbcc");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV1}${STRATIOBDD_ENV2:-bb}${STRATIOBDD_ENV3:-aa.aa}", pjp)).as("Unexpected replacement").isEqualTo("aabbcc");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2:-\"\"}", pjp)).as("Unexpected replacement").isEqualTo("");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2:-{}}", pjp)).as("Unexpected replacement").isEqualTo("{}");
        assertThat(repAspect.replaceEnvironmentPlaceholders("${STRATIOBDD_ENV2:-[]}", pjp)).as("Unexpected replacement").isEqualTo("[]");
    }

    @Test
    public void replaceThreadValue() throws Exception {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ThreadProperty.set("threadVar1", "Upper");
        ProceedingJoinPoint pjp = null;
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThat(repAspect.replacedElement("!{threadVar1}", pjp)).isEqualTo("Upper");
        assertThat(repAspect.replacedElement("!{threadVar1.toLower}", pjp)).isEqualTo("upper");
        assertThat(repAspect.replacedElement("!{threadVar1.toUpper}", pjp)).isEqualTo("UPPER");
    }

    // -----------------------------------------------------------------------
    // suppressSnippetForRuntimeVars tests
    // -----------------------------------------------------------------------

    @Test
    public void suppressSnippet_stepWithRuntimeVar_suppressesSnippet() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn(
                "in less than '500' seconds, checking each '10' seconds, custom resource 'pgbouncers' with name 'pool-psql' in namespace 's000001-datastores' has '!{instancesActual}' replicas ready");
        when(pjp.getArgs()).thenReturn(new Object[]{mock(io.cucumber.core.gherkin.Pickle.class), step});

        Object result = aspect.suppressSnippetForRuntimeVars(pjp);

        assertThat(result).isNull();
        verify(pjp, never()).proceed();
    }

    @Test
    public void suppressSnippet_stepWithRuntimeVarInResourceName_suppressesSnippet() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn(
                "in less than '500' seconds, checking each '10' seconds, we have '!{instancesActual}' pod/s with label filter 'app=foo' in namespace 'bar' having 'running' status and 'ready' state");
        when(pjp.getArgs()).thenReturn(new Object[]{mock(io.cucumber.core.gherkin.Pickle.class), step});

        Object result = aspect.suppressSnippetForRuntimeVars(pjp);

        assertThat(result).isNull();
        verify(pjp, never()).proceed();
    }

    @Test
    public void suppressSnippet_stepWithoutRuntimeVar_proceedsNormally() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn(
                "in less than '500' seconds, checking each '10' seconds, deployment with name 'my-deploy' in namespace 'my-ns' has '3' replicas ready");
        when(pjp.getArgs()).thenReturn(new Object[]{mock(io.cucumber.core.gherkin.Pickle.class), step});

        aspect.suppressSnippetForRuntimeVars(pjp);

        verify(pjp).proceed();
    }

    @Test
    public void suppressSnippet_stepWithEnvVarButNoRuntimeVar_proceedsNormally() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn(
                "in less than '${TIMEOUT:-500}' seconds, checking each '10' seconds, deployment with name 'my-deploy' in namespace 'my-ns' has '3' replicas ready");
        when(pjp.getArgs()).thenReturn(new Object[]{mock(io.cucumber.core.gherkin.Pickle.class), step});

        aspect.suppressSnippetForRuntimeVars(pjp);

        verify(pjp).proceed();
    }

    @Test
    public void suppressSnippet_nullArgs_proceedsNormally() throws Throwable {
        ReplacementAspect aspect = new ReplacementAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{});

        aspect.suppressSnippetForRuntimeVars(pjp);

        verify(pjp).proceed();
    }

    /**
     * Reproduces CROSSDATA-10434: a multi-line step argument (e.g. a SQL docstring) that contains a
     * placeholder is routed through {@link ReplacementAspect#aroundReplacementExecute} at execute time.
     * There the resolved string has all real newlines collapsed into the literal two-char sequence
     * backslash+n by {@code replaceAll("[\\r\\n]+", "\\\\n")}. For SQL this breaks single-line ({@code --})
     * and block ({@code /* *}{@code /}) comments: with no real newline the {@code --} comment swallows the
     * rest of the query and the stray backslash is an invalid token, so the engine fails to parse it.
     * The same query typed by hand in DBeaver (no placeholder, no aspect) keeps its newlines and works.
     */
    @Test
    public void docStringWithPlaceholderMustPreserveNewlinesTest() throws Throwable {
        ThreadProperty.set("class", this.getClass().getCanonicalName());
        ThreadProperty.set("ts", "12345");
        ReplacementAspect repAspect = new ReplacementAspect();

        String multilineSql =
                "SELECT id, -- primary key\n" +
                "       name\n" +
                "FROM test!{ts}\n" +
                "WHERE id > 1";

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Object[] stepArgs = new Object[]{multilineSql};
        when(pjp.getArgs()).thenReturn(new Object[]{stepArgs});

        repAspect.aroundReplacementExecute(pjp);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(pjp).proceed(captor.capture());
        Object[] newArgs = (Object[]) captor.getValue()[0];
        String resolved = (String) newArgs[0];

        assertThat(resolved).as("placeholder must be resolved").contains("test12345");
        assertThat(resolved).as("real newlines must be preserved").contains("\n");
        assertThat(resolved).as("newlines must NOT be escaped to the literal sequence backslash+n").doesNotContain("\\n");
    }

    // -----------------------------------------------------------------------
    // @{...} placeholders: the IP form and the failure paths
    // -----------------------------------------------------------------------

    @Test
    public void replaceCodePlaceholderWithoutSubpropertyThrows() {
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThatExceptionOfType(NonReplaceableException.class)
                .as("@{X} carries no source to read from, so it cannot be resolved")
                .isThrownBy(() -> repAspect.replaceCodePlaceholders("@{NOSUBPROPERTY}", null))
                .withMessageContaining("@{NOSUBPROPERTY}");
    }

    @Test
    public void replaceCodePlaceholderWithEmptyInterfaceThrows() {
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> repAspect.replaceCodePlaceholders("@{IP.}", null))
                .withMessageContaining("not available");
    }

    @Test
    public void replaceCodePlaceholderResolvesTheIpv4OfALocalInterface() throws Exception {
        NetworkInterface itf = firstInterfaceWithIpv4();
        if (itf == null) {
            throw new SkipException("no network interface with an IPv4 address on this host");
        }
        String expected = firstIpv4Of(itf);
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThat(repAspect.replaceCodePlaceholders("host=@{IP." + itf.getName() + "}", null))
                .isEqualTo("host=" + expected);
    }

    // -----------------------------------------------------------------------
    // ${VAR:-''}: an explicitly empty default is not the same as no default
    // -----------------------------------------------------------------------

    @Test
    public void replaceEnvironmentPlaceholderWithEmptyDefault() throws Exception {
        ReplacementAspect repAspect = new ReplacementAspect();
        System.clearProperty("STRATIOBDD_EMPTY_DEFAULT");

        assertThat(repAspect.replaceEnvironmentPlaceholders("a${STRATIOBDD_EMPTY_DEFAULT:-''}b", null))
                .as("an undefined var with an empty default must collapse to nothing, not fail")
                .isEqualTo("ab");

        System.setProperty("STRATIOBDD_EMPTY_DEFAULT", "value");
        try {
            assertThat(repAspect.replaceEnvironmentPlaceholders("a${STRATIOBDD_EMPTY_DEFAULT:-''}b", null))
                    .as("the default only applies when the var is undefined")
                    .isEqualTo("avalueb");
        } finally {
            System.clearProperty("STRATIOBDD_EMPTY_DEFAULT");
        }
    }

    // -----------------------------------------------------------------------
    // Failure paths of replacedElement / replacedElementSkipReflection
    // -----------------------------------------------------------------------

    @Test
    public void replacedElementSkipReflectionDefersRuntimeAndCodePlaceholders() throws Exception {
        ThreadProperty.set("STRATIOBDD_DEFERRED", "resolved");
        System.setProperty("STRATIOBDD_MATCH_TIME", "now");
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThat(repAspect.replacedElementSkipReflection(
                "${STRATIOBDD_MATCH_TIME} !{STRATIOBDD_DEFERRED} @{JSON.schemas/simple1.json}", null))
                .as("only ${ENV} is resolved at match time")
                .isEqualTo("now !{STRATIOBDD_DEFERRED} @{JSON.schemas/simple1.json}");
    }

    @Test
    public void replacedElementSkipReflectionRethrowsUnresolvableEnvVars() {
        ReplacementAspect repAspect = new ReplacementAspect();
        System.clearProperty("STRATIOBDD_SKIPREFLECTION_UNDEFINED");

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> repAspect.replacedElementSkipReflection("${STRATIOBDD_SKIPREFLECTION_UNDEFINED}", null))
                .withMessageContaining("Unreplaceable placeholder");
    }

    @Test
    public void replacedElementSkipReflectionWrapsUnexpectedFailures() {
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThatExceptionOfType(NonReplaceableException.class)
                .as("a placeholder that is never closed blows up inside the parser, not with our own exception")
                .isThrownBy(() -> repAspect.replacedElementSkipReflection("${UNCLOSED", null))
                .withMessageContaining("Unreplaceable element");
    }

    @Test
    public void replacedElementWrapsUnexpectedFailures() {
        ReplacementAspect repAspect = new ReplacementAspect();

        assertThatExceptionOfType(NonReplaceableException.class)
                .isThrownBy(() -> repAspect.replacedElement("${UNCLOSED", null))
                .withMessageContaining("Unreplaceable element");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * @return the first interface this host exposes with an IPv4 address, in the same order
     *         {@code replaceCodePlaceholders} walks them, or {@code null} if there is none
     */
    private static NetworkInterface firstInterfaceWithIpv4() throws SocketException {
        for (NetworkInterface itf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (firstIpv4Of(itf) != null) {
                return itf;
            }
        }
        return null;
    }

    private static String firstIpv4Of(NetworkInterface itf) {
        for (InetAddress address : Collections.list(itf.getInetAddresses())) {
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
        }
        return null;
    }
}
