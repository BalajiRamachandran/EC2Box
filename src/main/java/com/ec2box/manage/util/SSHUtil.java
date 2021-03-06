/**
 * Copyright 2013 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ec2box.manage.util;

import com.ec2box.common.util.AppConfig;
import com.ec2box.manage.db.SystemDB;
import com.ec2box.manage.model.*;
import com.ec2box.manage.task.SecureShellTask;
import com.jcraft.jsch.*;
import com.ec2box.manage.db.EC2KeyDB;
import com.ec2box.manage.db.SystemStatusDB;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.Map;

/**
 * SSH utility class used to create public/private key for system and distribute authorized key files
 */
public class SSHUtil {

    //system path to public/private key
    public static String KEY_PATH = DBUtils.class.getClassLoader().getResource("ec2db").getPath();

    public static final int SESSION_TIMEOUT = 60000;
    public static final int CHANNEL_TIMEOUT = 60000;


    public static final int SERVER_ALIVE_INTERVAL = StringUtils.isNumeric(AppConfig.getProperty("serverAliveInterval")) ? Integer.parseInt(AppConfig.getProperty("serverAliveInterval")) * 1000 : 60 * 1000;

    /**
     * distributes uploaded item to system defined
     *
     * @param hostSystem object contains host system information
     * @param session          an established SSH session
     * @param source           source file
     * @param destination      destination file
     * @return status uploaded file
     */
    public static HostSystem pushUpload(HostSystem hostSystem, Session session, String source, String destination) {


        hostSystem.setStatusCd(HostSystem.SUCCESS_STATUS);
        Channel channel = null;
        ChannelSftp c = null;

        try {


            channel = session.openChannel("sftp");
            channel.setInputStream(System.in);
            channel.setOutputStream(System.out);
            channel.connect(CHANNEL_TIMEOUT);

            c = (ChannelSftp) channel;
            destination = destination.replaceAll("~\\/|~", "");


            //get file input stream
            FileInputStream file = new FileInputStream(source);
            c.put(file, destination);


        } catch (Exception e) {
            hostSystem.setErrorMsg(e.getMessage());
            hostSystem.setStatusCd(HostSystem.GENERIC_FAIL_STATUS);
        }
        //exit
        if (c != null) {
            c.exit();
        }
        //disconnect
        if (channel != null) {
            channel.disconnect();
        }

        return hostSystem;


    }


    /**
     * return the next instance id based on ids defined in the session map
     *
     * @param sessionId      session id
     * @param userSessionMap user session map
     * @return
     */
    private static int getNextInstanceId(Long sessionId, Map<Long, UserSchSessions> userSessionMap ){

        Integer instanceId=1;
        if(userSessionMap.get(sessionId)!=null){

            for(Integer id :userSessionMap.get(sessionId).getSchSessionMap().keySet()) {
                if (!id.equals(instanceId) ) {

                    if(userSessionMap.get(sessionId).getSchSessionMap().get(instanceId) == null) {
                        return instanceId;
                    }
                }
                instanceId = instanceId + 1;
            }
        }
        return instanceId;

    }


    /**
     * open new ssh session on host system
     *
     * @param passphrase     key passphrase for instance
     * @param password       password for instance
     * @param userId         user id
     * @param sessionId      session id
     * @param hostSystem     host system
     * @param userSessionMap user session map
     * @return status of systems
     */
    public static HostSystem openSSHTermOnSystem(String passphrase, String password, Long userId, Long sessionId, HostSystem hostSystem, Map<Long, UserSchSessions> userSessionMap) {

        JSch jsch = new JSch();

        int instanceId = getNextInstanceId(sessionId,userSessionMap);
        hostSystem.setStatusCd(HostSystem.SUCCESS_STATUS);
        hostSystem.setInstanceId(instanceId);


        SchSession schSession = null;

        try {
           EC2Key ec2Key = EC2KeyDB.getEC2Key(hostSystem.getKeyId());
            //add private key
            if(ec2Key!=null && ec2Key.getId()!=null){
                if(passphrase!=null && !passphrase.trim().equals("")){
                    jsch.addIdentity(ec2Key.getId().toString(), ec2Key.getPrivateKey().trim().getBytes(), null , passphrase.getBytes());
                }else{
                    jsch.addIdentity(ec2Key.getId().toString(), ec2Key.getPrivateKey().trim().getBytes(), null , null);
                }
            }

            //create session
            Session session = jsch.getSession(hostSystem.getUser(), hostSystem.getHost(), hostSystem.getPort());

            //set password if it exists
            if (password != null && !password.trim().equals("")) {
                session.setPassword(password);
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.setServerAliveInterval(SERVER_ALIVE_INTERVAL);
            session.connect(SESSION_TIMEOUT);
            Channel channel = session.openChannel("shell");
            if ("true".equals(AppConfig.getProperty("agentForwarding"))) {
                ((ChannelShell) channel).setAgentForwarding(true);
            }
            ((ChannelShell) channel).setPtyType("xterm");

            InputStream outFromChannel = channel.getInputStream();


            //new session output
            //new session output
            SessionOutput sessionOutput = new SessionOutput(sessionId, hostSystem);


            Runnable run = new SecureShellTask(sessionOutput, outFromChannel);
            Thread thread = new Thread(run);
            thread.start();


            OutputStream inputToChannel = channel.getOutputStream();
            PrintStream commander = new PrintStream(inputToChannel, true);


            channel.connect();

            schSession = new SchSession();
            schSession.setUserId(userId);
            schSession.setSession(session);
            schSession.setChannel(channel);
            schSession.setCommander(commander);
            schSession.setInputToChannel(inputToChannel);
            schSession.setOutFromChannel(outFromChannel);
            schSession.setHostSystem(hostSystem);



        } catch (Exception e) {
            hostSystem.setErrorMsg(e.getMessage());
            if (e.getMessage().toLowerCase().contains("userauth fail")) {
                hostSystem.setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
            } else if (e.getMessage().toLowerCase().contains("auth fail") || e.getMessage().toLowerCase().contains("auth cancel")) {
                hostSystem.setStatusCd(HostSystem.AUTH_FAIL_STATUS);
            } else if (e.getMessage().toLowerCase().contains("unknownhostexception")){
                hostSystem.setErrorMsg("DNS Lookup Failed");
                hostSystem.setStatusCd(HostSystem.HOST_FAIL_STATUS);
            } else {
                hostSystem.setStatusCd(HostSystem.GENERIC_FAIL_STATUS);
            }
        }


        //add session to map
        if (hostSystem.getStatusCd().equals(HostSystem.SUCCESS_STATUS)) {
            //get the server maps for user
            UserSchSessions userSchSessions = userSessionMap.get(sessionId);

            //if no user session create a new one
            if (userSchSessions == null) {
                userSchSessions = new UserSchSessions();
            }
            Map<Integer, SchSession> schSessionMap = userSchSessions.getSchSessionMap();

            //add server information
            schSessionMap.put(instanceId, schSession);
            userSchSessions.setSchSessionMap(schSessionMap);
            //add back to map
            userSessionMap.put(sessionId, userSchSessions);
        }

        SystemStatusDB.updateSystemStatus(hostSystem, userId);
        SystemDB.updateSystem(hostSystem);

        return hostSystem;
    }


}
