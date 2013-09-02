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
package com.ec2box.manage.model;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Value object that contains a list of user sessions and the date in which the session began
 */
public class UserSchSessions {

    Map<Long, SchSession> schSessionMap = new ConcurrentHashMap<Long, SchSession>();
    Date startTime=new Date();


    public Map<Long, SchSession> getSchSessionMap() {
        return schSessionMap;
    }

    public void setSchSessionMap(Map<Long, SchSession> schSessionMap) {
        this.schSessionMap = schSessionMap;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
}