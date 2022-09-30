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

import com.jcraft.jsch.Session;
import com.stratio.qa.utils.RemoteSSHConnection;
import com.stratio.qa.utils.RemoteSSHConnectionsUtil;
import com.stratio.qa.utils.ThreadProperty;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;

import static com.stratio.qa.assertions.Assertions.assertThat;

/**
 * Generic Command Execution Specs.
 *
 * @see <a href="CommandExecutionSpec-annotations.html">Command Execution Steps &amp; Matching Regex</a>
 */
public class CommandExecutionSpec extends BaseGSpec {

    /**
     * Generic constructor.
     *
     * @param spec object
     */
    public CommandExecutionSpec(CommonG spec) {
        this.commonspec = spec;
    }

    /**
     * Opens a ssh connection to remote host
     *
     * @param remoteHost remote host
     * @param user       remote user
     * @param password   (required if pemFile null)
     * @param pemFile    (required if password null)
     * @throws Exception exception
     */
    @Given("^I open a ssh connection to '(.+?)'( in port '(.+?)')? with user '(.+?)'( and password '(.+?)')?( using pem file '(.+?)')?( and saved with id '(.+?)')?$")
    public void openSSHConnection(String remoteHost, String remotePort, String user, String password, String pemFile, String sshConnectionId) throws Exception {
        String sshConnectionIdAux = sshConnectionId != null ? sshConnectionId : remoteHost + "_" + user;
        if (RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().get(sshConnectionId) != null) {
            RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().get(sshConnectionId).getSession().disconnect();
        }
        if ((pemFile == null) || (pemFile.equals("none"))) {
            if (password == null) {
                throw new Exception("You have to provide a password or a pem file to be used for connection");
            }
            commonspec.setRemoteSSHConnection(new RemoteSSHConnection(user, password, remoteHost, remotePort, null), sshConnectionIdAux);
            commonspec.getLogger().debug("Opening ssh connection with password: { " + password + "}", sshConnectionIdAux);
        } else {
            File pem = new File(pemFile);
            if (!pem.exists()) {
                throw new Exception("Pem file: " + pemFile + " does not exist");
            }
            commonspec.setRemoteSSHConnection(new RemoteSSHConnection(user, null, remoteHost, remotePort, pemFile), sshConnectionIdAux);
            commonspec.getLogger().debug("Opening ssh connection with pemFile: {}", sshConnectionIdAux);
        }
    }

    @Deprecated
    public void openSSHConnection(String remoteHost, String remotePort, String user, String password, String pemFile) throws Exception {
        openSSHConnection(remoteHost, remotePort, user, password, pemFile, null);
    }

    /*
     * Copies file/s from remote system into local system
     *
     * @param remotePath path where file is going to be copy
     * @param localPath path where file is located
     * @throws Exception exception
     *
     */
    @Given("^I inbound copy '(.+?)' through a ssh connection to '(.+?)'$")
    public void copyFromRemoteFile(String remotePath, String localPath) throws Exception {
        commonspec.getRemoteSSHConnection().copyFrom(remotePath, localPath);
    }


    /**
     * Copies file/s from local system to remote system
     *
     * @param remotePath path where file is going to be copied
     * @param localPath  path where file is located
     * @throws Exception exception
     */
    @Given("^I outbound copy '(.+?)' through a ssh connection to '(.+?)'$")
    public void copyToRemoteFile(String localPath, String remotePath) throws Exception {
        commonspec.getRemoteSSHConnection().copyTo(localPath, remotePath);
    }


    /**
     * Executes the command specified in local system
     *
     * @param command    command to be run locally
     * @param sExitStatus command exit status
     * @param envVar     environment variable name
     * @throws Exception exception
     **/
    @Given("^I run '(.+?)' locally( with exit status '(\\d+)')?( and save the value in environment variable '(.+?)')?$")
    public void executeLocalCommand(String command, String sExitStatus, String envVar) throws Exception {
        Integer exitStatus = sExitStatus != null ? Integer.valueOf(sExitStatus) : null;
        if (exitStatus == null) {
            exitStatus = 0;
        }

        commonspec.runLocalCommand(command);
        commonspec.runCommandLoggerAndEnvVar(exitStatus, envVar, Boolean.TRUE);

        Assertions.assertThat(commonspec.getCommandExitStatus()).isEqualTo(exitStatus);
    }

    /**
     * Executes the command specified in remote system
     *
     * @param command    command to be run locally
     * @param sExitStatus command exit status
     * @param envVar     environment variable name
     * @throws Exception exception
     **/
    @Given("^I run '(.+?)' in the ssh connection( with id '(.+?)')?( with exit status '(.+?)')?( and save the value in environment variable '(.+?)')?$")
    public void executeCommand(String command, String sshConnectionId, String sExitStatus, String envVar) throws Exception {
        Integer exitStatus = sExitStatus != null ? Integer.valueOf(sExitStatus) : null;
        if (exitStatus == null) {
            exitStatus = 0;
        }

        commonspec.executeCommand(command, sshConnectionId, exitStatus, envVar);
    }

    @Deprecated
    public void executeCommand(String command, String sExitStatus, String envVar) throws Exception {
        executeCommand(command, null, sExitStatus, envVar);
    }


    /**
     * Checks if {@code expectedCount} text is found, whithin a {@code timeout} in the output of local command {@code command}
     * and the exit status is the specified in {@code exitStatus}.
     * Each negative lookup is followed by a wait of {@code wait} seconds.
     *
     * @param timeout
     * @param wait
     * @param command
     * @param search
     * @param sExitStatus
     * @throws InterruptedException
     */
    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, the local command output '(.+?)' contains '(.*?)'( with exit status '(\\d+)')?$")
    public void assertCommandExistsOnTimeOutLocal(Integer timeout, Integer wait, String command, String search, String sExitStatus) throws Exception {
        Integer exitStatus = sExitStatus != null ? Integer.valueOf(sExitStatus) : null;
        Boolean found = false;
        for (int i = 0; (i <= timeout); i += wait) {
            if (found) {
                break;
            }
            commonspec.getLogger().debug("Checking local output value");
            commonspec.runLocalCommand(command);
            try {
                if (exitStatus != null) {
                    assertThat(commonspec.getCommandExitStatus()).isEqualTo(exitStatus);
                }
                assertThat(commonspec.getCommandResult()).as("Contains " + search + ".").contains(search);
                found = true;
                timeout = i;
            } catch (AssertionError e) {
                commonspec.getLogger().info("Local command output don't found yet after " + i + " seconds");
                Thread.sleep(wait * 1000);
            }
        }

        if (!found) {
            throw new Exception("Local command output don't found yet after " + timeout + " seconds");
        }
        commonspec.getLogger().info("Local command output found after " + timeout + " seconds");
    }

    /**
     * Checks if {@code expectedCount} text is found, whithin a {@code timeout} in the output of local command {@code command}
     * and the exit status is the specified in {@code exitStatus}.
     * Each negative lookup is followed by a wait of {@code wait} seconds.
     *
     * @param timeout
     * @param wait
     * @param command
     * @param search
     * @param sExitStatus
     * @throws InterruptedException
     */
    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, the local command output '(.+?)' is equal to '(.*?)'( with exit status '(\\d+)')?$")
    public void assertCommandIsEqualToOnTimeOut(Integer timeout, Integer wait, String command, String search, String sExitStatus) throws Exception {
        Integer exitStatus = sExitStatus != null ? Integer.valueOf(sExitStatus) : null;
        Boolean found = false;
        for (int i = 0; (i <= timeout); i += wait) {
            if (found) {
                break;
            }
            commonspec.getLogger().debug("Checking local output value");
            commonspec.runLocalCommand(command);
            try {
                if (exitStatus != null) {
                    assertThat(commonspec.getCommandExitStatus()).isEqualTo(exitStatus);
                }
                assertThat(commonspec.getCommandResult()).as("Equal to " + search + ".").isEqualTo(search);
                found = true;
                timeout = i;
            } catch (AssertionError e) {
                commonspec.getLogger().info("Local command output don't found yet after " + i + " seconds");
                Thread.sleep(wait * 1000);
            }
        }

        if (!found) {
            throw new Exception("Local command output don't found yet after " + timeout + " seconds");
        }
        commonspec.getLogger().info("Local command output found after " + timeout + " seconds");
    }

    /**
     * Checks if {@code expectedCount} text is found, whithin a {@code timeout} in the output of command {@code command}
     * and the exit status is the specified in {@code exitStatus}.
     * Each negative lookup is followed by a wait of {@code wait} seconds.
     *
     * @param timeout
     * @param wait
     * @param command
     * @param search
     * @param sExitStatus
     * @throws InterruptedException
     */
    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, the command output '(.+?)'( not)? contains '(.*?)'( with exit status '(\\d+)')?$")
    public void assertCommandExistsOnTimeOut(Integer timeout, Integer wait, String command, String contains, String search, String sExitStatus) throws Exception {
        Integer exitStatus = sExitStatus != null ? Integer.valueOf(sExitStatus) : null;
        Boolean found = false;

        command = "set -o pipefail && alias grep='grep --color=never' && " + command;
        for (int i = 0; (i <= timeout); i += wait) {
            if (found) {
                break;
            }
            commonspec.getLogger().debug("Checking output value");
            commonspec.getRemoteSSHConnection().runCommand(command);
            commonspec.setCommandResult(commonspec.getRemoteSSHConnection().getResult());
            try {
                if (exitStatus != null) {
                    assertThat(commonspec.getRemoteSSHConnection().getExitStatus()).isEqualTo(exitStatus);
                }
                if (contains == null || contains.isEmpty()) {
                    assertThat(commonspec.getCommandResult()).as("Contains " + search + ".").contains(search);
                } else {
                    assertThat(commonspec.getCommandResult()).as("doesn't contains " + search + ".").doesNotContain(search);
                }
                found = true;
                timeout = i;
            } catch (AssertionError e) {
                commonspec.getLogger().info("Command output don't found yet after " + i + " seconds");
                Thread.sleep(wait * 1000);
            }
        }

        if (!found) {
            throw new Exception("Command output don't found yet after " + timeout + " seconds");
        }
        commonspec.getLogger().info("Command output found after " + timeout + " seconds");
    }

    /**
     * Checks if {@code expectedCount} text is found, whithin a {@code timeout} in the output of command {@code command}
     * and the exit status is the specified in {@code exitStatus}.
     * Each negative lookup is followed by a wait of {@code wait} seconds.
     *
     * @param timeout
     * @param wait
     * @param command
     * @param search
     * @param sExitStatus
     * @throws InterruptedException
     */
    @Then("^in less than '(\\d+)' seconds, checking each '(\\d+)' seconds, the command output '(.+?)' is( not)? equal to '(.*?)'( with exit status '(\\d+)')?$")
    public void assertCommandIsEqualToOnTimeOut(Integer timeout, Integer wait, String command, String notEqual, String search, String sExitStatus) throws Exception {
        Integer exitStatus = sExitStatus != null ? Integer.valueOf(sExitStatus) : null;
        Boolean found = false;

        command = "set -o pipefail && alias grep='grep --color=never' && " + command;
        for (int i = 0; (i <= timeout); i += wait) {
            if (found) {
                break;
            }
            commonspec.getLogger().debug("Checking output value");
            commonspec.getRemoteSSHConnection().runCommand(command);
            commonspec.setCommandResult(commonspec.getRemoteSSHConnection().getResult());
            try {
                if (exitStatus != null) {
                    assertThat(commonspec.getRemoteSSHConnection().getExitStatus()).isEqualTo(exitStatus);
                }
                if (notEqual == null || notEqual.isEmpty()) {
                    assertThat(commonspec.getCommandResult()).as("Equal to " + search + ".").isEqualTo(search);
                } else {
                    assertThat(commonspec.getCommandResult()).as("Not equal to " + search + ".").isNotEqualTo(search);
                }
                found = true;
                timeout = i;
            } catch (AssertionError e) {
                commonspec.getLogger().info("Command output don't found yet after " + i + " seconds");
                Thread.sleep(wait * 1000);
            }
        }

        if (!found) {
            throw new Exception("Command output don't found yet after " + timeout + " seconds");
        }
        commonspec.getLogger().info("Command output found after " + timeout + " seconds");
    }

    /**
     * Check the existence of a text at a command output
     *
     * @param search
     **/
    @Then("^the command output contains '(.+?)'$")
    public void findShellOutput(String search) throws Exception {
        assertThat(commonspec.getCommandResult()).as("Contains " + search + ".").contains(search);
    }

    /**
     * Check the command output is empty
     *
     **/
    @Then("^the command output is empty$")
    public void emptyShellOutput() {
        assertThat(commonspec.getCommandResult()).as("Command output should be empty").isEmpty();
    }

    /**
     * Check the non existence of a text at a command output
     *
     * @param search
     **/
    @Then("^the command output does not contain '(.+?)'$")
    public void notFindShellOutput(String search) throws Exception {
        assertThat(commonspec.getCommandResult()).as("NotContains " + search + ".").doesNotContain(search);
    }

    /**
     * Check the exitStatus of previous command execution matches the expected one
     *
     * @param expectedExitStatus
     * @deprecated Success exit status is directly checked in the "execute remote command" method, so this is not
     * needed anymore.
     **/
    @Deprecated
    @Then("the command exit status is '{int}'")
    public void checkShellExitStatus(Integer expectedExitStatus) throws Exception {
        assertThat(commonspec.getCommandExitStatus()).as("Is equal to " + expectedExitStatus + ".").isEqualTo(expectedExitStatus);
    }

    /**
     * Execute command in multiple nodes at the same time
     *
     * @param command
     * @param user
     * @param pem
     *
     */
    @Then(value = "^I execute( remotely)? the command '(.*?)' in '(.*?)' nodes of my cluster with user '(.+?)' and pem '(.+?)'$")
    public void runInAllNodes(String remote, String command, String nodes, String user, String pem) throws Exception {
        executeScriptInAllnodes(remote, command, null, nodes, user, pem);
    }

    /**
     * Copy file in multiple nodes at the same time
     *
     * @param localPath
     * @param remotePath
     * @param user
     * @param pem
     *
     */
    @Then(value = "^I copy( remotely file)? '(.*?)' in '(.*?)' path in '(.*?)' nodes of my cluster with user '(.+?)' and pem '(.+?)'$")
    public void copyFileInAllnodes(String remote, String localPath, String remotePath, String nodes, String user, String pem) throws Exception {
        executeScriptInAllnodes(remote, localPath, remotePath, nodes, user, pem);
    }


    private void executeScriptInAllnodes(String remotely, String commandOrLocalPath, String remotePath, String nodes, String user, String pem) throws Exception {
        String baseFolder = remotely != null ? "/tmp" : "target/test-classes";
        String tmpFileBase = baseFolder + "/parallel-script-";
        long timestamp = new Date().getTime();
        String finalFile = tmpFileBase + timestamp + ".sh";
        String pemFile = remotely != null ? baseFolder + "/key" + timestamp + ".pem" : pem;
        String[] aNodes = obtainNodes(nodes);
        StringBuilder finalCmd = new StringBuilder("#!/bin/bash\n");

        commonspec.getLogger().debug("Creating script file:" + finalFile);

        //Prepare script command in parallel
        for (String node : aNodes) {
            if (remotePath != null) {
                finalCmd.append(constructSshParallelCopyFileCmd(commandOrLocalPath, remotePath, user, pemFile, node));
            } else {
                finalCmd.append(constructSshParallelCmd(commandOrLocalPath, user, pemFile, node));
            }
        }

        finalCmd.append("wait");

        //Save the script and send it to the running ssh (dcos-cli) connection and execute it
        BufferedWriter writer = new BufferedWriter(new FileWriter(finalFile));
        writer.write(finalCmd.toString());
        writer.close();

        if (remotely != null) {
            commonspec.getLogger().debug("Uploading script file:" + finalFile);
            copyToRemoteFile(finalFile, baseFolder);
            copyToRemoteFile(pem, pemFile);
        }

        //Now launch it
        commonspec.getLogger().debug("Giving permissions:" + finalFile);
        if (remotely != null) {
            commonspec.getRemoteSSHConnection().runCommand("chmod +x " + finalFile);
            commonspec.getRemoteSSHConnection().runCommand("chmod 400 " + pemFile);
        } else {
            commonspec.runLocalCommand("chmod +x " + finalFile);
        }

        commonspec.getLogger().debug("Executing script file:" + finalFile);
        if (remotely != null) {
            commonspec.runCommandAndGetResult(finalFile);
        } else {
            commonspec.runLocalCommand(finalFile);
        }

        //Delete file
        commonspec.runLocalCommand("rm -Rf " + finalFile);
        if (remotely != null) {
            commonspec.getRemoteSSHConnection().runCommand("rm -Rf " + finalFile);
            commonspec.getRemoteSSHConnection().runCommand("rm -Rf " + pemFile);
        }
    }

    private String[] obtainNodes(String nodes) throws Exception {
        String sNodes;
        if (nodes.startsWith("IP:")) {
            sNodes = nodes.substring(3) + ",";
        } else {
            String[] expectedValues = {"MASTERS", "NODES", "PRIV_NODES", "PUBLIC_NODES", "GOSEC_NODES"};
            if (Arrays.asList(expectedValues).contains(nodes)) {
                DcosSpec dcosSpec = new DcosSpec(commonspec);
                dcosSpec.obtainInfoFromDescriptor(nodes, "obtainedNodes");
                sNodes = ThreadProperty.get("obtainedNodes") + ",";
            } else {
                throw new Exception("Invalid value in nodes variable. It should be one of these values: \"MASTERS\", \"NODES\", \"PRIV_NODES\", \"PUBLIC_NODES\", \"GOSEC_NODES\" or \"IP:<IP_list_separated_by_comma>\"");
            }
        }
        String[] splitted = sNodes.split(",");
        commonspec.getLogger().debug("Nodes found: " + splitted.length + " (" + sNodes + ")");
        return splitted;
    }

    private String constructSshParallelCmd(String command, String user, String pem, String node) {
        return "ssh -o StrictHostKeyChecking=no -o " +
                "UserKnownHostsFile=/dev/null " +
                "-i " + pem + " " +
                user + "@" + node + " '" +
                command + "' &\n";
    }

    private String constructSshParallelCopyFileCmd(String localPath, String remotePath, String user, String pem, String node) {
        return "scp -o StrictHostKeyChecking=no -o " +
                "UserKnownHostsFile=/dev/null " +
                "-rp -i " + pem + " " + localPath + " " +
                user + "@" + node + ":" +
                remotePath + " &\n";
    }

    /**
     * Set SSH connection as active
     *
     * @param sshConnectionId
     **/
    @Then("^I put remote SSH connection with id '(.+?)' as active$")
    public void setActiveSSHConnection(String sshConnectionId) throws Exception {
        RemoteSSHConnectionsUtil.setLastRemoteSSHConnection(RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().get(sshConnectionId));
        RemoteSSHConnectionsUtil.setLastRemoteSSHConnectionId(sshConnectionId);
    }

    /**
     * Close SSH connection
     *
     * @param sshConnectionId
     **/
    @Then("^I close SSH connection with id '(.+?)'$")
    public void closeSSHConnection(String sshConnectionId) throws Exception {
        RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().get(sshConnectionId).getSession().disconnect();
        RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().remove(sshConnectionId);
    }


    /**
     * Open ssh tunnel for connect through calico to any component of a framework
     *
     * @param host
     * @param user
     * @param pemFilePath
     * @param tunnelHostIp
     * @param tunnelHostPort
     * @param tunnelLocalPort
     *
     */
    @When("^I open a ssh tunnel to '(.+?)' with user '(.+?)' using pem file '(.+?)' to host ip '(.+?)' host port '(.+?)' to local port '(.+?)'( and save it with id '(.+?)')?")
    public void openSshTunnelTo(String host, String user, String pemFilePath, String tunnelHostIp, String tunnelHostPort, String tunnelLocalPort, String sshConnectionId) throws Exception {
        String sshConnectionIdAux = sshConnectionId != null ? sshConnectionId : host + "_" + user + "_tunnel";
        if (RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().get(sshConnectionId) != null) {
            RemoteSSHConnectionsUtil.getRemoteSSHConnectionsMap().get(sshConnectionId).getSession().disconnect();
        }
        commonspec.setRemoteSSHConnection(new RemoteSSHConnection(user, null, host, null, pemFilePath), sshConnectionIdAux);
        commonspec.getRemoteSSHConnection().openSshTunnel(host, user, pemFilePath, tunnelHostIp, tunnelHostPort, tunnelLocalPort);
        RemoteSSHConnectionsUtil.setLastTunnelId(sshConnectionIdAux);
    }

    @Deprecated
    public void openSshTunnelTo(String host, String user, String pemFilePath, String tunnelHostIp, String tunnelHostPort, String tunnelLocalPort) throws Exception {
        openSshTunnelTo(host, user, pemFilePath, tunnelHostIp, tunnelHostPort, tunnelLocalPort, "tunnel");
    }

    /**
     * Close ssh tunnel to connect through calico to any component of a framework
     */
    @Then ("^I close the ssh tunnel( with id '(.+?)')?")
    public void closeSshTunnel(String tunnelId) throws Exception {
        String tunnelIdAux = tunnelId != null ? tunnelId : RemoteSSHConnectionsUtil.getLastTunnelId();
        closeSSHConnection(tunnelIdAux);
        if (RemoteSSHConnectionsUtil.getLastTunnelId().equals(tunnelId)) {
            RemoteSSHConnectionsUtil.setLastTunnelId(null);
        }
    }

    @Deprecated
    public void closeSshTunnel() throws Exception {
        closeSSHConnection(RemoteSSHConnectionsUtil.getLastTunnelId());
    }
}
