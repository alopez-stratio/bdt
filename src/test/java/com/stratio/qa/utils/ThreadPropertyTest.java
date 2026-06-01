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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadPropertyTest {

    @AfterMethod
    public void cleanup() {
        ThreadProperty.cleanup();
    }

    @Test
    public void testSetAndGet() {
        ThreadProperty.set("key1", "value1");
        assertThat(ThreadProperty.get("key1")).isEqualTo("value1");
    }

    @Test
    public void testGetNonExistentKeyReturnsNull() {
        assertThat(ThreadProperty.get("nonexistent")).isNull();
    }

    @Test
    public void testHas() {
        assertThat(ThreadProperty.has("mykey")).isFalse();
        ThreadProperty.set("mykey", "myval");
        assertThat(ThreadProperty.has("mykey")).isTrue();
    }

    @Test
    public void testRemove() {
        ThreadProperty.set("toRemove", "val");
        assertThat(ThreadProperty.has("toRemove")).isTrue();
        ThreadProperty.remove("toRemove");
        assertThat(ThreadProperty.has("toRemove")).isFalse();
    }

    @Test
    public void testOverwrite() {
        ThreadProperty.set("k", "first");
        ThreadProperty.set("k", "second");
        assertThat(ThreadProperty.get("k")).isEqualTo("second");
    }

    @Test
    public void testMultipleKeys() {
        ThreadProperty.set("a", "1");
        ThreadProperty.set("b", "2");
        assertThat(ThreadProperty.get("a")).isEqualTo("1");
        assertThat(ThreadProperty.get("b")).isEqualTo("2");
    }
}
