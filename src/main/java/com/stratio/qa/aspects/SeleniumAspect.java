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

import com.stratio.qa.assertions.SeleniumAssert;
import com.stratio.qa.specs.BaseGSpec;
import com.stratio.qa.specs.CommonG;
import com.stratio.qa.specs.SeleniumSpec;
import com.stratio.qa.utils.PreviousWebElements;
import io.cucumber.java.Scenario;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;

@Aspect
public class SeleniumAspect extends BaseGSpec {

    private final Logger logger = LoggerFactory.getLogger(this.getClass()
            .getCanonicalName());

    @Pointcut("call(* com.stratio.qa.assertions.SeleniumAssert.*(..))"
            + " || call(* org.openqa.selenium.*.click(..))"
            + " || call(* org.openqa.selenium.*.findElement(..))")
    protected void exceptionCallPointcut() {
    }

    /**
     * If an exception has thrown by selenium, this methods save a screen
     * capture.
     *
     * @param pjp ProceedingJoinPoint
     * @return Object object
     * @throws Throwable exception
     */
    @Around(value = "exceptionCallPointcut()")
    public Object aroundExceptionCalls(ProceedingJoinPoint pjp)
            throws Throwable {
        Object retVal = null;
        try {
            retVal = pjp.proceed();
            return retVal;
        } catch (Throwable ex) {
            WebDriver driver = null;
            if (ex instanceof WebDriverException) {
                logger.info("Got a selenium exception");
                if (!(pjp.getThis() instanceof BaseGSpec)) {
                    throw ex;
                }
                driver = ((BaseGSpec) pjp.getThis()).getCommonSpec().getDriver();
            } else if ((pjp.getTarget() instanceof SeleniumAssert)
                    && (ex instanceof AssertionError)) {
                logger.info("Got a SeleniumAssert response");
                SeleniumAssert as = (SeleniumAssert) pjp.getTarget();
                Class<?> c = as.getClass().getSuperclass();
                Field actual = c.getDeclaredField("actual");
                actual.setAccessible(true);
                Object realActual = actual.get(as);

                if (realActual instanceof WebDriver) {
                    driver = (WebDriver) actual.get(as);
                } else if (realActual instanceof ArrayList) {
                    if (((ArrayList) realActual).get(0) instanceof RemoteWebElement) {
                        driver = ((RemoteWebElement) ((ArrayList) realActual).get(0)).getWrappedDriver();
                    }
                } else if ((realActual instanceof PreviousWebElements) ||
                        (realActual instanceof Boolean) ||
                        (realActual instanceof String) ||
                        (realActual == null)) {
                    driver = (((SeleniumAssert) pjp.getTarget()).getCommonspec()).getDriver();
                } else if (realActual instanceof RemoteWebElement) {
                    driver = ((RemoteWebElement) actual.get(as)).getWrappedDriver();
                }
            }
            if (driver != null) {
                logger.info("Trying to capture screenshots...");
                CommonG common = null;
                Scenario scenario = null;
                if ((pjp.getThis() instanceof SeleniumSpec) && (((SeleniumSpec) pjp.getThis()).getCommonSpec() != null)) {
                    common = ((SeleniumSpec) pjp.getThis()).getCommonSpec();
                    scenario = ((SeleniumSpec) pjp.getThis()).getScenario();
                } else if ((pjp.getTarget() instanceof SeleniumAssert) && ((SeleniumAssert) pjp.getTarget()).getCommonspec() != null) {
                    common = ((SeleniumAssert) pjp.getTarget()).getCommonspec();
                    scenario = ((SeleniumAssert) pjp.getTarget()).getScenario();
                } else {
                    logger.info("Got no Selenium driver to capture a screen");
                    throw ex;
                }
                common.captureEvidence(driver, "framehtmlSource", "assert", null);
                common.captureEvidence(driver, "htmlSource", "assert", null);
                common.captureEvidence(driver, "screenCapture", "assert", scenario);
                logger.info("Screenshots are available at target/executions");
            } else {
                logger.info("Got no Selenium driver to capture a screen");
            }
            throw ex;
        }
    }
}