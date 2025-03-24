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

import com.stratio.qa.assertions.Assertions;
import io.cucumber.datatable.DataTable;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.Properties;

import static java.util.Arrays.asList;

public class KafkaSecUtils {

    private final Logger logger = LoggerFactory.getLogger(KafkaSecUtils.class);

    private final long kafkaConsumerTimeoutMS;

    private final long kafkaProducerTimeoutMS;

    private final long KAFKA_DEFAULT_TIMEOUT_MS;

    Properties kafkaConnectionProperties;

    Properties kafkaProducerProperties;

    Properties kafkaConsumerProperties;

    AdminClient adminClient;

    public KafkaSecUtils() {
        kafkaConsumerTimeoutMS = System.getProperty("KAFKA_CONSUMER_TIMEOUT_MS") != null ? Long.parseLong(System.getProperty("KAFKA_CONSUMER_TIMEOUT_MS")) : 10000L;
        kafkaProducerTimeoutMS = System.getProperty("KAFKA_PRODUCER_TIMEOUT_MS") != null ? Long.parseLong(System.getProperty("KAFKA_PRODUCER_TIMEOUT_MS")) : 10000L;
        KAFKA_DEFAULT_TIMEOUT_MS = System.getProperty("KAFKA_DEFAULT_TIMEOUT_MS") != null ? Long.parseLong(System.getProperty("KAFKA_DEFAULT_TIMEOUT_MS")) : 20000L;

        kafkaConnectionProperties = new Properties();
        kafkaProducerProperties = new Properties();
        kafkaConsumerProperties = new Properties();

        setKafkaConnectionProperties();
    }

    private void setKafkaConnectionProperties() {
        // Producer properties
        kafkaProducerProperties.put(ProducerConfig.BATCH_SIZE_CONFIG, 100);
        kafkaProducerProperties.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        kafkaProducerProperties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) kafkaProducerTimeoutMS / 2);
        kafkaProducerProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) kafkaProducerTimeoutMS / 2);
        kafkaProducerProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) kafkaProducerTimeoutMS);
        kafkaProducerProperties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1048576); // 1 MByte Buffer Memory Size
        kafkaProducerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        // Consumer properties
        kafkaConsumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumerProperties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        kafkaConsumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaConsumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
    }

    public void createConnection(String brokersUrl) {
        kafkaConnectionProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);
        kafkaProducerProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);
        kafkaConsumerProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);

        logger.debug("Creating non-secured Kafka connection: " + brokersUrl);
        kafkaConnectionProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        kafkaProducerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        kafkaConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");

        adminClient = KafkaAdminClient.create(kafkaConnectionProperties);
        logger.debug("Kafka connection created.");
    }

    public void createConnection(String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws InterruptedException {
        if (adminClient != null) {
            closeConnection();
        }

        kafkaConnectionProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);
        kafkaProducerProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);
        kafkaConsumerProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);

        logger.debug("Creating secured Kafka connection: " + brokersUrl);
        kafkaConnectionProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        kafkaConnectionProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststore);
        kafkaConnectionProperties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustpass);
        kafkaConnectionProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystore);
        kafkaConnectionProperties.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keypass);

        kafkaProducerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        kafkaProducerProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststore);
        kafkaProducerProperties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustpass);
        kafkaProducerProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystore);
        kafkaProducerProperties.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keypass);

        kafkaConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        kafkaConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststore);
        kafkaConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustpass);
        kafkaConsumerProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystore);
        kafkaConsumerProperties.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keypass);

        adminClient = KafkaAdminClient.create(kafkaConnectionProperties);
        logger.debug("Kafka connection created.");
    }

    public void createConnectionWithPEM(String brokersUrl, String keyPath, String caPath) throws InterruptedException {
        if (adminClient != null) {
            closeConnection();
        }

        kafkaConnectionProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);
        kafkaProducerProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);
        kafkaConsumerProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokersUrl);

        logger.debug("Creating secured Kafka connection: " + brokersUrl);
        kafkaConnectionProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        kafkaConnectionProperties.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
        kafkaConnectionProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
        kafkaConnectionProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyPath);
        kafkaConnectionProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, caPath);

        kafkaProducerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        kafkaProducerProperties.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
        kafkaProducerProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
        kafkaProducerProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyPath);
        kafkaProducerProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, caPath);

        kafkaConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        kafkaConsumerProperties.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
        kafkaConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
        kafkaConsumerProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyPath);
        kafkaConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, caPath);

        adminClient = KafkaAdminClient.create(kafkaConnectionProperties);
        logger.debug("Kafka connection with PEM created.");
    }

    public void checkKafkaReady(int timeout, int wait) throws Exception {
        boolean ready = false;

        for (int i = 0; (i <= timeout); i += wait) {
            try {
                ListTopicsResult topics = adminClient.listTopics();
                Set<String> names = topics.names().get();
                if (names.isEmpty()) {
                    // case: if no topic found.
                    logger.info("Kafka not ready yet to accept requests. Waiting...");
                    Thread.sleep(wait * 1000);
                } else {
                    ready = true;
                    logger.info("Kafka ready to accept requests after " + i + " seconds.");
                    break;
                }
            } catch (Exception e) {
                // Kafka is not available
                logger.info("Kafka not ready yet to accept requests. Waiting...");
                Thread.sleep(wait * 1000);
            }
        }

        if (!ready) {
            throw new Exception("Kafka not ready after " + timeout + " seconds.");
        }
    }

    public void closeConnection() {
        logger.debug("Closing kafka connection: " + kafkaConnectionProperties.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
        if (adminClient != null) {
            adminClient.close();
            logger.debug("Kafka connection closed.");
        } else {
            logger.debug("No Kafka connection opened. Nothing to close.");
        }
    }


    public String listTopics() throws Exception {
        String topics;

        topics = adminClient.listTopics().names().get(KAFKA_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS).toString();

        return topics;
    }

    public String listTopics(String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        String topics;

        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // list topics
        topics = listTopics();
        // close connection
        closeConnection();

        return topics;
    }

    public void deleteTopic(String topic) throws Exception {
        logger.debug("Deleting topic: " + topic);
        adminClient.deleteTopics(asList(topic)).all().get(KAFKA_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        logger.debug("Topic deleted.");
    }

    public void deleteTopic(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // delete topic
        deleteTopic(topic);
        // close connection
        closeConnection();
    }

    public void deleteTopic(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        // create connection
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // delete topic
        deleteTopic(topic);
        // close connection
        closeConnection();
    }

    public void checkTopicExists(String topic) throws Exception {
        Set<String> topicsList = adminClient.listTopics().names().get(KAFKA_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        Assertions.assertThat(topicsList.contains(topic)).as("Topic " + topic + " does not exist.").isTrue();
    }

    public void checkTopicExists(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        // create connection
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // check topic exists
        checkTopicExists(topic);
        // close connection
        closeConnection();
    }

    public void checkTopicExists(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // check topic exists
        checkTopicExists(topic);
        // close connection
        closeConnection();
    }

    public void checkTopicDoesNotExist(String topic) throws Exception {
        Set<String> topicsList = adminClient.listTopics().names().get(KAFKA_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        Assertions.assertThat(topicsList.contains(topic)).as("Topic " + topic + " exists.").isFalse();
    }

    public void checkTopicDoesNotExist(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // check topic does not exist
        checkTopicDoesNotExist(topic);
        // close connection
        closeConnection();
    }

    public void checkTopicDoesNotExist(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        // create connection
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // check topic does not exist
        checkTopicDoesNotExist(topic);
        // close connection
        closeConnection();
    }

    public void createTopic(String topic, String numPartitions) throws Exception {
        // Create the testAT topic
        Map<String, String> topicProperties = new HashMap<String, String>() {
            {
                put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1");
            }
        };

        logger.debug("Creating topic: " + topic);
        NewTopic newTopic = new NewTopic(topic, 1, (short) 1).configs(topicProperties);
        adminClient.createTopics(asList(newTopic)).all().get(KAFKA_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        logger.debug("Topic created.");

        if (numPartitions != null) {
            // create partitions
            logger.debug("Creating: " + numPartitions + " partitions in topic: " + topic);
            Map<String, NewPartitions> partitions = new HashMap<String, NewPartitions>() {
                {
                    put (topic, NewPartitions.increaseTo(Integer.parseInt(numPartitions)));
                }
            };

            adminClient.createPartitions(partitions).all().get(KAFKA_DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            logger.debug("Partitions created.");
        }
    }

    public void createTopic(String topic, String numPartitions, String brokersUrl, String keyPath, String caPath) throws Exception {
        // Create connection with new connection details
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // create topic
        createTopic(topic, numPartitions);
        // close connection
        closeConnection();
    }

    public void createTopic(String topic, String numPartitions, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // Create connection with new connection details
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // create topic
        createTopic(topic, numPartitions);
        // close connection
        closeConnection();
    }

    public void cannotCreateTopic(String topic, String numPartitions, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            createTopic(topic, numPartitions, brokersUrl, keystore, keypass, truststore, trustpass);

            logger.error("NoAuth Exception not launched. Check that ACLs are set to create that Topic");
        } catch (Exception createException) {
            if (createException.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                logger.info("NoAuth creating {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to create that Topic").isTrue();
    }

    public void cannotCreateTopic(String topic, String numPartitions, String brokersUrl, String keyPath, String caPath) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            createTopic(topic, numPartitions, brokersUrl, keyPath, caPath);

            logger.error("NoAuth Exception not launched. Check that ACLs are set to create that Topic");
        } catch (Exception createException) {
            if (createException.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                logger.info("NoAuth creating {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to create that Topic").isTrue();
    }

    public void cannotDeleteTopic(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            deleteTopic(topic, brokersUrl, keystore, keypass, truststore, trustpass);

            logger.error("NoAuth Exception not launched. Check that ACLs are set to create that Topic");
        } catch (Exception createException) {
            if (createException.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                logger.info("NoAuth deleting {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to delete that Topic").isTrue();

    }

    public void cannotDeleteTopic(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            deleteTopic(topic, brokersUrl, keyPath, caPath);

            logger.error("NoAuth Exception not launched. Check that ACLs are set to create that Topic");
        } catch (Exception createException) {
            if (createException.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                logger.info("NoAuth deleting {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to delete that Topic").isTrue();

    }

    public void sendMessage(String topic, String partition, String message) throws Exception {
        try (KafkaProducer producer = new KafkaProducer<String, String>(kafkaProducerProperties)) {
            ProducerRecord record;
            if (partition == null) {
                logger.debug("Sending message to topic: " + topic);
                record = new ProducerRecord<>(topic, message);
            } else {
                logger.debug("Sending message to topic: " + topic + " and partition: " + partition);
                record = new ProducerRecord<>(topic, Integer.valueOf(partition), kafkaConnectionProperties.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG), message);
            }
            producer.send(record, (metadata, exception) -> {

                if (exception != null) {
                    logger.debug("Error sending message: {}", exception);
                } else {
                    logger.debug("Message sent and acknowlegded by Kafka RIGHT metadata {}", metadata.toString());
                }

            }).get(kafkaProducerTimeoutMS, TimeUnit.MILLISECONDS);

            logger.debug("Message sent and acknowlegded by Kafka");
        } catch (Exception e) {
            logger.error("Message not sent or acknowlegded by Kafka {}", e.getMessage());
            throw e;
        }
    }

    public void sendMessage(String topic, String partition, String message, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // Create connection with new connection details
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // send message
        sendMessage(topic, partition, message);
        // close connection
        closeConnection();
    }

    public void sendMessage(String topic, String partition, String message, String brokersUrl, String keyPath, String caPath) throws Exception {
        // Create connection with new connection details
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // send message
        sendMessage(topic, partition, message);
        // close connection
        closeConnection();
    }

    public void cannotSendMessage(String topic, String partition, String message, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            sendMessage(topic, partition, message, brokersUrl, keystore, keypass, truststore, trustpass);

            logger.error("NoAuth Exception not launched. Check that ACLs are set to produce messages to Topic");
        } catch (Exception sendException) {
            if (sendException.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                logger.info("NoAuth sending message to {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to produce messages to Topic").isTrue();
    }

    public void cannotSendMessage(String topic, String partition, String message, String brokersUrl, String keyPath, String caPath) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            sendMessage(topic, partition, message, brokersUrl, keyPath, caPath);

            logger.error("NoAuth Exception not launched. Check that ACLs are set to produce messages to Topic");
        } catch (Exception sendException) {
            if (sendException.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                logger.info("NoAuth sending message to {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to produce messages to Topic").isTrue();
    }

    public void sendTransactionalMessages(String topic) throws Exception {
        kafkaProducerProperties.put("transactional.id", "transactionIdAT");

        try (KafkaProducer producer = new KafkaProducer<String, String>(kafkaProducerProperties)) {
            producer.initTransactions();
            producer.beginTransaction();

            for (int i = 0; i < 5; i++) {
                ProducerRecord record = new ProducerRecord<>(topic, kafkaConnectionProperties.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG), "testsAT_transactional_message_" + i);
                producer.send(record).get(kafkaProducerTimeoutMS, TimeUnit.MILLISECONDS);
            }

            producer.commitTransaction();
            logger.debug("Transactional Messages sent to topic: " + topic);
        } catch (Exception e) {
            logger.error("Transactional Messages not sent to topic: {} with error: {}", topic, e.getMessage());
            throw e;
        }
    }

    public void sendTransactionalMessages(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // Create connection with new connection details
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // send transactional messages
        sendTransactionalMessages(topic);
        // close connection
        closeConnection();
    }

    public void sendTransactionalMessages(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        // Create connection with new connection details
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // send transactional messages
        sendTransactionalMessages(topic);
        // close connection
        closeConnection();
    }

    public void cannotSendTransactionalMessages(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            sendTransactionalMessages(topic, brokersUrl, keystore, keypass, truststore, trustpass);
            logger.error("NoAuth Exception not launched. Check that ACLs are set to produce transactional messages to Topic");
        } catch (Exception sendTransactionalException) {
            if (sendTransactionalException instanceof org.apache.kafka.common.errors.TransactionalIdAuthorizationException) {
                logger.info("NoAuth sending transactional message to {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to produce transactional messages to Topic").isTrue();
    }

    public void cannotSendTransactionalMessages(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            sendTransactionalMessages(topic, brokersUrl, keyPath, caPath);
            logger.error("NoAuth Exception not launched. Check that ACLs are set to produce transactional messages to Topic");
        } catch (Exception sendTransactionalException) {
            if (sendTransactionalException instanceof org.apache.kafka.common.errors.TransactionalIdAuthorizationException) {
                logger.info("NoAuth sending transactional message to {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to produce transactional messages to Topic").isTrue();
    }

    public void cannotConsumeMessages(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            containsMessage(topic, null, "message", brokersUrl, keystore, keypass, truststore, trustpass);
            logger.error("NoAuth Exception not launched. Check that ACLs are set to consume messages to Topic");
        } catch (Exception consumeException) {
            if (consumeException.getMessage().contains("Not authorized")) {
                logger.info("NoAuth consuming message from {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to consume messages in Topic").isTrue();
    }

    public void cannotConsumeMessages(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        boolean isLaunchedNoAuthException = false;

        try {
            containsMessage(topic, null, "message", brokersUrl, keyPath, caPath);
            logger.error("NoAuth Exception not launched. Check that ACLs are set to consume messages to Topic");
        } catch (Exception consumeException) {
            if (consumeException.getMessage().contains("Not authorized")) {
                logger.info("NoAuth consuming message from {} topic", topic);
                isLaunchedNoAuthException = true;
            }
        }

        Assertions.assertThat(isLaunchedNoAuthException).as("NoAuth Exception not launched. Check that ACLs are set to consume messages in Topic").isTrue();
    }

    public void containsMessage(String topic, String partitionId, String message) throws Exception {
        kafkaConsumerProperties.put("group.id", "functionalAT");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaConsumerProperties)) {

            List<String> result = new ArrayList<>();

            if (partitionId == null) {
                consumer.subscribe(asList(topic));
            } else {
                TopicPartition partition = new TopicPartition(topic, Integer.valueOf(partitionId));
                consumer.assign(asList(partition));
            }

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(kafkaConsumerTimeoutMS));

            for (ConsumerRecord<String, String> record : records) {
                result.add(record.value());
            }

            Assertions.assertThat(result.contains(message)).as("Topic does not exist or the content does not match").isTrue();
        } catch (Exception e) {
            logger.error("Not possible to consume from topic: {} with error: {}", topic, e.getMessage());
            throw e;
        }
    }

    public void containsMessage(String topic, String partitionId, String message, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // open connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // check topic contains message
        containsMessage(topic, partitionId, message);
        // close connection
        closeConnection();
    }

    public void containsMessage(String topic, String partitionId, String message, String brokersUrl, String keyPath, String caPath) throws Exception {
        // open connection
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // check topic contains message
        containsMessage(topic, partitionId, message);
        // close connection
        closeConnection();
    }

    public void containsTransactionalMessages(String topic) throws Exception {
        String message = "testsAT_transactional_message_";
        kafkaConsumerProperties.put("group.id", "functionalAT");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaConsumerProperties)) {

            List<String> result = new ArrayList<>();

            consumer.subscribe(asList(topic));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(kafkaConsumerTimeoutMS));

            for (ConsumerRecord<String, String> record : records) {
                result.add(record.value());
            }

            for (int i = 0; i < 5; i++) {
                Assertions.assertThat(result.contains(message + i)).as("Topic does not exist or the content does not match").isTrue();
            }
        } catch (Exception e) {
            logger.error("Not possible to consume from topic: {} with error: {}", topic, e.getMessage());
            throw e;
        }
    }

    public void containsTransactionalMessages(String topic, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // check topic contains transactional messages
        containsTransactionalMessages(topic);
        // close connection
        closeConnection();
    }

    public void containsTransactionalMessages(String topic, String brokersUrl, String keyPath, String caPath) throws Exception {
        // create connection
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // check topic contains transactional messages
        containsTransactionalMessages(topic);
        // close connection
        closeConnection();
    }

    public void containsNMessagesInTopic(String topic, String numMessages, String partitionId, DataTable values) throws Exception {
        kafkaConsumerProperties.put("group.id", "functionalAT");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kafkaConsumerProperties)) {

            List<String> result = new ArrayList<>();

            if (partitionId == null) {
                consumer.subscribe(asList(topic));
            } else {
                TopicPartition partition = new TopicPartition(topic, Integer.valueOf(partitionId));
                consumer.assign(asList(partition));
            }

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(kafkaConsumerTimeoutMS));

            for (ConsumerRecord<String, String> record : records) {
                result.add(record.value());
            }

            Assertions.assertThat(result.size()).as("Expected number of messages: " + numMessages + ", different from existing ones: " + result.size()).isEqualTo(Integer.parseInt(numMessages));

            if (values != null) {
                for (int i = 0; i < values.cells().size(); i++) {
                    String value = values.cells().get(i).get(0);
                    Assertions.assertThat(result.contains(value)).as("Topic: " + topic + " does not contain value: " + value).isTrue();
                }
            }
        } catch (Exception e) {
            logger.error("Not possible to consume from topic: {} with error: {}", topic, e.getMessage());
            throw e;
        }
    }

    public void containsNMessagesInTopic(String topic, String numMessages, String partitionId, String brokersUrl, String keystore, String keypass, String truststore, String trustpass, DataTable values) throws Exception {
        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // check topic contains specified messages
        containsNMessagesInTopic(topic, numMessages, partitionId, values);
        // close connection
        closeConnection();
    }

    public void containsNMessagesInTopic(String topic, String numMessages, String partitionId, String brokersUrl, String keyPath, String caPath, DataTable values) throws Exception {
        // create connection
        createConnectionWithPEM(brokersUrl, keyPath, caPath);
        // check topic contains specified messages
        containsNMessagesInTopic(topic, numMessages, partitionId, values);
        // close connection
        closeConnection();
    }

    public void numbersOfPartitionsIsN(String topic, String numPartitions) throws Exception {
        int partitions = adminClient.describeTopics(asList(topic)).all().get().get(topic).partitions().size();

        Assertions.assertThat(partitions).as("Expected number of partitions: " + numPartitions + " is different from obtained one: " + partitions).isEqualTo(Integer.parseInt(numPartitions));
    }

    public void numbersOfPartitionsIsN(String topic, String numPartitions, String brokersUrl, String keystore, String keypass, String truststore, String trustpass) throws Exception {
        // create connection
        createConnection(brokersUrl, keystore, keypass, truststore, trustpass);
        // check number of partitions in topic is the specified
        numbersOfPartitionsIsN(topic, numPartitions);
        // close connection
        closeConnection();
    }
}
