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
package com.stratio.qa.specs;

import io.cucumber.cucumberexpressions.Group;
import io.cucumber.java.en.When;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the step expressions of the composite Postgres steps. The SQL splitting they delegate to
 * is covered by {@code SqlStatementSplitterTest}.
 * <p>
 * cucumber-expressions builds one argument per top-level capture group, so nesting two captures in
 * a single optional clause silently loses a parameter and the step blows up with an arity mismatch
 * the first time a feature matches it.
 */
public class DatabaseSpecPostgresQueriesTest {

    @Test
    public void queriesStepExpressionHasOneTopLevelGroupPerParameter() {
        // every parameter but the docstring comes from a top-level capture group
        assertThat(topLevelGroupsOf("executeQueriesInKubernetesPostgres", 10)).isEqualTo(9);
    }

    @Test
    public void sqlFileStepExpressionHasOneTopLevelGroupPerParameter() {
        assertThat(topLevelGroupsOf("executeSqlFileInKubernetesPostgres", 10)).isEqualTo(10);
    }

    private int topLevelGroupsOf(String methodName, int expectedParameters) {
        Method step = Arrays.stream(DatabaseSpec.class.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No step named " + methodName));
        assertThat(step.getParameterCount()).as("parameters of " + methodName).isEqualTo(expectedParameters);
        return Group.parse(Pattern.compile(step.getAnnotation(When.class).value())).size();
    }
}
