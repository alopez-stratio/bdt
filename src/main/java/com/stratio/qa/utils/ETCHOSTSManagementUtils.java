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

import com.stratio.qa.specs.CommonG;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ETCHOSTSManagementUtils {

    private final Logger logger = LoggerFactory.getLogger(ETCHOSTSManagementUtils.class);

    private CommonG comm = new CommonG();

    private final String homeHostsFile = "~/.hosts";

    private final String etcHostsFile = "/etc/hosts";

    private final String homeHostsBackupFile = "~/.hosts.bdt";

    private final String etcHostsBackupFile = "/etc/hosts.bdt";

    private final String homeHostsBackupK8sFile = "~/.hosts.bdt.k8s";

    private final String etcHostsBackupK8sFile = "/etc/hosts.bdt.k8s";

    private final String homeHostsLockFile;

    private final String etcHostsLockFile;

    private final int loops;

    private final int wait_time;

    private final boolean libnssHomehostsInstalled;

    public ETCHOSTSManagementUtils() {
        homeHostsLockFile = homeHostsFile + ".lock." + obtainPID();
        etcHostsLockFile = etcHostsFile + ".lock." + obtainPID();
        loops = System.getProperty("LOCK_LOOPS") != null ? Integer.parseInt(System.getProperty("LOCK_LOOPS")) : 3;
        wait_time = System.getProperty("LOCK_WAIT_TIME_MS") != null ? Integer.parseInt(System.getProperty("LOCK_WAIT_TIME_MS")) : 5000; // default: 5 seconds
        libnssHomehostsInstalled = checkIfLibnssHomehostsIsInstalled();
    }

    private boolean checkIfLibnssHomehostsIsInstalled() {
        try {
            comm.runLocalCommand("ldconfig -p | grep libnss_homehosts");
            return comm.getCommandExitStatus() == 0 && comm.getCommandResult().contains("/lib/x86_64-linux-gnu/libnss_homehosts.so.2") && comm.getCommandResult().contains("/lib/x86_64-linux-gnu/libnss_homehosts.so");
        } catch (Exception e) {
            logger.warn("Error checking if libnss_homehosts is installed", e);
            return false;
        }
    }

    public String obtainPID() {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = vmName.substring(0, vmName.indexOf("@"));

        return pid;
    }

    private String obtainSSHConnectionId(String sshConnectionId) {
        String connectionId = "";

        if (sshConnectionId != null) {
            connectionId = sshConnectionId;
        } else {
            connectionId = RemoteSSHConnectionsUtil.getLastRemoteSSHConnectionId();
        }

        return connectionId;
    }

    public void acquireLock(String remote, String sshConnectionId, String ip, String hostname) throws Exception {
        String lockFile = (remote != null || !libnssHomehostsInstalled ? etcHostsLockFile : homeHostsLockFile);
        String backupFile = (remote != null || !libnssHomehostsInstalled ? etcHostsBackupFile : homeHostsBackupFile);
        String file = (remote != null || !libnssHomehostsInstalled ? etcHostsFile : homeHostsFile);
        String sudoOrEmpty = (remote == null && !libnssHomehostsInstalled ? "sudo " : "");
        String createLockCommand = sudoOrEmpty + "touch " + lockFile;
        String checkLockCommand = "if [ ! -f " + backupFile + " ]; then " + createLockCommand + " && echo 0; else if [ -f " + lockFile + " ]; then " + createLockCommand + " && echo 0; else echo 1; fi; fi";
        boolean lockAcquired = false;
        int iterator = 1;

        while (!lockAcquired && iterator <= loops) {
            // Remote system
            if (remote != null) {
                comm.executeCommand(checkLockCommand, sshConnectionId, 0, null);
            // Local system
            } else {
                comm.runLocalCommand(checkLockCommand);
            }

            // Lock acquired
            if (comm.getCommandResult().matches("0")) {
                lockAcquired = true;
                logger.info("Lock ACQUIRED in file: " + file + " with lock file: " + lockFile);
            // Lock not acquired, we have to wait
            } else {
                logger.debug("Not possible to acquire lock over file: " + file + ". Waiting... " + iterator + "/" + loops);
                iterator = iterator + 1;
                Thread.sleep(wait_time);
            }
        }

        // If we have reached this point without having acquired the lock, we have a problem
        assertThat(lockAcquired).as("It has not been possible to acquire lock over file: " + file).isTrue();

        // Add entry in /etc/hosts
        String backupCommand = sudoOrEmpty + "cp " + file + " " + backupFile;
        String checkBackupCommand = "if [ ! -f " + backupFile + " ]; then " + backupCommand  + "; fi";
        String addCommand = "echo \"" + ip + "   " + hostname + "\" | " + sudoOrEmpty + "tee -a " + file;

        // We want to save in remote machines's /etc/hosts
        if (remote != null) {
            comm.executeCommand(checkBackupCommand, sshConnectionId, 0, null);
            comm.executeCommand(addCommand, sshConnectionId, 0, null);
        // We want to save in local system /etc/hosts
        } else {
            comm.runLocalCommand(checkBackupCommand);
            comm.runLocalCommand(addCommand);
        }

    }

    public void addK8sHost(String ip, String hostname) throws Exception {
        String lockFile = (!libnssHomehostsInstalled ? etcHostsLockFile : homeHostsLockFile);
        String backupFile = (!libnssHomehostsInstalled ? etcHostsBackupFile : homeHostsBackupFile);
        String backupK8sFile = (!libnssHomehostsInstalled ? etcHostsBackupK8sFile : homeHostsBackupK8sFile);
        String file = (!libnssHomehostsInstalled ? etcHostsFile : homeHostsFile);
        String sudoOrEmpty = (!libnssHomehostsInstalled ? "sudo " : "");
        String checkLockCommand = "if [ -f " + lockFile + " ]; then echo 0; else echo 1; fi";
        boolean lockAcquired = false;

        comm.runLocalCommand(checkLockCommand);
        if (comm.getCommandResult().matches("0")) {
            lockAcquired = true;
        }

        if (lockAcquired) {
            comm.runLocalCommand(sudoOrEmpty + "cp " + backupFile + " " + backupK8sFile);
            comm.runLocalCommand("echo \"" + ip + "   " + hostname + "\" | " + sudoOrEmpty + "tee -a " + file);
            comm.runLocalCommand("echo \"" + ip + "   " + hostname + "\" | " + sudoOrEmpty + "tee -a " + backupFile);
        } else {
            comm.runLocalCommand("cp " + file + " " + backupK8sFile);
            comm.runLocalCommand("echo \"" + ip + "   " + hostname + "\" | " + sudoOrEmpty + "tee -a " + file);
        }

        logger.debug("Kubernetes hosts added!");
    }

    public void releaseLock(String remote, String sshConnectionId) throws Exception {
        String lockFile = (remote != null || !libnssHomehostsInstalled ? etcHostsLockFile : homeHostsLockFile);
        String backupFile = (remote != null || !libnssHomehostsInstalled ? etcHostsBackupFile : homeHostsBackupFile);
        String file = (remote != null || !libnssHomehostsInstalled ? etcHostsFile : homeHostsFile);
        String sudoOrEmpty = (remote == null && !libnssHomehostsInstalled ? "sudo " : "");
        String removeLockCommand = sudoOrEmpty + "rm " + lockFile;
        String checkLockCommand = "if [ -f " + lockFile + " ]; then " + removeLockCommand + " && echo 0; else echo 1; fi";

        String restoreCommand = sudoOrEmpty + "cp " + backupFile + " " + file + " && " + sudoOrEmpty + "rm " + backupFile;
        String checkRestoreCommand = "if [ -f " + backupFile + " ] && [ -f " + lockFile + " ] ; then " + restoreCommand  + "; fi";

        // We want to restore remote machines's /etc/hosts
        if (remote != null) {
            String connectionId = obtainSSHConnectionId(sshConnectionId);
            logger.debug("Restoring /etc/hosts, if needed, in connection: " + connectionId);
            comm.executeCommand(checkRestoreCommand, sshConnectionId, 0, null);
        // We want to restore local system /etc/hosts
        } else {
            logger.debug("Restoring /etc/hosts, if needed, locally");
            comm.runLocalCommand(checkRestoreCommand);
        }

        // We want to remove lock in remote system
        if (remote != null) {
            String connectionId = obtainSSHConnectionId(sshConnectionId);
            logger.debug("Releasing lock, if needed, in connection: " + connectionId);
            comm.executeCommand(checkLockCommand, sshConnectionId, 0, null);
        // We want to remove lock in local system
        } else {
            logger.debug("Releasing lock, if needed, locally");
            comm.runLocalCommand(checkLockCommand);
        }

        // Lock not acquired
        if (comm.getCommandResult().matches("1")) {
            throw new Exception("File was not locked by this process: " + file);
        }

        logger.info("Lock RELEASED in file: " + file + " with lock file: " + lockFile);
    }

    public void forceReleaseLock(String remote, String sshConnectionId) {
        try {
            if (remote != null) {
                String connectionId = obtainSSHConnectionId(sshConnectionId);
                logger.debug("Checking left behind changes in /etc/hosts in connection: " + connectionId);
            } else {
                logger.debug("Checking left behind changes in /etc/hosts locally");
            }
            releaseLock(remote, sshConnectionId);
        } catch (Exception e) {
            if (remote != null) {
                String connectionId = obtainSSHConnectionId(sshConnectionId);
                logger.debug("Nothing to be cleaned for this execution for connection: " + connectionId);
            } else {
                logger.debug("Nothing to be cleaned for this execution locally");
            }
        }
    }

    public void removeK8sHost() throws Exception {
        String backupK8sFile = (!libnssHomehostsInstalled ? etcHostsBackupK8sFile : homeHostsBackupK8sFile);
        String file = (!libnssHomehostsInstalled ? etcHostsFile : homeHostsFile);
        String sudoOrEmpty = (!libnssHomehostsInstalled ? "sudo " : "");
        if (new File(backupK8sFile).exists()) {
            comm.runLocalCommand(sudoOrEmpty + "cp " + backupK8sFile + " " + file);
            comm.runLocalCommand(sudoOrEmpty + "rm " + backupK8sFile);
            logger.debug("Kubernetes hosts deleted!");
        }
    }
}
