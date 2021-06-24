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

import com.stratio.qa.utils.ThreadProperty;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.cucumber.datatable.DataTable;
import org.apache.zookeeper.KeeperException;
import org.assertj.core.api.Assertions;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * Generic HDFS Sec Specs.
 *
 * @see <a href="HDFSSecSpec-annotations.html">HDFS Sec Steps &amp; Matching Regex</a>
 */
public class HDFSSecSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public HDFSSecSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**
     * Open connection to HDFS cluster
     *
     * @param hdfsHost          brokers URL to connect to cluster
     * @param coreSite          core-site.xml file path
     * @param hdfsSite          hdfs-site.xml file path
     * @param krb5Conf          krb5.conf file path
     * @param sslClient         ssl-client.xml file path
     * @param keytabPath        keytab file path
     * @param truststorePath    truststore file path
     * @param realm             kerberos realm
     */
    @Given("^I open connection to HDFS '(.+?)' with core-site '(.+?)' and hdfs-site '(.+?)' and krb5 '(.+?)' and sslClient '(.+?)' and keytab '(.+?)' and truststore '(.+?)' and realm '(.+?)'$")
    public void openHDFSConnection(String hdfsHost, String coreSite, String hdfsSite, String krb5Conf, String sslClient, String keytabPath, String truststorePath, String realm) throws Exception {
        commonspec.getHDFSSecUtils().createSecuredHDFSConnection(coreSite, hdfsSite, krb5Conf, sslClient, hdfsHost, keytabPath, truststorePath, realm);
    }

    /**
     * Open connection to HDFS cluster
     *
     * @param hdfsHost          brokers URL to connect to cluster
     * @param keytabPath        keytab file path
     * @param realm             kerberos realm
     * @param principal         User to connect
     * @param kdc               kerberos kdc
     */
    @Given("^I open( private)? connection to HDFS '(.+?)' with principal '(.+?)' and keytab '(.+?)' and realm '(.+?)' and kdc '(.+?)'$")
    public void openHDFSConnection(String privateConnection, String hdfsHost, String principal, String keytabPath, String realm, String kdc) throws Exception {
        commonspec.getHDFSSecUtils().createExternalHDFSConnection(principal, hdfsHost, keytabPath, realm, kdc, privateConnection != null);
    }

    @Given("^I close HDFS connection$")
    public void closeHDFSConnection() {
        commonspec.getHDFSSecUtils().closeConnection();
    }

    @When("^I write local file '(.+?)' to HDFS file '(.+?)'$")
    public void writeFile(String local, String hdfsPath) throws IOException {
        commonspec.getHDFSSecUtils().writeToHDFS(local, hdfsPath);
    }

    @When("^I read file '(.+?)' from HDFS to local file '(.+?)'$")
    public void readFile(String hdfsPath, String local) throws IOException {
        commonspec.getHDFSSecUtils().readFromHDFS(hdfsPath, local);
    }

    @When("^I delete file '(.+?)' from HDFS$")
    public void deleteFile(String hdfsPath) throws IOException {
        commonspec.getHDFSSecUtils().deleteFile(hdfsPath);
    }

    @Then("^file '(.+?)' exists in HDFS$")
    public void fileExists(String hdfsPath) throws IOException {
        commonspec.getHDFSSecUtils().fileExists(hdfsPath);
    }

    @Then("^file '(.+?)' does not exist in HDFS$")
    public void fileDoesNotExist(String hdfsPath) throws IOException {
        commonspec.getHDFSSecUtils().fileDoesNotExist(hdfsPath);
    }

    @When("^I list (content|files) in HDFS directory '(.+?)' and save it in environment variable '(.+?)'$")
    public void listDir(String content, String dirPath, String envVar) throws IOException {
        String list = commonspec.getHDFSSecUtils().listFiles(content, dirPath);
        ThreadProperty.set(envVar, list);
    }

    @Given("^I create HDFS directory '(.+?)'( with permissions '(.+?)')?$")
    public void createDirectory(String dir, String permissions) throws IOException {
        commonspec.getHDFSSecUtils().createDirectory(dir, permissions);
    }

    @Given("^I delete HDFS directory '(.+?)'( recursively)?$")
    public void deleteDrectory(String dir, String recursively) throws IOException {
        if (recursively == null) {
            commonspec.getHDFSSecUtils().deleteDirectory(dir, false);
        } else {
            commonspec.getHDFSSecUtils().deleteDirectory(dir, true);
        }
    }
}
