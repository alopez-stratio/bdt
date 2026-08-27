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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LdapSchemaTest {

    @BeforeMethod
    @AfterMethod
    public void clearConfiguration() {
        System.clearProperty(LdapSchema.PROPERTY);
        ThreadProperty.remove(LdapSchema.PROPERTY);
    }

    @Test
    public void rfc2307KeepsGroupsPosixOnly() {
        assertThat(LdapSchema.RFC2307.getGroupObjectClasses()).containsExactly("posixGroup");
        assertThat(LdapSchema.RFC2307.getMembershipAttributes()).containsExactly("memberUid");
        assertThat(LdapSchema.RFC2307.getMembershipSearchAttribute()).isEqualTo("memberUid");
    }

    @Test
    public void groupOfNamesKeepsGroupsOutOfPosix() {
        assertThat(LdapSchema.GROUPOFNAMES.getGroupObjectClasses()).containsExactly("groupOfNames");
        assertThat(LdapSchema.GROUPOFNAMES.getMembershipAttributes()).containsExactly("member");
        assertThat(LdapSchema.GROUPOFNAMES.getMembershipSearchAttribute()).isEqualTo("member");
    }

    @Test
    public void rfc2307bisIsTheOnlyOneThatCombinesBoth() {
        assertThat(LdapSchema.RFC2307BIS.getGroupObjectClasses())
                .as("only legal where posixGroup has been redefined as AUXILIARY")
                .containsExactly("groupOfNames", "posixGroup");
        assertThat(LdapSchema.RFC2307BIS.getMembershipAttributes()).containsExactly("member", "memberUid");
        assertThat(LdapSchema.RFC2307BIS.getMembershipSearchAttribute()).isEqualTo("member");
    }

    @Test
    public void noSchemaConfiguredKeepsTheBehaviourBdtHasAlwaysHad() {
        assertThat(LdapSchema.current()).isEqualTo(LdapSchema.RFC2307BIS);
        assertThat(LdapSchema.from(null)).isEqualTo(LdapSchema.RFC2307BIS);
        assertThat(LdapSchema.from("")).isEqualTo(LdapSchema.RFC2307BIS);
    }

    @Test
    public void theSystemPropertySelectsTheSchema() {
        System.setProperty(LdapSchema.PROPERTY, "rfc2307");

        assertThat(LdapSchema.current()).isEqualTo(LdapSchema.RFC2307);
    }

    @Test
    public void aThreadPropertySelectsItToo() {
        ThreadProperty.set(LdapSchema.PROPERTY, "groupOfNames");

        assertThat(LdapSchema.current()).isEqualTo(LdapSchema.GROUPOFNAMES);
    }

    @Test
    public void theSystemPropertyWinsOverTheThreadProperty() {
        System.setProperty(LdapSchema.PROPERTY, "rfc2307");
        ThreadProperty.set(LdapSchema.PROPERTY, "groupOfNames");

        assertThat(LdapSchema.current()).isEqualTo(LdapSchema.RFC2307);
    }

    @Test
    public void anEmptySystemPropertyFallsBackToTheThreadProperty() {
        System.setProperty(LdapSchema.PROPERTY, "");
        ThreadProperty.set(LdapSchema.PROPERTY, "rfc2307");

        assertThat(LdapSchema.current()).isEqualTo(LdapSchema.RFC2307);
    }

    @Test
    public void theNameIsReadLoosely() {
        assertThat(LdapSchema.from("RFC2307BIS")).isEqualTo(LdapSchema.RFC2307BIS);
        assertThat(LdapSchema.from("rfc-2307-bis")).isEqualTo(LdapSchema.RFC2307BIS);
        assertThat(LdapSchema.from("rfc_2307")).isEqualTo(LdapSchema.RFC2307);
        assertThat(LdapSchema.from("groupofnames")).isEqualTo(LdapSchema.GROUPOFNAMES);
    }

    @Test
    public void aSchemaNameThatDoesNotExistIsRejectedRatherThanIgnored() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .as("silently defaulting would hide the typo until the server rejected a write")
                .isThrownBy(() -> LdapSchema.from("rfc2307ter"))
                .withMessageContaining("Unknown LDAP_SCHEMA 'rfc2307ter'")
                .withMessageContaining("RFC2307BIS");
    }
}
