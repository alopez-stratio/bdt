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

import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.core.gherkin.Pickle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
public class RunOnTagAspect {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    // Matches X.Y.Z-(m|rc).N or X.Y.Z-U.V.W-(m|rc).N (new semver qualifier formats)
    private static final Pattern SEMVER_QUALIFIER_PATTERN =
            Pattern.compile("^(.+)-(m|rc)\\.([1-9][0-9]*)$");

    // Strips legacy build qualifiers (SNAPSHOT, git hashes, M/RC, PR-style) from a version string
    private static final String LEGACY_QUALIFIER_STRIP =
            "-(SNAPSHOT|[a-zA-Z0-9]{7}|M[1-9]*[0-9]|RC[1-9]*[0-9]|m\\.[1-9][0-9]*|rc\\.[1-9][0-9]*|[a-zA-Z]+[0-9]+)";

    @Pointcut("execution (* io.cucumber.core.runner.Runner.runPickle(..)) && "
            + "args (pickle)")
    protected void AddRunOnTagPointcutScenario(Pickle pickle) {
    }

    /**
     * Allows conditional scenario execution.
     * If the scenario contains the following tag:
     * <dl>
     *    <dt>\@runOnEnv(param)</dt>
     *<dd>The scenario will only be executed if the param is defined when test is launched. Configuration map object.
     * More than one param can be passed in the tag. To do so, the params must be comma separated:
     * \@runOnEnv(param): The scenario will only be executed if the param is defined when test is launched.
     * \@runOnEnv(param1,param2,param3): The scenario will only be executed if ALL the params are defined.
     * </dd>
     * </dl>
     * Additionally, if the scenario contains the following tag:
     * <dl>
     *    <dt>\@skipOnEnv(param)</dt>
     *<dd>The scenario will be omitted if the param is defined when test is launched.
     * More than one param can be passed in the tag. To do so, the params must be comma separated.
     * The scenario will omitted if ANY of params are defined. (OR)</dd>
     *
     *<dd>Or in separated lines to force ALL of the params to be defined in order to omit the execution</dd>
     *    <dt>  \@skipOnEnv(param1)
     *          \@skipOnEnv(param2)
     *          \@skipOnEnv(param3)</dt>
     *<dd>The scenario will omitted if ALL of params are defined. (AND)</dd>
     *</dl>
     *
     * <p>A param can also compare the value of a property, with any of these operators:
     * <dl>
     *     <dt>=</dt><dd>equal</dd>
     *     <dt>&gt;</dt><dd>greater than</dd>
     *     <dt>&lt;</dt><dd>lower than</dd>
     *     <dt>&gt;=</dt><dd>greater than or equal</dd>
     *     <dt>&lt;=</dt><dd>lower than or equal</dd>
     *     <dt>!=</dt><dd>not equal</dd>
     * </dl>
     * Values that look like a version (they contain a dot) are compared element by element, ignoring a
     * -SNAPSHOT, -M&lt;n&gt;, -RC&lt;n&gt; or commit-hash suffix; numeric values are compared as numbers and
     * anything else is compared as a string. An undefined property makes every comparison false,
     * "!=" included.
     *
     * @param pjp ProceedingJoinPoint
     * @param pickle pickle
     * @throws Throwable exception
     */
    @Around(value = "AddRunOnTagPointcutScenario(pickle)")
    public void aroundAddRunOnTagPointcut(ProceedingJoinPoint pjp, Pickle pickle) throws Throwable {
        int line = pickle.getLocation().getLine();
        try {
            boolean exit = tagsIteration(pickle.getTags(), line);
            if (exit) {
                ThreadProperty.set("skippedOnParams" + pickle.getName() + line, "true");
                return;
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            return;
        }
        pjp.proceed();
    }

    public boolean tagsIteration(List<String> tags, Integer line) throws Exception {
        for (String tag : tags) {
            if (tag.contains("@runOnEnv")) {
                if (!checkParams(getParams(tag))) {
                    return true;
                }
            } else if (tag.contains("@skipOnEnv")) {
                if (checkParams(getParams(tag))) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
    * Returns a string array of params
    */
    public String[][] getParams(String s) throws Exception {
        String delimeters = ",|&&|\\|\\|";
        String[] val = s.substring((s.lastIndexOf("(") + 1), (s.length()) - 1).split(delimeters);
        if (val[0].startsWith("@")) {
            throw new Exception ("Error while parsing params. Format is: \"runOnEnv(PARAM)\", but found: " + s);
        }

        String[] ops;
        // Only valid operators: AND -> &&; OR -> ||
        // Valid variables names and values can only contain: characters, numbers, underscores, hyphens, dots and equal
        if (s.contains("&&") || s.contains("||")) {
            // Normalise the comparison operators before extracting the conditional ones, so that neither the
            // '!' of "!=" nor the '!' that marks a value as a reference to another property is read as a
            // conditional operator. A stray '!' anywhere else is still reported as an invalid operator.
            String normalised = s.substring((s.lastIndexOf("(") + 1), (s.length()) - 1)
                    .replace("!=", "=").replaceAll("([<>=])!", "$1");
            ops = normalised.split("[a-zA-Z._\\-0-9=<>]+");
            if (ops.length > 0) {
                ops = Arrays.copyOfRange(ops, 1, ops.length);
            }
            if (ops.length != val.length - 1) {
                throw new Exception("Error in expression. Number of conditional operators plus 1 should be equal to the number of expressions.");
            }
            for (String op: ops) {
                if (!("||".equals(op) || "&&".equals(op))) {
                    throw new Exception("Error in conditional operators. Operators should be && or ||.");
                }
            }
        } else {
            ops = new String[] {};
        }

        String[][] result = new String[][] {val, ops};
        return result;
    }

    /**
     * Comparison operators recognised inside an expression, longest first so that ">=" is not read as
     * "=" followed by a parameter ending in ">".
     */
    private static final String[] OPERATORS = {">=", "<=", "!=", "=", ">", "<"};

    /**
     * Returns the comparison operator present in the expression, or null when the expression is just a
     * parameter name.
     */
    private static String operatorIn(String element) {
        for (String operador : OPERATORS) {
            if (element.contains(operador)) {
                return operador;
            }
        }
        return null;
    }

    /** Whether the operator is one of the ones evaluated as a combination of the basic three. */
    private static boolean isCompositeOperator(String operador) {
        return ">=".equals(operador) || "<=".equals(operador) || "!=".equals(operador);
    }

    /** Replaces the first occurrence of an operator by another one, leaving the value untouched. */
    private static String replaceOperator(String element, String from, String to) {
        int i = element.indexOf(from);
        return element.substring(0, i) + to + element.substring(i + from.length());
    }

    /** Whether the property named by the parameter has a value, either as a system or a thread property. */
    private static boolean isDefined(String param) {
        return !System.getProperty(param, "").isEmpty() || ThreadProperty.get(param) != null;
    }

    /**
     * Evaluates one "PARAM<operator>VALUE" expression on its own.
     * <p>
     * ">=", "<=" and "!=" are evaluated as combinations of "=", ">" and "<", so the version comparison
     * in {@link #checkVersion} stays untouched.
     */
    private boolean evaluateElement(String element, String operador) throws Exception {
        switch (operador) {
            case ">=":
                return firstElementOperator(replaceOperator(element, ">=", ">"), ">")
                        || firstElementOperator(replaceOperator(element, ">=", "="), "=");
            case "<=":
                return firstElementOperator(replaceOperator(element, "<=", "<"), "<")
                        || firstElementOperator(replaceOperator(element, "<=", "="), "=");
            case "!=":
                // An undefined property makes every comparison false, "!=" included: with no value there
                // is nothing to compare, and a scenario must not be run on a guess.
                return isDefined(element.substring(0, element.indexOf("!=")))
                        && !firstElementOperator(replaceOperator(element, "!=", "="), "=");
            default:
                return firstElementOperator(element, operador);
        }
    }

   /*
    * Checks if every param in the array of strings is defined
    */
    public boolean checkParams(String[][] params) throws Exception {
        if ("".equals(params[0][0])) {
            throw new Exception("Error while parsing params. Params must be at least one");
        }

        boolean result = true;
        // Primer elemento
        String firstOperator = operatorIn(params[0][0]);
        if (firstOperator != null) {
            result = evaluateElement(params[0][0], firstOperator);
        } else if (!isDefined(params[0][0])) {
            result = false;
        }

        // Elementos intermedios
        for (int j = 1; j < params[0].length - 1; j++) {
            String operador = operatorIn(params[0][j]);
            if (operador != null) {
                result = elementOperator(params[0][j], operador, params[1], j - 1, result);
            } else {
                result = updateResultOperation(params[1], j - 1, result, isDefined(params[0][j]));
            }
        }

        // Último elemento
        if (params[0].length > 1) {
            String lastElement = params[0][params[0].length - 1];
            String lastOperator = operatorIn(lastElement);
            if (lastOperator != null) {
                result = elementOperator(lastElement, lastOperator, params[1], params[1].length - 1, result);
            } else {
                result = updateResultOperation(params[1], params[1].length - 1, result, isDefined(lastElement));
            }
        }
        return result;
    }

    private boolean firstElementOperator(String element, String operador) throws Exception {
        if (isCompositeOperator(operador)) {
            return evaluateElement(element, operador);
        }
        boolean result = true;
        String param = element.split(operador)[0];
        String value = element.split(operador)[1];
        if (value.startsWith("!")) {
            value = System.getProperty(value.substring(1)) != null ? System.getProperty(value.substring(1), "") : ThreadProperty.get(value.substring(1)) != null ? ThreadProperty.get(value.substring(1)) : "";
        }
        String property = System.getProperty(param) != null ? System.getProperty(param, "") : ThreadProperty.get(param) != null ? ThreadProperty.get(param) : "";
        if (property.isEmpty()) {
            result = false;
        } else if (value.contains(".") && property.contains(".")) {
            if (!checkVersion(operador.charAt(0), param, value)) {
                result = false;
            }
        } else if (isNumeric(value) && isNumeric(property)) {
            int iValue = Integer.parseInt(value);
            int iProperty = Integer.parseInt(property);
            if ((operador.equals("=") && !(iProperty == iValue)) || (operador.equals(">") && !(iProperty > iValue)) || (operador.equals("<") && !(iProperty < iValue))) {
                result = false;
            }
        } else if ((operador.equals("=") && !value.equals(property)) || (operador.equals(">") && !(property.compareTo(value) > 0)) || (operador.equals("<") && !(property.compareTo(value) < 0))) {
            result = false;
        }
        return result;
    }

    private boolean elementOperator(String element, String operador, String[] operations, int posop, boolean result) throws Exception {
        if (isCompositeOperator(operador)) {
            return updateResultOperation(operations, posop, result, evaluateElement(element, operador));
        }
        boolean res = result;
        String param = element.split(operador)[0];
        String value = element.split(operador)[1];
        if (value.startsWith("!")) {
            value = System.getProperty(value.substring(1)) != null ? System.getProperty(value.substring(1), "") : ThreadProperty.get(value.substring(1)) != null ? ThreadProperty.get(value.substring(1)) : "";
        }
        String property = System.getProperty(param) != null ? System.getProperty(param, "") : ThreadProperty.get(param) != null ? ThreadProperty.get(param) : "";
        if (property.isEmpty()) {
            res =  updateResultOperation(operations, posop, result, false);
        } else if (value.contains(".") && property.contains(".")) {
            if (!checkVersion(operador.charAt(0), param, value)) {
                res =  updateResultOperation(operations, posop, result, false);
            } else {
                res =  updateResultOperation(operations, posop, result, true);
            }
        } else if (isNumeric(value) && isNumeric(property)) {
            int iValue = Integer.parseInt(value);
            int iProperty = Integer.parseInt(property);
            if ((operador.equals("=") && !(iProperty == iValue)) || (operador.equals(">") && !(iProperty > iValue)) || (operador.equals("<") && !(iProperty < iValue))) {
                res =  updateResultOperation(operations, posop, result, false);
            } else  {
                res =  updateResultOperation(operations, posop, result, true);
            }
        } else if (operador.equals("=")) {
            if (!value.equals(property)) {
                res =  updateResultOperation(operations, posop, result, false);
            } else {
                res =  updateResultOperation(operations, posop, result, true);
            }
        } else if (operador.equals(">")) {
            if (!(property.compareTo(value) > 0)) {
                res =  updateResultOperation(operations, posop, result, false);
            } else {
                res =  updateResultOperation(operations, posop, result, true);
            }
        } else if (operador.equals("<")) {
            if (!(property.compareTo(value) < 0)) {
                res =  updateResultOperation(operations, posop, result, false);
            } else {
                res =  updateResultOperation(operations, posop, result, true);
            }
        }
        return res;
    }

    private boolean updateResultOperation (String[] param, int pos, boolean result, boolean valor) throws Exception {
        if (param.length == 0) {
            return result && valor;
        } else if ("&&".equals(param[pos])) {
            return result && valor;
        } else {
            return result || valor;
        }
    }

    private boolean checkVersion(char operador, String param, String value) throws Exception {
        boolean result = true;
        String regexp = "^[[[0-9]+.]+[0-9]+][-[[0-9]+.]+[0-9]+]*";
        String property = System.getProperty(param) != null ? System.getProperty(param, "") : ThreadProperty.get(param) != null ? ThreadProperty.get(param) : "";

        if (hasSemverQualifier(property) || hasSemverQualifier(value)) {
            return checkVersionSemver(operador, property, value);
        }

        String envVarValue = stripLegacyQualifiers(property);
        if (!Pattern.matches(regexp, envVarValue) || !Pattern.matches(regexp, value)) {
            throw new Exception("Error while parsing params. The versions have some characters that are not numbers, '.' or '-' or an invalid format");
        } else if (operador == '=') {
            if (value.contains("-") || envVarValue.contains("-")) {
                String[] paramversion = envVarValue.split("-");
                String[] valueversion = value.split("-");
                if (paramversion.length != valueversion.length) {
                    result = false;
                } else {
                    int j = 0;
                    while (j < paramversion.length && result) {
                        String[] parver = paramversion[j].split("\\.");
                        String[] valver = valueversion[j].split("\\.");
                        if (parver.length != valver.length) {
                            result = false;
                        } else {
                            int z = 0;
                            while (z < parver.length && result) {
                                if (Integer.parseInt(parver[z]) != Integer.parseInt(valver[z])) {
                                    result = false;
                                }
                                z++;
                            }
                        }
                        j++;
                    }
                }
            } else {
                String[] parver = envVarValue.split("\\.");
                String[] valver = value.split("\\.");
                if (parver.length != valver.length) {
                    result = false;
                } else {
                    int z = 0;
                    while (z < parver.length && result) {
                        if (Integer.parseInt(parver[z]) != Integer.parseInt(valver[z])) {
                            result = false;
                        }
                        z++;
                    }
                }
            }
        } else {
            if ((value.contains("-") || envVarValue.contains("-"))) {
                String[] paramversion = envVarValue.split("-");
                String[] valueversion = value.split("-");
                if (operador == '>' && paramversion.length < valueversion.length) {
                    result = false;
                } else if (operador == '<' && paramversion.length > valueversion.length) {
                    result = false;
                } else {
                    int size = paramversion.length;
                    if (valueversion.length < size) {
                        size = valueversion.length;
                    }
                    int countversion = 0;
                    int j = 0;
                    while (j < size && result) {
                        String[] parver = paramversion[j].split("\\.");
                        String[] valver = valueversion[j].split("\\.");
                        if (parver.length != valver.length) {
                            throw new Exception("Error while parsing params. The versions must have the same number of elements");
                        } else {
                            int count = 0;
                            int z = 0;
                            while (z < parver.length && result) {
                                if (operador == '>' && Integer.parseInt(parver[z]) < Integer.parseInt(valver[z])) {
                                    result = false;
                                } else if (operador == '<' && Integer.parseInt(parver[z]) > Integer.parseInt(valver[z])) {
                                    result = false;
                                } else if (Integer.parseInt(parver[z]) == Integer.parseInt(valver[z])) {
                                    count = count + 1;
                                } else {
                                    z = parver.length;
                                    j = size;
                                }
                                z++;
                            }
                            if (count == parver.length) {
                                countversion = countversion + 1;
                            }
                        }
                        j++;
                    }
                    if (countversion == size && paramversion.length == valueversion.length) {
                        result = false;
                    }
                }
            } else {
                String[] parver = envVarValue.split("\\.");
                String[] valver = value.split("\\.");
                if (parver.length != valver.length) {
                    throw new Exception("Error while parsing params. The versions must have the same number of elements");
                }
                int count = 0;
                int z = 0;
                while (z < parver.length && result) {
                    if (operador == '>' && Integer.parseInt(parver[z]) < Integer.parseInt(valver[z])) {
                        result = false;
                    } else if (operador == '<' && Integer.parseInt(parver[z]) > Integer.parseInt(valver[z])) {
                        result = false;
                    } else if (Integer.parseInt(parver[z]) == Integer.parseInt(valver[z])) {
                        count = count + 1;
                    } else {
                        z = parver.length;
                    }
                    z++;
                }
                if (count == parver.length) {
                    result = false;
                }
            }
        }
        return result;
    }

    private String stripLegacyQualifiers(String version) {
        return version
                .replaceAll("-SNAPSHOT+[0-9a-zA-Z]", "error")
                .replaceAll("-([a-zA-Z0-9]{7}[0-9]|M[1-9]*[0-9][a-zA-Z]|RC[1-9]*[0-9][a-zA-Z])", "error")
                .replaceAll(LEGACY_QUALIFIER_STRIP, "");
    }

    private boolean hasSemverQualifier(String version) {
        return version != null && SEMVER_QUALIFIER_PATTERN.matcher(version).matches();
    }

    /**
     * Compares two version strings that may include new semver qualifiers (m.N, rc.N).
     * Qualifier ordering: m.N < rc.N < release (no qualifier).
     * Supports base versions like X.Y.Z or X.Y.Z-U.V.W.
     */
    private boolean checkVersionSemver(char operador, String property, String value) throws Exception {
        String regexp = "^[[[0-9]+.]+[0-9]+][-[[0-9]+.]+[0-9]+]*";

        String propBase;
        int propRank;
        int propQualNum;
        Matcher propMatcher = SEMVER_QUALIFIER_PATTERN.matcher(property);
        if (propMatcher.matches()) {
            propBase = propMatcher.group(1);
            propRank = "m".equals(propMatcher.group(2)) ? 0 : 1;
            propQualNum = Integer.parseInt(propMatcher.group(3));
        } else {
            // Strip legacy qualifiers so mixed semver+legacy comparisons work (e.g. 1.0.0-SNAPSHOT vs 1.0.0-rc.1)
            propBase = stripLegacyQualifiers(property);
            propRank = 2;
            propQualNum = 0;
        }

        String valBase;
        int valRank;
        int valQualNum;
        Matcher valMatcher = SEMVER_QUALIFIER_PATTERN.matcher(value);
        if (valMatcher.matches()) {
            valBase = valMatcher.group(1);
            valRank = "m".equals(valMatcher.group(2)) ? 0 : 1;
            valQualNum = Integer.parseInt(valMatcher.group(3));
        } else {
            valBase = stripLegacyQualifiers(value);
            valRank = 2;
            valQualNum = 0;
        }

        if (!Pattern.matches(regexp, propBase) || !Pattern.matches(regexp, valBase)) {
            throw new Exception("Error while parsing params. The versions have some characters that are not numbers, '.' or '-' or an invalid format");
        }

        int baseCmp;
        try {
            baseCmp = compareNumericVersionParts(propBase, valBase);
        } catch (Exception e) {
            if (operador == '=') {
                return false;
            }
            throw e;
        }
        if (baseCmp != 0) {
            if (operador == '=') {
                return false;
            }
            if (operador == '>') {
                return baseCmp > 0;
            }
            return baseCmp < 0;
        }

        if (propRank != valRank) {
            if (operador == '=') {
                return false;
            }
            if (operador == '>') {
                return propRank > valRank;
            }
            return propRank < valRank;
        }

        if (propQualNum != valQualNum) {
            if (operador == '=') {
                return false;
            }
            if (operador == '>') {
                return propQualNum > valQualNum;
            }
            return propQualNum < valQualNum;
        }

        return operador == '=';
    }

    /**
     * Numerically compares two base version strings (e.g. "1.0.0" or "1.0.0-1.2.3").
     * Returns negative if v1 < v2, zero if equal, positive if v1 > v2.
     */
    private int compareNumericVersionParts(String v1, String v2) throws Exception {
        String[] segments1 = v1.split("-");
        String[] segments2 = v2.split("-");
        int minSegs = Math.min(segments1.length, segments2.length);
        for (int i = 0; i < minSegs; i++) {
            String[] nums1 = segments1[i].split("\\.");
            String[] nums2 = segments2[i].split("\\.");
            if (nums1.length != nums2.length) {
                throw new Exception("Error while parsing params. The versions must have the same number of elements");
            }
            for (int j = 0; j < nums1.length; j++) {
                int n1 = Integer.parseInt(nums1[j]);
                int n2 = Integer.parseInt(nums2[j]);
                if (n1 != n2) {
                    return Integer.compare(n1, n2);
                }
            }
        }
        return Integer.compare(segments1.length, segments2.length);
    }

    private boolean isNumeric(String value) {
        if (value == null) {
            return false;
        } else {
            try {
                Integer.parseInt(value);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}


