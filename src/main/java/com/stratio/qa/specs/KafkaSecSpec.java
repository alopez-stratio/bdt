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

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.cucumber.datatable.DataTable;
import org.apache.zookeeper.KeeperException;
import org.assertj.core.api.Assertions;

import java.net.UnknownHostException;

/**
 * Generic Kafka Sec Specs.
 *
 * @see <a href="KafkaSecSpec-annotations.html">Kafka Sec Steps &amp; Matching Regex</a>
 */
public class KafkaSecSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public KafkaSecSpec(CommonG spec) {
        this.commonspec = spec;

    }

    /**********************************************
     *   Operations for non-secured kafka
    **********************************************/

    /**
     * Open connection to Kafka cluster
     *
     * @param brokersUrl    brokers URL to connect to cluster
     */
    @Given("I open connection to kafka with url {string}")
    public void openKafkaConnection(String brokersUrl) {
        commonspec.getKafkaSecUtils().createConnection(brokersUrl);
    }

    /**
     * Delete topic in Kafka cluster
     *
     * @param topic topic to delete
     * @throws Exception
     */
    @When("I delete topic {string}")
    public void deleteTopic(String topic) throws Exception {
        commonspec.getKafkaSecUtils().deleteTopic(topic);
    }

    /**
     * Create topic without partitions
     *
     * @param topic         topic to be created
     * @throws Exception
     */
    @When("I create topic {string}")
    public void createTopic(String topic) throws Exception {
        commonspec.getKafkaSecUtils().createTopic(topic, null);
    }

    /**
     * Create topic with partitions
     *
     * @param topic         topic to be created
     * @param partitions    number of partitions to be created (optional)
     * @throws Exception
     */
    @When("I create topic {string} with {string} partitions")
    public void createTopic(String topic, String partitions) throws Exception {
        commonspec.getKafkaSecUtils().createTopic(topic, partitions);
    }

    /**
     * Send a message to a topic
     *
     * @param message       message to be sent
     * @param topic         topic to send message to
     * @throws Exception
     */
    @When("I send message {string} to topic {string}")
    public void produceMessage(String message, String topic) throws Exception {
        commonspec.getKafkaSecUtils().sendMessage(topic, null, message);
    }

    /**
     * Send a message to a topic in a specific partition
     *
     * @param message       message to be sent
     * @param topic         topic to send message to
     * @param partition     partition where to store message (optional)
     * @throws Exception
     */
    @When("I send message {string} to topic {string} in partition {string}")
    public void produceMessage(String message, String topic, String partition) throws Exception {
        commonspec.getKafkaSecUtils().sendMessage(topic, partition, message);
    }

    /**
     * Send transactional message to topic
     *
     * @param topic topic to send transactional messages to
     * @throws Exception
     */
    @When("I send transactional messages to topic {string}")
    public void produceTransactionalMessage(String topic) throws Exception {
        commonspec.getKafkaSecUtils().sendTransactionalMessages(topic);
    }

    /**
     * Check topic exists in Kafka cluster
     *
     * @param topic topic to be checked
     * @throws Exception
     */
    @Then("topic {string} exists")
    public void checkTopicExists(String topic) throws Exception {
        commonspec.getKafkaSecUtils().checkTopicExists(topic);
    }

    /**
     * Check topic does not exist in Kafka cluster
     *
     * @param topic topic to be checked
     * @throws Exception
     */
    @Then("topic {string} does not exist")
    public void checkTopicDoesNotExist(String topic) throws Exception {
        commonspec.getKafkaSecUtils().checkTopicDoesNotExist(topic);
    }

    /**
     * Check whether a topic contains message or not
     *
     * @param topic         topic where to look for message
     * @param message       message to look for
     * @throws Exception
     */
    @Then("topic {string} contains message {string}")
    public void containsMessage(String topic, String message) throws Exception {
        commonspec.getKafkaSecUtils().containsMessage(topic, null, message);
    }

    /**
     * Check whether a topic contains message in a particular partition or not
     *
     * @param topic         topic where to look for message
     * @param message       message to look for
     * @param partition     partition where to look for message (optional)
     * @throws Exception
     */
    @Then("topic {string} contains message {string} in partition {string}")
    public void containsMessage(String topic, String message, String partition) throws Exception {
        commonspec.getKafkaSecUtils().containsMessage(topic, partition, message);
    }

    /**
     * Check topic contains a specific number of messages
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @throws Exception
     */
    @Then("topic {string} contains {string} messages")
    public void containsNMessagesInTopic(String topic, String numMessages) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, null, null);
    }

    /**
     * Check topic contains a specific number of messages in a specific partition
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @param partition     partition where to look for messages (optional)
     * @throws Exception
     */
    @Then("topic {string} contains {string} messages in partition {string}")
    public void containsNMessagesInTopic(String topic, String numMessages, String partition) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, partition, null);
    }

    /**
     * Check topic contains specific messages
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @param values        values to be checked
     * @throws Exception
     */
    @Then("topic {string} contains {string} messages with values:")
    public void containsNMessagesInTopic(String topic, String numMessages, DataTable values) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, null, values);
    }

    /**
     * Check topic contains specific messages in partition
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @param partition     partition where to look for messages (optional)
     * @param values        values to be checked
     * @throws Exception
     */
    @Then("topic {string} contains {string} messages in partition {string} with values:")
    public void containsNMessagesInTopic(String topic, String numMessages, String partition, DataTable values) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, partition, values);
    }

    /**
     * Check that of partitions in a topic matches
     *
     * @param topic             topic to be checked
     * @param numPartitions     expected number of partitions
     * @throws Exception
     */
    @Then("number of partitions in topic {string} is {string}")
    public void checkNumberPartitionsIsN(String topic, String numPartitions) throws Exception {
        commonspec.getKafkaSecUtils().numbersOfPartitionsIsN(topic, numPartitions);
    }

    /**********************************************
     *    Operations for secured kafka
    **********************************************/

    /**
     * Open secured connection to Kafka cluster
     *
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     */
    @Given("^I open connection to kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void openSecuredKafkaConnection(String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws InterruptedException {
        commonspec.getKafkaSecUtils().createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Open secured connection to Kafka cluster
     *
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath   CA path for secured connection
     */
    @Given("^I open connection to kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void openSecuredKafkaConnectionWithPEM(String brokersUrl, String keyPath, String caPath) throws InterruptedException {
        this.commonspec.getKafkaSecUtils().createConnectionWithPEM(brokersUrl, keyPath, caPath);
    }

    /**
     * Check kafka is ready to accept requests in a specific time
     *
     * @param timeout
     * @param wait
     * @throws Exception
     */
    @Given("^I wait '(\\d+)' seconds, checking every '(\\d+)' seconds for kafka server availability$")
    public void checkKafkaReady(Integer timeout, Integer wait) throws Exception {
        commonspec.getKafkaSecUtils().checkKafkaReady(timeout, wait);
    }

    /**
     * Delete topic in Kafka cluster
     *
     * @param topic topic to delete
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @When("^I delete topic '(.+?)' in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void deleteTopic(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().deleteTopic(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Delete topic in Kafka cluster
     *
     * @param topic         topic to delete
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @When("^I delete topic '(.+?)' in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void deleteTopic(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().deleteTopic(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Create topic with/without partitions
     *
     * @param topic         topic to be created
     * @param partitions    number of partitions to be created (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @When("^I create topic '(.+?)'( with '(.+?)' partitions)? in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void createTopic(String topic, String partitions, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().createTopic(topic, partitions, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Create topic with/without partitions
     *
     * @param topic         topic to be created
     * @param partitions    number of partitions to be created (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @When("^I create topic '(.+?)'( with '(.+?)' partitions)? in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void createTopic(String topic, String partitions, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().createTopic(topic, partitions, brokersUrl, keyPath, caPath);
    }

    /**
     * Send a message to a topic
     *
     * @param message       message to be sent
     * @param topic         topic to send message to
     * @param partition     partition where to store message (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @When("^I send message '(.+?)' to topic '(.+?)'( in partition '(.+?)')? in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void produceMessage(String message, String topic, String partition, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().sendMessage(topic, partition, message, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Send a message to a topic
     *
     * @param message       message to be sent
     * @param topic         topic to send message to
     * @param partition     partition where to store message (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @When("^I send message '(.+?)' to topic '(.+?)'( in partition '(.+?)')? in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void produceMessage(String message, String topic, String partition, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().sendMessage(topic, partition, message, brokersUrl, keyPath, caPath);
    }

    /**
     * Send transactional message to topic
     *
     * @param topic topic to send transactional messages to
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @When("^I send transactional messages to topic '(.+?)' in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void produceTransactionalMessage(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().sendTransactionalMessages(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check topic exists in Kafka cluster
     *
     * @param topic topic to be checked
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' exists in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void checkTopicExists(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().checkTopicExists(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check topic exists in Kafka cluster
     *
     * @param topic topic to be checked
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' exists in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void checkTopicExists(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().checkTopicExists(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Check topic does not exist in Kafka cluster
     *
     * @param topic topic to be checked
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' does not exist in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void checkTopicDoesNotExist(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().checkTopicDoesNotExist(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check topic does not exist in Kafka cluster
     *
     * @param topic topic to be checked
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' does not exist in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void checkTopicDoesNotExist(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().checkTopicDoesNotExist(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Check whether a topic contains message in a particular partition or not
     *
     * @param topic         topic where to look for message
     * @param message       message to look for
     * @param partition     partition where to look for message (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains message '(.+?)'( in partition '(.+?)')? in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void containsMessage(String topic, String message, String partition, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().containsMessage(topic, partition, message, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check whether a topic contains message in a particular partition or not
     *
     * @param topic         topic where to look for message
     * @param message       message to look for
     * @param partition     partition where to look for message (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains message '(.+?)'( in partition '(.+?)')? in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void containsMessage(String topic, String message, String partition, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().containsMessage(topic, partition, message, brokersUrl, keyPath, caPath);
    }

    /**
     * Check whether a topic contains the transactional message generated or not
     *
     * @param topic         topic where to look for message
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains transactional messages in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void containsTransactionalMessage(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().containsTransactionalMessages(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check whether a topic contains the transactional message generated or not
     *
     * @param topic         topic where to look for message
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains transactional messages in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void containsTransactionalMessage(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().containsTransactionalMessages(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Check topic contains a specific number of messages
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @param partition     partition where to look for messages (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains '(.+?)' messages( in partition '(.+?)')? in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void containsNMessagesInTopic(String topic, String numMessages, String partition, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, partition, brokersUrl, keystore, keypass, truststore, trustpass, null);
    }

    /**
     * Check topic contains a specific number of messages
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @param partition     partition where to look for messages (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains '(.+?)' messages( in partition '(.+?)')? in kafka with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void containsNMessagesInTopic(String topic, String numMessages, String partition, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, partition, brokersUrl, keyPath, caPath, null);
    }

    /**
     * Check topic contains a specific number of messages
     *
     * @param topic         topic where to look for messages
     * @param numMessages   number of expected messages
     * @param partition     partition where to look for messages (optional)
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @param values        values to be checked
     * @throws Exception
     */
    @Then("^topic '(.+?)' contains '(.+?)' messages( in partition '(.+?)')? in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)' with values:$")
    public void containsNMessagesInTopic(String topic, String numMessages, String partition, String brokersUrl, String keystore, String keypass, String truststore, String trustpass, DataTable values) throws Exception {
        commonspec.getKafkaSecUtils().containsNMessagesInTopic(topic, numMessages, partition, brokersUrl, keystore, keypass, truststore, trustpass, values);
    }

    /**
     * Check that of partitions in a topic matches
     *
     * @param topic             topic to be checked
     * @param numPartitions     expected number of partitions
     * @param brokersUrl    brokers URL to connect to cluster
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^number of partitions in topic '(.+?)' is '(.+?)' in kafka with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void checkNumberPartitionsIsN(String topic, String numPartitions, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().numbersOfPartitionsIsN(topic, numPartitions, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check that I do not have authorization to create topic in kafka cluster
     *
     * @param topic         topic to be created
     * @param brokersUrl    Kafka cluster URL
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^I cannot create topic '(.+?)' with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void cannotCreateTopic(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().cannotCreateTopic(topic, null, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check that I do not have authorization to create topic in kafka cluster
     *
     * @param topic         topic to be created
     * @param brokersUrl    Kafka cluster URL
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^I cannot create topic '(.+?)' with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void cannotCreateTopic(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().cannotCreateTopic(topic, null, brokersUrl, keyPath, caPath);
    }

    @Then("^I cannot delete topic '(.+?)' with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void cannotDeleteTopic(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().cannotDeleteTopic(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    @Then("^I cannot delete topic '(.+?)' with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void cannotDeleteTopic(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().cannotDeleteTopic(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Check that I do not have authorization to send messages to topic
     * @param message       message to be sent
     * @param topic         topic where to send message
     * @param brokersUrl    Kafka cluster URL
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^I cannot send message '(.+?)' to topic '(.+?)' with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void cannotSendMessage(String message, String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().cannotSendMessage(topic, null, message, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check that I do not have authorization to send messages to topic
     * @param message       message to be sent
     * @param topic         topic where to send message
     * @param brokersUrl    Kafka cluster URL
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^I cannot send message '(.+?)' to topic '(.+?)' with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void cannotSendMessage(String message, String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().cannotSendMessage(topic, null, message, brokersUrl, keyPath, caPath);
    }

    /**
     * Check that I do not have authorization to send transactional messages to topic
     *
     * @param topic         topic where to send message
     * @param brokersUrl    Kafka cluster URL
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^I cannot send transactional messages to topic '(.+?)' with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void cannotSendTransactionalMessages(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().cannotSendTransactionalMessages(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check that I do not have authorization to send transactional messages to topic
     *
     * @param topic         topic where to send message
     * @param brokersUrl    Kafka cluster URL
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^I cannot send transactional messages to topic '(.+?)' with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void cannotSendTransactionalMessages(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().cannotSendTransactionalMessages(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Check that I do not have authorization to consume messages from topic
     *
     * @param topic         topic where to send message
     * @param brokersUrl    Kafka cluster URL
     * @param keystore      keystore for secured connection
     * @param keypass       key password for secured connection
     * @param truststore    truststore for secured connection
     * @param trustpass     password for secured connection
     * @throws Exception
     */
    @Then("^I cannot consume messages from topic '(.+?)' with url '(.+?)' with keyStorePath '(.+?)' and keyStorePassword '(.+?)' and trustStorePath '(.+?)' and trustStorePassword '(.+?)'$")
    public void cannotConsumeMessages(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        commonspec.getKafkaSecUtils().cannotConsumeMessages(topic, brokersUrl, keystore, keypass, truststore, trustpass);
    }

    /**
     * Check that I do not have authorization to consume messages from topic
     *
     * @param topic         topic where to send message
     * @param brokersUrl    Kafka cluster URL
     * @param keyPath       key path for secured connection
     * @param caPath        CA path for secured connection
     * @throws Exception
     */
    @Then("^I cannot consume messages from topic '(.+?)' with url '(.+?)' using PEM with keyPath '(.+?)' and caPath '(.+?)'$")
    public void cannotConsumeMessages(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        commonspec.getKafkaSecUtils().cannotConsumeMessages(topic, brokersUrl, keyPath, caPath);
    }

    /**
     * Close previously opened connection to Kafka cluster
     */
    @Given("^I close Kafka connection$")
    public void closeKafkaConnection() {
        commonspec.getKafkaSecUtils().closeConnection();
    }
}
