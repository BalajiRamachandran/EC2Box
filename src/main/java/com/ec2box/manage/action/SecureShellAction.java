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
package com.ec2box.manage.action;

import com.ec2box.manage.db.ScriptDB;
import com.ec2box.manage.util.AdminUtil;
import com.ec2box.manage.util.SessionOutputUtil;
import com.ec2box.manage.db.SystemStatusDB;
import com.ec2box.manage.model.*;
import com.ec2box.manage.util.SSHUtil;
import com.opensymphony.xwork2.ActionSupport;
import net.sf.json.JSONArray;
import net.sf.json.JSONSerializer;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This action will create composite ssh terminals to be used
 */
public class SecureShellAction extends ActionSupport implements ServletResponseAware, ServletRequestAware {

    static Map<Long, SchSession> schSessionMap = new HashMap<Long, SchSession>();
    List<SessionOutput> outputList;
    String command;
    HttpServletResponse servletResponse;
    HttpServletRequest servletRequest;
    Integer keyCode = null;
    List<Long> idList = new ArrayList<Long>();
    List<Long> systemSelectId;
    SystemStatus currentSystemStatus;
    SystemStatus pendingSystemStatus;
    static String passphrase;
    String password;
    Script script = new Script();

    /**
     * Maps key press events to the ascii values
     */
    static Map<Integer, byte[]> keyMap = new HashMap<Integer, byte[]>();

    static {
        //ESC
        keyMap.put(27, new byte[]{(byte) 0x1b});
        //ENTER
        keyMap.put(13, new byte[]{(byte) 0x0d});
        //LEFT
        keyMap.put(37, new byte[]{(byte) 0x1b, (byte) 0x4f, (byte) 0x44});
        //UP
        keyMap.put(38, new byte[]{(byte) 0x1b, (byte) 0x4f, (byte) 0x41});
        //RIGHT
        keyMap.put(39, new byte[]{(byte) 0x1b, (byte) 0x4f, (byte) 0x43});
        //DOWN
        keyMap.put(40, new byte[]{(byte) 0x1b, (byte) 0x4f, (byte) 0x42});
        //BS
        keyMap.put(8, new byte[]{(byte) 0x08});
        //TAB
        keyMap.put(9, new byte[]{(byte) 0x09});
        //CTR
        keyMap.put(17, new byte[]{});
        //CTR-A
        keyMap.put(65, new byte[]{(byte) 0x01});
        //CTR-B
        keyMap.put(66, new byte[]{(byte) 0x02});
        //CTR-C
        keyMap.put(67, new byte[]{(byte) 0x03});
        //CTR-D
        keyMap.put(68, new byte[]{(byte) 0x04});
        //CTR-E
        keyMap.put(69, new byte[]{(byte) 0x05});
        //CTR-F
        keyMap.put(70, new byte[]{(byte) 0x06});
        //CTR-G
        keyMap.put(71, new byte[]{(byte) 0x07});
        //CTR-H
        keyMap.put(72, new byte[]{(byte) 0x08});
        //CTR-I
        keyMap.put(73, new byte[]{(byte) 0x09});
        //CTR-J
        keyMap.put(74, new byte[]{(byte) 0x0A});
        //CTR-K
        keyMap.put(75, new byte[]{(byte) 0x0B});
        //CTR-L
        keyMap.put(76, new byte[]{(byte) 0x0C});
        //CTR-M
        keyMap.put(77, new byte[]{(byte) 0x0D});
        //CTR-N
        keyMap.put(78, new byte[]{(byte) 0x0E});
        //CTR-O
        keyMap.put(79, new byte[]{(byte) 0x0F});
        //CTR-P
        keyMap.put(80, new byte[]{(byte) 0x10});
        //CTR-Q
        keyMap.put(81, new byte[]{(byte) 0x11});
        //CTR-R
        keyMap.put(82, new byte[]{(byte) 0x12});
        //CTR-S
        keyMap.put(83, new byte[]{(byte) 0x13});
        //CTR-T
        keyMap.put(84, new byte[]{(byte) 0x14});
        //CTR-U
        keyMap.put(85, new byte[]{(byte) 0x15});
        //CTR-V
        keyMap.put(86, new byte[]{(byte) 0x16});
        //CTR-W
        keyMap.put(87, new byte[]{(byte) 0x17});
        //CTR-X
        keyMap.put(88, new byte[]{(byte) 0x18});
        //CTR-Y
        keyMap.put(89, new byte[]{(byte) 0x19});
        //CTR-Z
        keyMap.put(90, new byte[]{(byte) 0x1A});
        //CTR-[
        keyMap.put(219, new byte[]{(byte) 0x1B});
        //CTR-]
        keyMap.put(221, new byte[]{(byte) 0x1D});

    }


    /**
     * Runs commands across sessions
     */
    @Action(value = "/manage/runCmd")
    public String runCmd() {
        try {
            //if id then write to single system output buffer
            if (idList != null && idList.size() > 0) {
                for (Long id : idList) {
                    SchSession schSession = schSessionMap.get(id);
                    if (keyCode != null) {
                        if (keyMap.containsKey(keyCode)) {
                            schSession.getCommander().write(keyMap.get(keyCode));
                        }
                    } else {
                        schSession.getCommander().print(command);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * returns terminal output as a json string
     */
    @Action(value = "/manage/getOutputJSON"
    )
    public String getOutputJSON() {
        synchronized (SessionOutputUtil.class) {
            outputList = SessionOutputUtil.getOutput();
            JSONArray json = (JSONArray) JSONSerializer.toJSON(outputList);
            try {
                servletResponse.getOutputStream().write(json.toString().getBytes());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }


    /**
     * creates composite terminals if there are errors or authentication issues.
     */
    @Action(value = "/manage/createTerms",
            results = {
                    @Result(name = "success", location = "/manage/secure_shell.jsp")
            }
    )
    public String createTerms() {


        if (pendingSystemStatus != null && pendingSystemStatus.getId() != null) {

            //get status
            currentSystemStatus = SystemStatusDB.getSystemStatus(pendingSystemStatus.getId());
            //if initial status run script
            if (currentSystemStatus != null
                    && (SystemStatus.INITIAL_STATUS.equals(currentSystemStatus.getStatusCd())
                    || SystemStatus.AUTH_FAIL_STATUS.equals(currentSystemStatus.getStatusCd())
                    || SystemStatus.PUBLIC_KEY_FAIL_STATUS.equals(currentSystemStatus.getStatusCd()))
                    ) {

                //set current session
                currentSystemStatus = SSHUtil.openSSHTermOnSystem(AdminUtil.getAdminId(servletRequest),passphrase, password, currentSystemStatus, schSessionMap);

            }
            if (currentSystemStatus != null
                    && (SystemStatus.AUTH_FAIL_STATUS.equals(currentSystemStatus.getStatusCd())
                    || SystemStatus.PUBLIC_KEY_FAIL_STATUS.equals(currentSystemStatus.getStatusCd()))) {

                pendingSystemStatus = currentSystemStatus;

            } else {


                pendingSystemStatus = SystemStatusDB.getNextPendingSystem();
                //if success loop through systems until finished or need password
                while (pendingSystemStatus != null && currentSystemStatus != null && SystemStatus.SUCCESS_STATUS.equals(currentSystemStatus.getStatusCd())) {
                    currentSystemStatus = SSHUtil.openSSHTermOnSystem(AdminUtil.getAdminId(servletRequest),passphrase, password, pendingSystemStatus, schSessionMap);
                    pendingSystemStatus = SystemStatusDB.getNextPendingSystem();
                }



            }
            passphrase=null;

        }
        if (SystemStatusDB.getNextPendingSystem()== null) {
            this.passphrase=null;

            //run script it exists
            if (script != null && script.getId() != null && script.getId() > 0) {

                script = ScriptDB.getScript(script.getId(),AdminUtil.getAdminId(servletRequest));

                for (SchSession schSession : schSessionMap.values()) {
                    BufferedReader reader = new BufferedReader(new StringReader(script.getScript()));
                    String line;
                    try {

                        while ((line = reader.readLine()) != null) {
                            schSession.getCommander().println(line);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();

                    }
                }

            }
        }


        return SUCCESS;
    }


    @Action(value = "/manage/getNextPendingSystemForTerms",
            results = {
                    @Result(name = "success", location = "/manage/secure_shell.jsp")
            }
    )
    public String getNextPendingSystemForTerms() {
        currentSystemStatus = SystemStatusDB.getSystemStatus(pendingSystemStatus.getId());
        currentSystemStatus.setErrorMsg("Auth fail");
        currentSystemStatus.setStatusCd(SystemStatus.GENERIC_FAIL_STATUS);


        SystemStatusDB.updateSystemStatus(currentSystemStatus);
        pendingSystemStatus = SystemStatusDB.getNextPendingSystem();

        return SUCCESS;
    }

    @Action(value = "/manage/selectSystemsForCompositeTerms",
            results = {
                    @Result(name = "success", location = "/manage/secure_shell.jsp")
            }
    )
    public String selectSystemsForCompositeTerms() {

        //exit any previous terms
        exitTerms();
        if (systemSelectId != null && !systemSelectId.isEmpty()) {


            List<SystemStatus> systemStatusList = SystemStatusDB.setInitialSystemStatus(systemSelectId);
            pendingSystemStatus = SystemStatusDB.getNextPendingSystem();
            //check to see if passphrase has been provided
            //set use provided passphrase
            if (pendingSystemStatus!=null) {
               pendingSystemStatus.setStatusCd(SystemStatus.PUBLIC_KEY_FAIL_STATUS);
            }

        }
        return SUCCESS;
    }




    @Action(value = "/manage/exitTerms",
            results = {
                    @Result(name = "success", location = "/manage/menu.jsp", type = "redirect")

            }
    )
    public String exitTerms() {


        for (SchSession schSession : schSessionMap.values()) {
            schSession.getChannel().disconnect();
            schSession.getSession().disconnect();
            schSession.setChannel(null);
            schSession.setSession(null);
            schSession.setInputToChannel(null);
            schSession.setCommander(null);
            schSession.setOutFromChannel(null);
            schSession = null;
        }
        schSessionMap.clear();


        return SUCCESS;
    }


    public List<SessionOutput> getOutputList() {
        return outputList;
    }

    public void setOutputList(List<SessionOutput> outputList) {
        this.outputList = outputList;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public HttpServletResponse getServletResponse() {
        return servletResponse;
    }

    public void setServletResponse(HttpServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }

    public Integer getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(Integer keyCode) {
        this.keyCode = keyCode;
    }

    public static Map<Long, SchSession> getSchSessionMap() {
        return schSessionMap;
    }

    public static void setSchSessionMap(Map<Long, SchSession> schSessionMap) {
        SecureShellAction.schSessionMap = schSessionMap;
    }

    public List<Long> getIdList() {
        return idList;
    }

    public void setIdList(List<Long> idList) {
        this.idList = idList;
    }

    public List<Long> getSystemSelectId() {
        return systemSelectId;
    }

    public void setSystemSelectId(List<Long> systemSelectId) {
        this.systemSelectId = systemSelectId;
    }

    public SystemStatus getCurrentSystemStatus() {
        return currentSystemStatus;
    }

    public void setCurrentSystemStatus(SystemStatus currentSystemStatus) {
        this.currentSystemStatus = currentSystemStatus;
    }

    public SystemStatus getPendingSystemStatus() {
        return pendingSystemStatus;
    }

    public void setPendingSystemStatus(SystemStatus pendingSystemStatus) {
        this.pendingSystemStatus = pendingSystemStatus;
    }

    public Script getScript() {
        return script;
    }

    public void setScript(Script script) {
        this.script = script;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    public void setServletRequest(HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}


