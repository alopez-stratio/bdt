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

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Generic Zookeeper Specs.
 *
 * @see <a href="ZookeeperSpec-annotations.html">Zookeeper Steps &amp; Matching Regex</a>
 */
public class ZookeeperSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public ZookeeperSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**
     * Connect to zookeeper.
     *
     * @param zookeeperHosts as host:port (comma separated)
     * @throws InterruptedException exception
     */
    @Given("I connect to Zookeeper at {string}")
    public void connectToZk(String zookeeperHosts) throws Exception {
        commonspec.getZookeeperSecUtils().connectZk(zookeeperHosts);
    }


    /**
     * Connect to secured zookeeper.
     *
     * @param zookeeperHosts as host:port (comma separated)
     * @throws InterruptedException exception
     */
    @Given("^I connect to Zookeeper at '(.+)' with keytab '(.+)', principal '(.+)' and krb5 '(.+)'$")
    public void connectToSecuredZk(String zookeeperHosts, String keytabPath, String principal, String krb5Path) throws Exception {
        commonspec.getZookeeperSecUtils().connectZk(zookeeperHosts, keytabPath, principal, krb5Path);
    }

    /**
     * Disconnect from zookeeper.
     */
    @Given("^I disconnect from Zookeeper$")
    public void disconnectFromZk() throws InterruptedException {
        commonspec.getZookeeperSecUtils().disconnect();
    }

    /**
     * Delete zPath, it should be empty
     *
     * @param zNode path at zookeeper
     */
    @When("^I remove the zNode '(.+?)'$")
    public void removeZNode(String zNode) throws Exception {
        commonspec.getZookeeperSecUtils().delete(zNode);
    }


    /**
     * Create zPath and domcument
     *
     * @param path      path at zookeeper
     * @param content   if it has content it should be defined
     * @param sEphemeral if it's created as ephemeral or not
     */
    @When("^I create the zNode '(.+?)'( with content '(.+?)')? which (IS|IS NOT) ephemeral$")
    public void createZNode(String path, String content, String sEphemeral) throws Exception {
        boolean ephemeral = sEphemeral.equals("IS");
        commonspec.getZookeeperSecUtils().zCreate(path, content, ephemeral);
    }

    /**
     * Read zPath
     *
     * @param zNode    path at zookeeper
     * @param document expected content of znode
     */
    @Then("^the zNode '(.+?)' exists( and contains '(.+?)')?$")
    public void checkZnodeExists(String zNode, String document) throws Exception {
        if (document == null) {
            String breakpoint = commonspec.getZookeeperSecUtils().zRead(zNode);
            assert breakpoint.equals("") : "The zNode does not exist";
        } else {
            assert commonspec.getZookeeperSecUtils().zRead(zNode).contains(document) : "The zNode does not exist or the content does not match";
        }
    }

    @Then("^the zNode '(.+?)' does not exist")
    public void checkZnodeNotExist(String zNode) throws Exception {
        assert !commonspec.getZookeeperSecUtils().exists(zNode) : "The zNode exists";
    }

}
