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
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.fs.permission.FsPermission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HDFSSecUtils {

    private final Logger logger = LoggerFactory.getLogger(HDFSSecUtils.class);

    private ByteArrayOutputStream outputStream;

    private ByteArrayOutputStream errorStream;

    private Configuration conf = new Configuration();

    public HDFSSecUtils() {
        conf.set("hadoop.home.dir", "/");
    }

    public void createSecuredHDFSConnection(String coreSite, String hdfsSite, String krb5Conf, String sslClient, String hdfsHost, String keytabPath, String truststorePath, String realm) throws Exception {
        // Check that ssl-config.xml file provided points to truststore path provided
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(sslClient);

        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();

        XPathExpression expr = xpath.compile("/configuration/property[name='ssl.client.truststore.location']/value/text()");
        String result = expr.evaluate(doc);

        Assertions.assertThat(result).as("Truststore path specified in ssl-client.xml: " + result + " is different from provided one: " + truststorePath).isEqualTo(truststorePath);

        conf.addResource(new Path("file:///" + coreSite));
        conf.addResource(new Path("file:///" + hdfsSite));

        System.setProperty("java.security.krb5.conf", krb5Conf);

        conf.addResource(new Path("file:///" + sslClient));
        conf.set("fs.defaultFS", "hdfs://" + hdfsHost + "/");

        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation.loginUserFromKeytab("hdfs/" + hdfsHost + "@" + realm, keytabPath);
    }

    public void createExternalHDFSConnection(String principal, String hdfsHost, String keytabPath, String realm, String krb5Kdc, boolean privacyProtection) throws IOException {
        // set kerberos host and realm
        System.setProperty("java.security.krb5.realm", realm);
        System.setProperty("java.security.krb5.kdc", krb5Kdc);

        conf.set("hadoop.security.authentication", "kerberos");
        conf.set("hadoop.security.authorization", "true");
        if (privacyProtection) {
            conf.set("hadoop.rpc.protection", "privacy");
        }

        conf.set("fs.defaultFS", "hdfs://" + hdfsHost);
        conf.set("fs.hdfs.impl", DistributedFileSystem.class.getName());

        // hack for running locally with fake DNS records
        // set this to true if overriding the host name in /etc/hosts
        conf.set("dfs.client.use.datanode.hostname", "true");

        // server principal
        // the kerberos principle that the namenode is using
        conf.set("dfs.namenode.kerberos.principal.pattern", "*@" + realm);

        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation.loginUserFromKeytab(principal + "@" + realm, keytabPath);
    }

    public void closeConnection() {
        conf.clear();
        conf = null;
        logger.info("Connection closed sucessfully.");
    }

    public void writeToHDFS(String sourcePath, String destinationPath) throws IOException {
        Path inputPath = new Path(sourcePath);
        Path outputPath = new Path(destinationPath);

        FileSystem fileSystem = FileSystem.get(conf);
        fileSystem.copyFromLocalFile(inputPath, outputPath);
        fileSystem.close();
    }

    public void readFromHDFS(String hdfsStorePath, String localSystemPath) throws IOException {
        Path hdfsPath = new Path(hdfsStorePath);
        Path localPath = new Path(localSystemPath);

        FileSystem fileSystem = FileSystem.get(conf);
        fileSystem.copyToLocalFile(hdfsPath, localPath);
        fileSystem.close();
    }

    public void deleteFile(String hdfsStorePath) throws IOException {
        Path hdfsPath = new Path(hdfsStorePath);

        FileSystem fileSystem = FileSystem.get(conf);

        Assertions.assertThat(fileSystem.exists(hdfsPath)).as("Path provided: " + hdfsStorePath + " does not exist.").isTrue();
        Assertions.assertThat(fileSystem.isFile(hdfsPath)).as("Path provided: " + hdfsStorePath + " is not a file.").isTrue();

        fileSystem.delete(hdfsPath, false);

        logger.info("File: {} deleted successfully." + hdfsPath);
    }

    public void fileExists(String hdfsStorePath) throws IOException {
        Path hdfsPath = new Path(hdfsStorePath);

        FileSystem fileSystem = FileSystem.get(conf);

        Assertions.assertThat(fileSystem.exists(hdfsPath)).as("File: " + hdfsStorePath + " does not exist.").isTrue();
    }

    public void fileDoesNotExist(String hdfsStorePath) throws IOException {
        Path hdfsPath = new Path(hdfsStorePath);

        FileSystem fileSystem = FileSystem.get(conf);

        Assertions.assertThat(fileSystem.exists(hdfsPath)).as("File: " + hdfsStorePath + " exists.").isFalse();
    }

    public String listFiles(String content, String hdfsDirPath) throws IOException {
        String files = "";
        Path path = new Path(hdfsDirPath);

        FileSystem fileSystem = FileSystem.get(conf);

        if ("files".equals(content)) {
            RemoteIterator<LocatedFileStatus> iterator = fileSystem.listFiles(path, false);

            while (iterator.hasNext()) {
                files = files + iterator.next().getPath().getName() + "\n";
            }
        } else {
            FileStatus[] status = fileSystem.listStatus(path);
            for (int i = 0; i < status.length; i++) {
                if (status[i].isDirectory()) {
                    files = files + status[i].getPath().getName() + "/\n";
                } else {
                    files = files + status[i].getPath().getName() + "\n";
                }
            }
        }

        return files.trim();
    }

    public void createDirectory(String dir, String permissions) throws IOException {
        Path path = new Path(dir);

        FileSystem fileSystem = FileSystem.get(conf);

        if (permissions == null) {
            fileSystem.mkdirs(path, new FsPermission(FsPermission.getDefault()));
            logger.info("Directory: {} created successfully." + dir);
        } else {
            fileSystem.mkdirs(path, new FsPermission(permissions));
            logger.info("Directory: {} created successfully." + dir);
        }
    }

    public void deleteDirectory(String dir, boolean recursive) throws IOException {
        Path path = new Path(dir);

        FileSystem fileSystem = FileSystem.get(conf);

        Assertions.assertThat(fileSystem.exists(path)).as("Path provided: " + dir + " does not exist.").isTrue();

        Assertions.assertThat(fileSystem.isDirectory(path)).as("Path provided: " + dir + " is not a directory.").isTrue();

        fileSystem.delete(path, recursive);
        logger.info("Directory: {} deleted successfully." + dir);
    }
}
