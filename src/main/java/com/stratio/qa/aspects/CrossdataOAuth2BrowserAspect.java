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

import com.stratio.qa.utils.CrossdataOAuth2Browser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Hands the authorization URL the Crossdata JDBC driver opens for its OAuth2 U2M login to the BDT's fake browser
 * ({@link CrossdataOAuth2Browser}), while a U2M connection of the BDT is being opened. Otherwise the driver opens the
 * real browser. It matches only the driver's launcher, a Scala object: BrowserLauncher$ and its static forwarder.
 */
@Aspect
public class CrossdataOAuth2BrowserAspect {

    @Pointcut("execution(void com.stratio.crossdata.driver.oauth.BrowserLauncher*.open(java.lang.String)) && args(url)")
    protected void driverOpensTheBrowserPointcut(String url) {
    }

    /**
     * @param pjp ProceedingJoinPoint
     * @param url authorization URL the driver opens
     * @throws Throwable exception of the driver's launcher, when it runs
     */
    @Around(value = "driverOpensTheBrowserPointcut(url)", argNames = "pjp,url")
    public void aroundDriverOpensTheBrowser(ProceedingJoinPoint pjp, String url) throws Throwable {
        if (!CrossdataOAuth2Browser.openInActiveBrowser(url)) {
            pjp.proceed();
        }
    }
}
