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

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashUtilsTest {

    @Test
    public void testDoHashProducesDeterministicResult() {
        String hash1 = HashUtils.doHash("hello");
        String hash2 = HashUtils.doHash("hello");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    public void testDoHashDifferentInputsDifferentResults() {
        String hash1 = HashUtils.doHash("hello");
        String hash2 = HashUtils.doHash("world");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    public void testDoHashEmptyString() {
        String hash = HashUtils.doHash("");
        assertThat(hash).isNotNull().isNotEmpty();
    }

    @Test
    public void testDoHashReturnsString() {
        String hash = HashUtils.doHash("test");
        assertThat(hash).isNotNull();
        // verify it is parseable as a number
        assertThat(Integer.parseInt(hash)).isInstanceOf(Integer.class);
    }
}
