//package com.mysql.jdbc;

import java.util.*;
import java.io.*;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class DatabaseTest {
	public static void main(String[] args){
		performQuery("insert ignore into user_accounts (username, password) values('fred', 'fred')");
		performQuery("update user_accounts set password = 'bob' where username = 'fred'");
	}

	private static ResultSet performQuery(String query){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			System.out.println("Connected!");
			Statement stmt = conn.createStatement();
			if (stmt.execute(query)) {
				result = stmt.getResultSet();
			}
			conn.close();
		}
		catch(Exception e){
			System.out.println("ERROR!");
			e.printStackTrace();
		}
		finally{
			return result;
		}
	}
}