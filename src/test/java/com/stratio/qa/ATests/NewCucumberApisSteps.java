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
package com.stratio.qa.ATests;

import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.DataTableType;
import io.cucumber.java.en.When;

import java.util.List;

/**
 * Glue class to verify that Cucumber 7 APIs (@BeforeAll, @AfterAll, @DataTableType) work
 * correctly with BDT's variable-replacement aspects.
 *
 * @BeforeAll / @AfterAll: must be static in Cucumber 7.
 * @DataTableType: converts each DataTable cell (String) to DtCell before execute() is called.
 * The test verifies that !{VAR} replacement happens BEFORE the @DataTableType converter runs.
 */
public class NewCucumberApisSteps {

    public static class DtCell {
        public final String value;
        public DtCell(String value) {
            this.value = value;
        }
    }

    @DataTableType
    public DtCell dtCell(String raw) {
        return new DtCell(raw);
    }

    @BeforeAll
    public static void beforeAllScenarios() {
        ThreadProperty.set("BEFORE_ALL_RAN", "yes");
    }

    @AfterAll
    public static void afterAllScenarios() {
        ThreadProperty.set("AFTER_ALL_RAN", "yes");
        // Reset so the next feature's @BeforeAll must fire again for the check to pass
        ThreadProperty.set("BEFORE_ALL_RAN", "no");
    }

    @When("^I load first datatabletype cell into var '(.+?)':$")
    public void loadFirstDtCell(String varName, List<DtCell> cells) {
        ThreadProperty.set(varName, cells.get(0).value);
    }
}
