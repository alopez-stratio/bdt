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

// STRATIO-FORK: registers custom ParameterType "isornot" -> "(IS|IS NOT)" -> Boolean,
// used in Stratio step definitions that match "IS" / "IS NOT" phrases.

package com.stratio.qa.cucumber.converter;

import io.cucumber.java.ParameterType;

public class TypeRegistryConfiguration {

    @ParameterType("IS|IS NOT")
    public Boolean isornot(String input) {
        return "IS".equals(input);
    }
}
