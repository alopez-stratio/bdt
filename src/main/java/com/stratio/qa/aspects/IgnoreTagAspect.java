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

import com.stratio.qa.specs.CommonG;
import io.cucumber.core.gherkin.Pickle;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
public class IgnoreTagAspect {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    @Pointcut("execution (* io.cucumber.core.runner.Runner.runPickle(..)) && "
            + "args (pickle)")
    protected void addIgnoreTagPointcutScenario(Pickle pickle) {
    }

    /**
     * @param pjp    ProceedingJoinPoint
     * @param pickle pickle
     * @throws Throwable exception
     */
    @Around(value = "addIgnoreTagPointcutScenario(pickle)")
    public void aroundAddIgnoreTagPointcut(ProceedingJoinPoint pjp, Pickle pickle) throws Throwable {
        String scenarioName = pickle.getName();
        List<String> tagList = pickle.getTags();

        ignoreReasons exitReason = manageTags(tagList, scenarioName);
        if (exitReason.equals(ignoreReasons.NOREASON)) {
            logger.error("Scenario '" + scenarioName + "' failed due to wrong use of the @ignore tag.");
        }

        if ((!(exitReason.equals(ignoreReasons.NOTIGNORED))) && (!(exitReason.equals(ignoreReasons.NOREASON)))) {
            // Scenario is ignored — do not proceed
            return;
        } else {
            pjp.proceed();
        }
    }

    public ignoreReasons manageTags(List<String> tagList, String scenarioName) {
        ignoreReasons exit = ignoreReasons.NOTIGNORED;
        if (tagList.contains("@ignore")) {
            exit = ignoreReasons.NOREASON;
            for (String tag : tagList) {
                Pattern pattern = Pattern.compile("@tillfixed\\((.*?)\\)");
                Matcher matcher = pattern.matcher(tag);
                if (matcher.find()) {
                    String ticket = matcher.group(1);
                    if (!isJiraTicketFixed(ticket)) {
                        logger.warn("Scenario '" + scenarioName + "' ignored because of ticket: " + ticket + "\n");
                        exit = ignoreReasons.JIRATICKET;
                    } else {
                        return ignoreReasons.NOTIGNORED;
                    }
                }
            }
            if (tagList.contains("@envCondition")) {
                exit = ignoreReasons.ENVCONDITION;
            }
            if (tagList.contains("@unimplemented")) {
                logger.warn("Scenario '" + scenarioName + "' ignored because it is not yet implemented.\n");
                exit = ignoreReasons.UNIMPLEMENTED;
            }
            if (tagList.contains("@manual")) {
                logger.warn("Scenario '" + scenarioName + "' ignored because it is marked as manual test.\n");
                exit = ignoreReasons.MANUAL;
            }
            if (tagList.contains("@toocomplex")) {
                logger.warn("Scenario '" + scenarioName + "' ignored because the test is too complex.\n");
                exit = ignoreReasons.TOOCOMPLEX;
            }
        }
        return exit;
    }

    public enum ignoreReasons { NOTIGNORED, ENVCONDITION, UNIMPLEMENTED, MANUAL, TOOCOMPLEX, JIRATICKET, NOREASON }

    /**
     * Checks the passed by ticket parameter validity against a Attlasian Jira account
     *
     * @param ticket Jira ticket
     */
    private boolean isJiraTicketFixed(String ticket) {
        String userJira = System.getProperty("usernamejira") != null ? System.getProperty("usernamejira") : System.getenv("usernamejira");
        String passJira = System.getProperty("passwordjira") != null ? System.getProperty("passwordjira") : System.getenv("passwordjira");
        Boolean isJiraTicketFixed = false;

        if ((userJira != null) || (passJira != null) || "".equals(ticket)) {
            CommonG comm = new CommonG();
            String authString = userJira + ":" + passJira;
            byte[] authEncBytes = java.util.Base64.getEncoder().encode(authString.getBytes());
            String value = "";
            try {
                comm.runLocalCommand("curl -X GET https://stratio.atlassian.net/rest/api/2/issue/" + ticket + " -H 'Authorization: Basic " + new String(authEncBytes) + "' | jq -r '.fields.status.name'");
                value = comm.getCommandResult().toLowerCase();
            } catch (Exception e) {
                logger.error("Rest API Jira connection error", e);
                return false;
            }

            if ("done".equals(value) || "finalizado".equals(value) || "qa".equals(value)) {
                isJiraTicketFixed = true;
                logger.debug("Jira ticket has status {}, so scenario is not ignored", value);
            }
        }
        return isJiraTicketFixed;
    }
}