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
package com.ec2box.manage.db;

import com.ec2box.manage.model.Login;
import com.ec2box.manage.util.DBUtils;
import com.ec2box.manage.util.EncryptionUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * DAO to login administrative users
 */
public class AdminDB {

    /**
     * returns admin login object based on auth token
     *
     * @param authToken auth token string
     */
    public static Login getAdminLogin(String authToken) {
        Login login = null;
        if (authToken != null && !authToken.trim().equals("")) {

            Connection con = null;
            try {
                con = DBUtils.getConn();
                PreparedStatement stmt = con.prepareStatement("select * from  admin where auth_token=?");
                stmt.setString(1, authToken);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {

                    login = new Login();
                    login.setId(rs.getLong("id"));
                    login.setAuthToken(rs.getString("auth_token"));
                    login.setUsername(rs.getString("username"));

                }
                DBUtils.closeRs(rs);
                DBUtils.closeStmt(stmt);

            } catch (Exception e) {
                e.printStackTrace();
            }
            DBUtils.closeConn(con);
        }

        return login;
    }

    /**
     * login user and return auth token if valid login
     *
     * @param login username and password object
     * @return auth token if success
     */
    public static String loginAdmin(Login login) {
        String authToken = null;


        Connection con = null;
        try {
            con = DBUtils.getConn();
            PreparedStatement stmt = con.prepareStatement("select * from  admin where username=? and password=?");
            stmt.setString(1, login.getUsername());
            stmt.setString(2, EncryptionUtil.hash(login.getPassword()));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {

                login.setId(rs.getLong("id"));

                authToken = UUID.randomUUID().toString();
                login.setAuthToken(authToken);

                //set auth token
                updateAdmin(con, login);


            }
            DBUtils.closeRs(rs);
            DBUtils.closeStmt(stmt);

        } catch (Exception e) {
            e.printStackTrace();
        }
        DBUtils.closeConn(con);


        return authToken;

    }


    /**
     * checks to see if user is an admin based on auth token
     *
     * @param authToken auth token string
     */
    public static boolean isAdmin(String authToken) {

        boolean isAdmin = false;

        Connection con = null;
        if (authToken != null && !authToken.trim().equals("")) {

            try {
                con = DBUtils.getConn();
                PreparedStatement stmt = con.prepareStatement("select * from  admin where auth_token=?");
                stmt.setString(1, authToken);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    isAdmin = true;

                }
                DBUtils.closeRs(rs);

                DBUtils.closeStmt(stmt);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        DBUtils.closeConn(con);
        return isAdmin;


    }

    /**
     * updates the admin table based on login id
     *
     * @param con   DB connection
     * @param login username and password object
     */
    private static void updateAdmin(Connection con, Login login) {


        try {
            PreparedStatement stmt = con.prepareStatement("update admin set username=?, password=?, auth_token=? where id=?");
            stmt.setString(1, login.getUsername());
            stmt.setString(2, EncryptionUtil.hash(login.getPassword()));
            stmt.setString(3, login.getAuthToken());
            stmt.setLong(4, login.getId());
            stmt.execute();

            DBUtils.closeStmt(stmt);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    /**
     * updates password for admin using auth token
     */
    public static boolean updatePassword(Login login) {
        boolean success = false;

        Connection con = null;
        try {
            con = DBUtils.getConn();


            PreparedStatement stmt = con.prepareStatement("select * from admin where auth_token like ? and password like ?");
            stmt.setString(1, login.getAuthToken());
            stmt.setString(2, EncryptionUtil.hash(login.getPrevPassword()));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {

                stmt = con.prepareStatement("update admin set password=? where auth_token like ?");
                stmt.setString(1, EncryptionUtil.hash(login.getPassword()));
                stmt.setString(2, login.getAuthToken());
                stmt.execute();
                success = true;
            }

            DBUtils.closeRs(rs);
            DBUtils.closeStmt(stmt);

        } catch (Exception e) {
            e.printStackTrace();
        }
        DBUtils.closeConn(con);
        return success;
    }
}
