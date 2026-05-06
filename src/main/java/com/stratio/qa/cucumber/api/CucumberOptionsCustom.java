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

package com.stratio.qa.cucumber.api;

import io.cucumber.core.snippets.SnippetType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CucumberOptionsCustom {
    boolean dryRun() default false;

    boolean strict() default false;

    FeatureEnvironment[] environments() default {};

    String[] glue() default {};

    String[] extraGlue() default {};

    String[] tags() default {};

    String[] plugin() default {};

    boolean monochrome() default false;

    String[] name() default {};

    SnippetType snippets() default SnippetType.UNDERSCORE;

    String[] junit() default {};
}
