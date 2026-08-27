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

package com.stratio.qa.specs;

import com.stratio.qa.utils.LdapSchema;
import com.stratio.qa.utils.ThreadProperty;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import org.assertj.core.api.Assertions;
import org.ldaptive.AttributeModification;
import org.ldaptive.AttributeModificationType;
import org.ldaptive.LdapAttribute;
import org.ldaptive.LdapEntry;
import org.ldaptive.SearchRequest;
import org.ldaptive.LdapException;
import java.security.NoSuchAlgorithmException;

import static org.testng.AssertJUnit.fail;

/**
 * Generic LDAP Specs.
 *
 * <p>How groups and membership are written depends on the schema the target directory serves.
 * Set the {@code LDAP_SCHEMA} system property to {@code rfc2307}, {@code rfc2307bis} (the default)
 * or {@code groupOfNames} -- see {@link LdapSchema}. Getting this wrong makes the server answer
 * {@code objectClassViolation (65)} on the first write.
 *
 * @see <a href="LdapSpec-annotations.html">LDAP Steps &amp; Matching Regex</a>
 */
public class LdapSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public LdapSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**
     * Connect to LDAP.
     */
    @Given("^I connect to LDAP( using the CA trust '(.+?)')?$")
    public void connectLDAP(String ldapCaTrust) {
        if (ldapCaTrust == null || ldapCaTrust.isEmpty()) {
            ldapCaTrust = "target/test-classes/ca_test.crt";
        }
        commonspec.getLdapUtils().connect(ldapCaTrust);
    }

    /**
     * Search for a LDAP object
     */
    @When("^I search in LDAP using the filter '(.+?)' and the baseDn '(.+?)'$")
    public void searchLDAP(String filter, String baseDn) throws Exception {
        this.commonspec.setPreviousLdapResults(commonspec.getLdapUtils().search(new SearchRequest(baseDn, filter)));
    }

    /**
     * Checks if the previous LDAP search contained a single Entry with a specific attribute and an expected value
     *
     * @param attributeName The name of the attribute to look for in the LdapEntry
     * @param expectedValue The expected value of the attribute
     */
    @Then("^the LDAP entry contains the attribute '(.+?)' with the value '(.+?)'$")
    public void ldapEntryContains(String attributeName, String expectedValue) {
        if (this.commonspec.getPreviousLdapResults().isPresent()) {
            Assertions.assertThat(this.commonspec.getPreviousLdapResults().get().getEntry().getAttribute(attributeName).getStringValues()).contains(expectedValue);
        } else {
            fail("No previous LDAP results were stored in memory");
        }
    }

    /**
     *
     * @param userUid The new user
     * @param userPassword The password for the new user
     * @param userGroup [optional] The group for the new user to be assigned to
     * @throws org.ldaptive.LdapException
     * @throws java.security.NoSuchAlgorithmException
     */
    @When("^I create LDAP user '(.+?)' with password '(.+?)'( and assign it to LDAP group '(.+?)')?$")
    public void createLDAPUser(String userUid, String userPassword, String userGroup) throws LdapException, NoSuchAlgorithmException {
        String userDn = "uid=" + userUid + "," + ThreadProperty.get("LDAP_USER_DN");
        int userUidNumber = this.commonspec.getLdapUtils().getLDAPMaxUidNumber() + 1;
        String groupName;
        if (userGroup == null) {
            groupName = "stratio";
        } else if (userGroup.equalsIgnoreCase("admin")) {
            groupName = ThreadProperty.get("LDAP_ADMIN_GROUP");
        } else {
            groupName = userGroup;
        }
        int groupGidNumber = this.commonspec.getLdapUtils().getLDAPgidNumber(groupName);
        this.assignLDAPuserToGroup(userUid, groupName);

        LdapEntry newUser = new LdapEntry(userDn);
        newUser.addAttribute(new LdapAttribute("objectClass", "inetOrgPerson", "posixAccount", "shadowAccount"));
        newUser.addAttribute(new LdapAttribute("cn", userUid));
        newUser.addAttribute(new LdapAttribute("sn", userUid));
        newUser.addAttribute(new LdapAttribute("gidNumber", String.valueOf(groupGidNumber)));
        newUser.addAttribute(new LdapAttribute("homeDirectory", "/home/" + userUid));
        newUser.addAttribute(new LdapAttribute("uidNumber", String.valueOf(userUidNumber)));
        newUser.addAttribute(new LdapAttribute("uid", userUid));
        this.commonspec.getLdapUtils().add(newUser);

        AttributeModification newAttr = new AttributeModification(AttributeModificationType.ADD, new LdapAttribute("userPassword", this.commonspec.getLdapUtils().hashPassword(userPassword)));
        this.commonspec.getLdapUtils().modify(userDn, newAttr);
    }

    /**
     * Delete a user, first removing it from every group it belongs to.
     *
     * <p>Deleting a user that does not exist is not an error -- see
     * {@link com.stratio.qa.utils.LdapUtils#delete(String)}. Follow this with
     * {@code I check that LDAP user '...' does not exist} when you need the deletion proven.
     *
     * @param userUid The user to be deleted
     * @throws LdapException if the server refuses the deletion
     */
    @When("^I delete LDAP user '(.+?)'$")
    public void deleteLDAPuser(String userUid) throws LdapException {
        this.unassignLDAPuserFromAllGroups(userUid);

        String userDn = "uid=" + userUid + "," + ThreadProperty.get("LDAP_USER_DN");
        this.commonspec.getLdapUtils().delete(userDn);
    }

    /**
     *
     * @param userUid The user
     * @param groupCn The group where the user is going to be added
     * @throws LdapException
     */
    @When("^I assign LDAP user '(.+?)' to LDAP group '(.+?)'$")
    public void assignLDAPuserToGroup(String userUid, String groupCn) throws LdapException {
        String groupDn = "cn=" + groupCn + "," + ThreadProperty.get("LDAP_GROUP_DN");
        String userDn = "uid=" + userUid + "," + ThreadProperty.get("LDAP_USER_DN");

        for (String memberAttr : LdapSchema.current().getMembershipAttributes()) {
            AttributeModification newAttr = new AttributeModification(AttributeModificationType.ADD, new LdapAttribute(memberAttr, userDn));
            this.commonspec.getLdapUtils().modify(groupDn, newAttr);
        }
    }

    /**
     *
     * @param userUid The user to be unassigned from all groups
     * @throws LdapException
     */
    @When("^I unassign LDAP user '(.+?)' from all LDAP groups$")
    public void unassignLDAPuserFromAllGroups(String userUid) throws LdapException {
        for (String memberAttr : LdapSchema.current().getMembershipAttributes()) {
            this.commonspec.getLdapUtils().deleteLDAPuserFromAllGroupsAttribute(userUid, memberAttr);
        }
    }

    /**
     *
     * @param userUid The user to be unassigned
     * @param groupCn The group from the user is going to be unassigned
     * @throws LdapException
     */
    @When("^I unassign LDAP user '(.+?)' from LDAP group '(.+?)'$")
    public void unassignLDAPuserFromGroup(String userUid, String groupCn) throws LdapException {
        for (String memberAttr : LdapSchema.current().getMembershipAttributes()) {
            this.commonspec.getLdapUtils().deleteLDAPuserFromGroupAttribute(userUid, groupCn, memberAttr);
        }
    }

    /**
     *
     * @param groupCn The new group
     * @throws LdapException
     * @throws NoSuchAlgorithmException
     */
    @When("^I create LDAP group '(.+?)'$")
    public void createLDAPGroup(String groupCn) throws LdapException {
        String groupDn = "cn=" + groupCn + "," + ThreadProperty.get("LDAP_GROUP_DN");
        int groupGidNumber = this.commonspec.getLdapUtils().getLDAPMaxGidNumber() + 1;

        LdapSchema schema = LdapSchema.current();

        LdapEntry newGroup = new LdapEntry(groupDn);
        newGroup.addAttribute(new LdapAttribute("objectClass", schema.getGroupObjectClasses().toArray(new String[0])));
        newGroup.addAttribute(new LdapAttribute("cn", groupCn));
        newGroup.addAttribute(new LdapAttribute("gidNumber", String.valueOf(groupGidNumber)));
        newGroup.addAttribute(new LdapAttribute("description", groupCn + " group"));
        // groupOfNames declares member as MUST, so a brand new group needs a placeholder to exist
        // at all. Written to every membership attribute of the schema to keep them consistent.
        for (String memberAttr : schema.getMembershipAttributes()) {
            newGroup.addAttribute(new LdapAttribute(memberAttr, "uid=fake," + ThreadProperty.get("LDAP_USER_DN")));
        }
        this.commonspec.getLdapUtils().add(newGroup);
    }

    /**
     * Delete a group.
     *
     * <p>Deleting a group that does not exist is not an error -- see
     * {@link com.stratio.qa.utils.LdapUtils#delete(String)}.
     *
     * @param groupCn The group to be deleted
     * @throws LdapException if the server refuses the deletion
     */
    @When("^I delete LDAP group '(.+?)'$")
    public void deleteLDAPgroup(String groupCn) throws LdapException {
        String groupDn = "cn=" + groupCn + "," + ThreadProperty.get("LDAP_GROUP_DN");
        this.commonspec.getLdapUtils().delete(groupDn);
    }

    /**
     *
     * @param userUid The user to change its password
     * @param newPassword The new password
     * @throws LdapException
     * @throws NoSuchAlgorithmException
     */
    @When("^I change the password of LDAP user '(.+?)' to '(.+?)'$")
    public void changeLDAPuserPassword(String userUid, String newPassword) throws LdapException, NoSuchAlgorithmException {
        String userDn = "uid=" + userUid + "," + ThreadProperty.get("LDAP_USER_DN");

        AttributeModification newAttr = new AttributeModification(AttributeModificationType.REPLACE, new LdapAttribute("userPassword", this.commonspec.getLdapUtils().hashPassword(newPassword)));
        this.commonspec.getLdapUtils().modify(userDn, newAttr);
    }

    /**
     *
     * @param userUid The user to check
     * @param groupCn The group to check
     * @throws LdapException
     */
    @When("^I check that LDAP user '(.+?)' belongs to LDAP group '(.+?)'$")
    public void checkLDAPuserBelongsToGroup(String userUid, String groupCn) throws LdapException {
        Assertions.assertThat(this.commonspec.getLdapUtils().isLDAPuserInGroup(userUid, groupCn)).isTrue();
    }

    /**
     *
     * @param userUid The user to check
     * @param groupCn The group to check
     * @throws LdapException
     */
    @When("^I check that LDAP user '(.+?)' does not belong to LDAP group '(.+?)'$")
    public void checkLDAPuserDoesNotBelongToGroup(String userUid, String groupCn) throws LdapException {
        Assertions.assertThat(this.commonspec.getLdapUtils().isLDAPuserInGroup(userUid, groupCn)).isFalse();
    }

    /**
     *
     * @param userUid The user to check
     * @throws LdapException
     */
    @When("^I check that LDAP user '(.+?)' exists$")
    public void checkLDAPuserExists(String userUid) throws LdapException {
        Assertions.assertThat(this.commonspec.getLdapUtils().userLDAPexists(userUid)).isTrue();
    }

    /**
     *
     * @param userUid The user to check
     * @throws LdapException
     */
    @When("^I check that LDAP user '(.+?)' does not exist$")
    public void checkLDAPuserDoesNotExist(String userUid) throws LdapException {
        Assertions.assertThat(this.commonspec.getLdapUtils().userLDAPexists(userUid)).isFalse();
    }

    /**
     *
     * @param groupCn The group to check
     * @throws LdapException
     */
    @When("^I check that LDAP group '(.+?)' exists$")
    public void checkLDAPgroupExists(String groupCn) throws LdapException {
        Assertions.assertThat(this.commonspec.getLdapUtils().groupLDAPexists(groupCn)).isTrue();
    }

    /**
     *
     * @param groupCn The group to check
     * @throws LdapException
     */
    @When("^I check that LDAP group '(.+?)' does not exist$")
    public void checkLDAPgroupDoesNotExist(String groupCn) throws LdapException {
        Assertions.assertThat(this.commonspec.getLdapUtils().groupLDAPexists(groupCn)).isFalse();
    }

    /**
     *
     * @param userUid The user to get its groups
     * @param envVar Environment variable to store the groups list
     * @throws LdapException
     */
    @When("^I get all LDAP groups where LDAP user '(.+?)' belongs and save it in environment variable '(.+?)'$")
    public void getLDAPgroupsOfUser(String userUid, String envVar) throws LdapException {
        ArrayList<String> groupsList = this.commonspec.getLdapUtils().getLDAPgroupsContainingUserAsAttribute(userUid, LdapSchema.current().getMembershipSearchAttribute());
        ThreadProperty.set(envVar, groupsList.toString());
    }
}
