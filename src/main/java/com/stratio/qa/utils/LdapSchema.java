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

import java.util.Arrays;
import java.util.List;

/**
 * How the LDAP server being tested models groups and their membership.
 *
 * <p>The two families are not interchangeable: {@code groupOfNames} and {@code posixGroup} are both
 * STRUCTURAL object classes in the standard schema, so an entry cannot hold both -- the server
 * answers {@code objectClassViolation (65) - invalid structural object class chain}. RFC 2307bis
 * redefines {@code posixGroup} as AUXILIARY, which is what makes the combination legal there.
 *
 * <p>Selected with the {@code LDAP_SCHEMA} system property (or the {@code LDAP_SCHEMA}
 * {@link ThreadProperty}). Defaults to {@link #RFC2307BIS}, which is the behaviour BDT has always
 * had.
 */
public enum LdapSchema {

    /**
     * Groups are {@code posixGroup}, membership lives in {@code memberUid}. This is what a stock
     * OpenLDAP with the {@code nis} schema gives you -- and what the {@code stratio/ldap-docker}
     * image used by the integration tests serves.
     */
    RFC2307("posixGroup", "memberUid"),

    /**
     * Groups are both {@code groupOfNames} and {@code posixGroup}, membership is written to
     * {@code member} and {@code memberUid}. Only valid where {@code posixGroup} has been redefined
     * as AUXILIARY.
     */
    RFC2307BIS(Arrays.asList("groupOfNames", "posixGroup"), Arrays.asList("member", "memberUid")),

    /**
     * Groups are {@code groupOfNames}, membership lives in {@code member}. The shape Active
     * Directory and most non-POSIX directories use.
     */
    GROUPOFNAMES("groupOfNames", "member");

    public static final String PROPERTY = "LDAP_SCHEMA";

    private final List<String> groupObjectClasses;

    private final List<String> membershipAttributes;

    LdapSchema(String groupObjectClass, String membershipAttribute) {
        this(Arrays.asList(groupObjectClass), Arrays.asList(membershipAttribute));
    }

    LdapSchema(List<String> groupObjectClasses, List<String> membershipAttributes) {
        this.groupObjectClasses = groupObjectClasses;
        this.membershipAttributes = membershipAttributes;
    }

    /**
     * The schema configured for this run, defaulting to {@link #RFC2307BIS}.
     *
     * @throws IllegalArgumentException if the property holds a name that is not a known schema --
     *                                  silently falling back to the default would hide the typo
     *                                  until the server rejected a write.
     */
    public static LdapSchema current() {
        String configured = System.getProperty(PROPERTY);
        if (configured == null || configured.isEmpty()) {
            configured = ThreadProperty.get(PROPERTY);
        }
        return from(configured);
    }

    /**
     * @param name the schema name, case and hyphen insensitive; null or empty gives the default
     */
    public static LdapSchema from(String name) {
        if (name == null || name.isEmpty()) {
            return RFC2307BIS;
        }
        String normalized = name.replace("-", "").replace("_", "").toUpperCase();
        for (LdapSchema schema : values()) {
            if (schema.name().equals(normalized)) {
                return schema;
            }
        }
        throw new IllegalArgumentException("Unknown " + PROPERTY + " '" + name + "'. Valid values: "
                + Arrays.toString(values()));
    }

    /**
     * The object classes a new group entry must declare.
     */
    public List<String> getGroupObjectClasses() {
        return groupObjectClasses;
    }

    /**
     * Every attribute that has to be kept in sync when a user joins or leaves a group.
     */
    public List<String> getMembershipAttributes() {
        return membershipAttributes;
    }

    /**
     * The attribute to filter on when asking the server whether a user belongs to a group. Writes
     * go to all of {@link #getMembershipAttributes()}, so any of them would answer -- searching a
     * single one keeps the filter simple.
     */
    public String getMembershipSearchAttribute() {
        return membershipAttributes.get(0);
    }
}
