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
package com.stratio.qa.utils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StepExceptionTest {

    @BeforeMethod
    public void resetException() {
        StepException.INSTANCE.setException(null);
    }

    @Test
    public void testInitiallyNull() {
        assertThat(StepException.INSTANCE.getException()).isNull();
    }

    @Test
    public void testSetAndGetException() {
        Exception e = new RuntimeException("test error");
        StepException.INSTANCE.setException(e);
        assertThat(StepException.INSTANCE.getException()).isSameAs(e);
        assertThat(StepException.INSTANCE.getException().getMessage()).isEqualTo("test error");
    }

    @Test
    public void testSingleton() {
        assertThat(StepException.INSTANCE).isSameAs(StepException.INSTANCE);
        assertThat(StepException.values()).hasSize(1);
    }

    @Test
    public void testResetToNull() {
        StepException.INSTANCE.setException(new RuntimeException("first"));
        StepException.INSTANCE.setException(null);
        assertThat(StepException.INSTANCE.getException()).isNull();
    }
}
