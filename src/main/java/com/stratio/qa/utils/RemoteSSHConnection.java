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

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


public class RemoteSSHConnection {

    private final Logger logger = LoggerFactory.getLogger(RemoteSSHConnection.class);

    private Session session;

    private String result;

    private int exitStatus;

    private String user;

    /**
     * Default constructor.
     */
    public RemoteSSHConnection(String user, String password, String remoteHost, String pemFile) throws Exception {
        this(user, password, remoteHost, null, pemFile);
    }

    public RemoteSSHConnection(String user, String password, String remoteHost, String remotePort, String pemFile) throws Exception {
        // Create session
        JSch jsch = new JSch();

        if (pemFile != null) {
            // Pass pem file
            jsch.addIdentity(pemFile);
        }

        int sshPort = 22;
        if (remotePort != null) {
            // Set remote port if provided
            sshPort = Integer.parseInt(remotePort);
        }

        Session session = jsch.getSession(user, remoteHost, sshPort);

        // Trust everywhere we go
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey,password");

        // Pass user
        UserInfo ui = new MyUserInfo();
        session.setUserInfo(ui);

        if (password != null) {
            // Pass password if provided
            session.setPassword(password);
        }

        session.connect();

        this.session = session;
        this.user = user;
    }

    public Session getSession() {
        return session;
    }

    public String getUser() {
        return user;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getResult() {
        return result.trim();
    }

    public void setResult(String result) {
        this.result = result;
    }

    public int getExitStatus() {
        return exitStatus;
    }

    public void setExitStatus(int exitStatus) {
        this.exitStatus = exitStatus;
    }

    /**
     * Copy localPath to remotePath using the session created
     *
     * @param localPath
     * @param remotePath
     */
    public void copyTo(String localPath, String remotePath) throws Exception {
        FileInputStream fis = null;
        String rfile = remotePath;
        String localfile = localPath;
        boolean ptimestamp = true;
        boolean isDirectory = false;
        boolean checkAckError = false;

        File local = new File(localfile);
        List<String> files = new ArrayList<String>();

        if (local.isDirectory()) {
            isDirectory = true;
            File[] listFiles = local.listFiles();
            for (File file : listFiles) {
                files.add(file.getAbsolutePath());
            }
        } else {
            files.add(localfile);
        }

        File remote = new File(remotePath);
        String remoteDir = remote.getParent();

        // exec 'scp -t rfile' remotely
        String command;
        if (isDirectory || remote.isDirectory()) {
            command = "scp " + (ptimestamp ? "-p" : "") + " -d -t " + rfile;
        } else {
            command = "scp " + (ptimestamp ? "-p" : "") + " -d -t " + remoteDir;
        }

        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();

        channel.connect();

        if (checkAck(in) != 0) {
            throw new Exception("Error copying file");
        }

        for (String lfile : files) {
            File _lfile = new File(lfile);

            if (ptimestamp) {
                command = "T" + (_lfile.lastModified() / 1000) + " 0";
                // The access time should be sent here,
                // but it is not accessible with JavaAPI ;-<
                command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
                out.write(command.getBytes());
                out.flush();
                if (checkAck(in) != 0) {
                    checkAckError = true;
                    break;
                }
            }

            // send "C0644 filesize filename", where filename should not include '/'
            long filesize = _lfile.length();
            command = "C0644 " + filesize + " ";
            if (lfile.lastIndexOf('/') > 0) {
                command += lfile.substring(lfile.lastIndexOf('/') + 1);
            } else {
                command += lfile;
            }
            command += "\n";
            out.write(command.getBytes());
            out.flush();
            if (checkAck(in) != 0) {
                checkAckError = true;
                break;
            }

            // send a content of lfile
            fis = new FileInputStream(lfile);
            byte[] buf = new byte[1024];
            while (true) {
                int len = fis.read(buf, 0, buf.length);
                if (len <= 0) {
                    break;
                }
                out.write(buf, 0, len);
            }
            fis.close();
            fis = null;
            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
            if (checkAck(in) != 0) {
                checkAckError = true;
                break;
            }
        }
        out.close();

        channel.disconnect();

        if (!isDirectory) {
            command = "mv " + remoteDir + File.separator + local.getName() + " " + rfile;
            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.connect();
            channel.disconnect();
        }

        if (checkAckError) {
            throw new Exception("Error copying file");
        }
    }


    /**
     * Execute the command in the session created
     *
     * @param command command to be run in the remote session
     */
    public void runCommand(String command) throws Exception {
        runCommand(command, -1);
    }

    /**
     * Execute the command in the session created
     *
     * @param command command to be run in the remote session
     * @param timeout max time in seconds that the command is allowed to run
     */
    public void runCommand(String command, int timeout) throws Exception {
        String result = "";
        String extras = "export PYTHONWARNINGS=\"ignore:Unverified HTTPS request\" && ";

        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(extras + command);

        channel.setInputStream(null);
        ((ChannelExec) channel).setErrStream(System.err);

        InputStream in = channel.getInputStream();

        ((ChannelExec) channel).setPty(true);

        channel.connect();

        byte[] tmp = new byte[1024];
        long startTime = System.currentTimeMillis();
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) {
                    break;
                }
                result = result + new String(tmp, 0, i);
            }
            this.result = result;
            this.setResult(result);

            if (channel.isClosed()) {
                if (in.available() > 0) {
                    continue;
                }
                this.exitStatus = channel.getExitStatus();
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ee) {
            }

            if (timeout > 0 && (System.currentTimeMillis() - startTime) > timeout * 1000L) {
                this.setResult("The remote command took too long to finish, the connection will be closed");
                this.setExitStatus(124);
                break;
            }
        }

        channel.disconnect();
    }

    /**
     * Copy remotePath to localPath using the session created
     *
     * @param remotePath
     * @param localPath
     */
    public void copyFrom(String remotePath, String localPath) throws Exception {
        FileOutputStream fos = null;
        String rfile = remotePath;
        String lfile = localPath;

        String prefix = null;

        File local = new File(lfile);
        if (local.isDirectory()) {
            File remote = new File(rfile);
            prefix = lfile + File.separator + remote.getName();
        } else {
            File dir = new File(local.getParent());
            if (dir.isDirectory()) {
                prefix = lfile;
            } else {
                dir.mkdirs();
                prefix = lfile;
            }
        }

        // exec 'scp -f rfile' remotely
        String command = "scp -f " + remotePath;
        Channel channel = this.session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);

        // get I/O streams for remote scp
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();

        channel.connect();

        byte[] buf = new byte[1024];

        // send '\0'
        buf[0] = 0;
        out.write(buf, 0, 1);
        out.flush();

        while (true) {
            int c = checkAck(in);
            if (c != 'C') {
                break;
            }

            // read '0644 '
            in.read(buf, 0, 5);

            long filesize = 0L;
            while (true) {
                if (in.read(buf, 0, 1) < 0) {
                    // error
                    break;
                }
                if (buf[0] == ' ') {
                    break;
                }
                filesize = filesize * 10L + (long) (buf[0] - '0');
            }

            String file = null;
            for (int i = 0;; i++) {
                in.read(buf, i, 1);
                if (buf[i] == (byte) 0x0a) {
                    file = new String(buf, 0, i);
                    break;
                }
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            // read a content of lfile
            fos = new FileOutputStream(prefix);

            int foo;
            while (true) {
                if (buf.length < filesize) {
                    foo = buf.length;
                } else {
                    foo = (int) filesize;
                }
                foo = in.read(buf, 0, foo);
                if (foo < 0) {
                    // error
                    break;
                }
                fos.write(buf, 0, foo);
                filesize -= foo;
                if (filesize == 0L) {
                    break;
                }
            }
            fos.close();
            fos = null;

            if (checkAck(in) != 0) {
                throw new RuntimeException("Read error on inputstream");
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
        }
        channel.disconnect();
    }

    /**
     * Close connection
     */
    public void closeConnection() throws Exception {
        session.disconnect();
    }

    /**
     * UTILS
     */
    public int checkAck(InputStream in) throws IOException {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) {
            return b;
        }
        if (b == -1) {
            return b;
        }

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');
            if (b == 1) { // error
                logger.error("Error checkACK: {}", sb.toString());
            }
            if (b == 2) { // fatal error
                logger.error("Error checkACK: {}", sb.toString());
            }
        }
        return b;
    }

    public void openSshTunnel(String host, String user, String pemFilePath, String tunnelHostIp, String tunnelHostPort, String tunnelLocalPort) throws JSchException {

        final String STRICT_HOST_KEY_CHECKING_KEY = "StrictHostKeyChecking";
        final String STRICT_HOST_KEY_CHECKING_VALUE = "no";

        // Create JSch object and set pem key
        JSch jsch = new JSch();
        JSch.setConfig(STRICT_HOST_KEY_CHECKING_KEY, STRICT_HOST_KEY_CHECKING_VALUE);
        jsch.addIdentity(pemFilePath);

        // Open session
        Session session = jsch.getSession(user, host, 22);
        session.connect();
        logger.info("Connected to {}@{} with pemFile: {}", user, host, pemFilePath);

        // Set port forwarding hop
        logger.info("Attempting to start port forwarding {}:{}:{}", tunnelLocalPort, tunnelHostIp, tunnelHostPort);
        int assignedPort = session.setPortForwardingL(Integer.valueOf(tunnelLocalPort), tunnelHostIp, Integer.valueOf(tunnelHostPort));
        logger.info("Completed port forwarding in local port {}", assignedPort);

        this.setSession(session);

    }

    private static class MyUserInfo implements UserInfo {
        public String getPassword() {
            return "";
        }

        public boolean promptYesNo(String str) {
            return true;
        }

        public String getPassphrase() {
            return null;
        }

        public boolean promptPassphrase(String message) {
            return true;
        }

        public boolean promptPassword(String message) {
            return true;
        }

        public void showMessage(String message) {
        }
    }

}