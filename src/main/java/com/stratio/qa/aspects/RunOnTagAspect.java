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
import gherkin.events.PickleEvent;
import gherkin.pickles.PickleLocation;
import gherkin.pickles.PickleTag;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Aspect
public class RunOnTagAspect {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    @Pointcut("execution (* cucumber.runner.Runner.runPickle(..)) && "
            + "args (pickleEvent)")
    protected void AddRunOnTagPointcutScenario(PickleEvent pickleEvent) {
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
     * @param pickleEvent pickleEvent
     * @throws Throwable exception
     */
    @Before(value = "AddRunOnTagPointcutScenario(pickleEvent)")
    public void aroundAddRunOnTagPointcut(JoinPoint jp, PickleEvent pickleEvent) throws Throwable {
        int line = 0;
        if (!pickleEvent.pickle.getLocations().isEmpty()) {
            line = pickleEvent.pickle.getLocations().get(0).getLine();
        }
        try {
            Boolean exit = tagsIteration(pickleEvent.pickle.getTags(), line);
            if (exit) {
                ThreadProperty.set("skippedOnParams" + pickleEvent.pickle.getName() + line, "true");
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            pickleEvent.pickle.getTags().add(new PickleTag(new PickleLocation(line, 0), "@ignore"));
            pickleEvent.pickle.getTags().add(new PickleTag(new PickleLocation(line, 0), "@envCondition"));
        }
    }

    public boolean tagsIteration(List<PickleTag> tags, Integer line) throws Exception {
        PickleLocation pickleLocation = new PickleLocation(line, 0);
        for (PickleTag tag : tags) {
            if (tag.getName().contains("@runOnEnv")) {
                if (!checkParams(getParams(tag.getName()))) {
                    tags.add(new PickleTag(pickleLocation, "@ignore"));
                    tags.add(new PickleTag(pickleLocation, "@envCondition"));
                    return true;
                }
            } else if (tag.getName().contains("@skipOnEnv")) {
                if (checkParams(getParams(tag.getName()))) {
                    tags.add(new PickleTag(pickleLocation, "@ignore"));
                    tags.add(new PickleTag(pickleLocation, "@envCondition"));
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
            ops = s.substring((s.lastIndexOf("(") + 1), (s.length()) - 1).split("[a-zA-Z._\\-0-9=<>]+");
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

   /*
    * Checks if every param in the array of strings is defined
    */
    public boolean checkParams(String[][] params) throws Exception {
        if ("".equals(params[0][0])) {
            throw new Exception("Error while parsing params. Params must be at least one");
        }

        boolean result = true;
        // Primer elemento
        if (params[0][0].contains("=")) {
            result = firstElementOperator(params[0][0], "=");
        } else if (params[0][0].contains(">")) {
            result = firstElementOperator(params[0][0], ">");
        } else if (params[0][0].contains("<")) {
            result = firstElementOperator(params[0][0], "<");
        } else {
            if (System.getProperty(params[0][0], "").isEmpty() && ThreadProperty.get(params[0][0]) == null) {
                result = false;
            }
        }

        // Elementos intermedios
        for (int j = 1; j < params[0].length - 1; j++) {
            if (params[0][j].contains("=")) {
                result = elementOperator(params[0][j], "=", params[1], j - 1, result);
            } else if (params[0][j].contains(">")) {
                result = elementOperator(params[0][j], ">", params[1], j - 1, result);
            } else if (params[0][j].contains("<")) {
                result = elementOperator(params[0][j], "<", params[1], j - 1, result);
            } else {
                if (System.getProperty(params[0][j], "").isEmpty() && ThreadProperty.get(params[0][j]) == null) {
                    result = updateResultOperation(params[1], j - 1, result, false);
                } else {
                    result = updateResultOperation(params[1], j - 1, result, true);
                }

            }
        }

        // Último elemento
        if (params[0].length > 1) {
            if (params[0][params[0].length - 1].contains("=")) {
                result = elementOperator(params[0][params[0].length - 1], "=", params[1], params[1].length - 1, result);
            } else if (params[0][params[0].length - 1].contains(">")) {
                result = elementOperator(params[0][params[0].length - 1], ">", params[1], params[1].length - 1, result);
            } else if (params[0][params[0].length - 1].contains("<")) {
                result = elementOperator(params[0][params[0].length - 1], "<", params[1], params[1].length - 1, result);
            } else {
                if (System.getProperty(params[0][params[0].length - 1], "").isEmpty() && ThreadProperty.get(params[0][params[0].length - 1]) == null) {
                    result = updateResultOperation(params[1], params[1].length - 1, result, false);
                } else {
                    result = updateResultOperation(params[1], params[1].length - 1, result, true);
                }
            }
        }
        return result;
    }

    private boolean firstElementOperator(String element, String operador) throws Exception {
        boolean result = true;
        String param = element.split(operador)[0];
        String value = element.split(operador)[1];
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
        boolean res = result;
        String param = element.split(operador)[0];
        String value = element.split(operador)[1];
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
        String envVarValue = property.replaceAll("-(SNAPSHOT|[a-zA-Z0-9]{7}|M[1-9]|RC[1-9])[0-9]", "error").replaceAll("-(SNAPSHOT|[a-zA-Z0-9]{7}|M[1-9]|RC[1-9])", "");
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


