package com.easylinking.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import com.easylinking.service.DBService;

public class Utils {
	
	public static String dbDriver = null;
	public static String dbUrl = null;
	public static String dbIp = null ;
	public static String dbName = null ;
	public static String dbUser = null;
	public static String dbPasswd = null;
	
	public static String time = null ;
	
	static {
		Properties prop = new Properties();
		InputStream in = null;
		try {
			in = DBService.class.getClassLoader().getResourceAsStream("config.properties");
			prop.load(in);
			Utils.dbDriver = prop.getProperty("dbDriver").trim();
			Utils.dbUrl = prop.getProperty("dbUrl").trim();
			Utils.dbIp = prop.getProperty("dbIp").trim();
			Utils.dbName = prop.getProperty("dbName").trim();
			Utils.dbUser = prop.getProperty("dbUser").trim();
			Utils.dbPasswd = prop.getProperty("dbPasswd").trim();
			
			Utils.time = prop.getProperty("time").trim();
			
		} catch (IOException e) {
			Utils.formatPrint("读取config.properties出错,退出定时任务!");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
			System.exit(1);
		}finally{
		    Utils.close(in);
		}
	}
	
	public static synchronized Connection getConn() {
		Connection conn = null;
		try {
			Class.forName(dbDriver);
			conn = DriverManager.getConnection(dbUrl, dbUser, dbPasswd);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return conn;
	}

	public static String[] getColumns(String tableName) {
		Statement st = null;
		ResultSet rs = null;

		int num = 0;

		Connection conn = null;

		String[] columns = null;
		try {
			conn = getConn();

			st = conn.createStatement();
			rs = st.executeQuery("select * from " + tableName + " limit 1");

			num = rs.getMetaData().getColumnCount();

			columns = new String[num];

			for (int i = 1; i <= num; i++) {
				columns[i - 1] = rs.getMetaData().getColumnName(i);
			}

		} catch (SQLException e) {
			formatPrint("获取表[" + tableName + "]的列名时，抛出异常！");
			formatPrint("异常信息:\n" + e.getMessage());
		}

		close(conn, st, rs);

		return columns;
	}

	public static String middSQL(String[] columns) {
		int num = columns.length;

		StringBuilder sqlSB = new StringBuilder(" ");

		for (int i = 0; i < num; i++) {
			if (i < num - 1)
				sqlSB.append(columns[i] + ",");
			else {
				sqlSB.append(columns[i]);
			}
		}
		return sqlSB.toString();
	}

	public static String getColumnData(String sql) throws Exception {
		StringBuilder columnSB = new StringBuilder(512);
		Connection conn = getConn();
		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery(sql);

		while (rs.next()) {
			columnSB.append(",'").append(rs.getString(1)).append("'");
		}

		close(conn, st, rs);

		return columnSB.substring(1).toString();
	}

	public static void close(Connection conn, Statement st, ResultSet rs) {
		try {
			if (rs != null) {
				rs.close();
			}
			if (st != null) {
				st.close();
			}
			if (conn != null)
				conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static void close(Connection conn){
		try {
			if (conn != null) {
				conn.close();
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void saveSql(String[] columns, String tableName, String sql)
			throws Exception {
		System.out.println("\n\n");
		formatPrint("开始执行[" + tableName + "]表......");

		formatPrint("获取数据库连接......");

		Connection conn = getConn();

		formatPrint("查询表[" + tableName + "]数据");

		Statement st = conn.createStatement();
		ResultSet rs = st.executeQuery(sql);

		if (rs == null) {
			formatPrint("查询表[" + tableName + "]不需要同步数据......");
			return;
		}

		formatPrint("开始执行数据备份操作");

		String dirName = DBService.parentDir+ DBService.currentDir+ "/";

		File dir = new File(dirName);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		String fileName = dirName + tableName + ".sql";

		BufferedWriter out = new BufferedWriter(new FileWriter(fileName));

		int columnCount = columns.length;

		StringBuilder saveSQL = new StringBuilder();
		saveSQL.append("insert into ").append(tableName).append(" (");

		for (int i = 0; i <= columnCount - 1; i++) {
			saveSQL.append(columns[i]);
			if (i < columnCount - 1)
				saveSQL.append(",");
		}
		saveSQL.append(") values ");

		out.write(saveSQL.toString());
		out.newLine();

		String colType = null;

		StringBuilder dataSB = null;
		while (rs.next()) {
			dataSB = new StringBuilder(512);

			dataSB.append("(");
			for (int i = 0; i <= columnCount - 1; i++) {
				colType = rs.getMetaData().getColumnTypeName(i + 1);

				if (colType.toUpperCase().contains("INT")) {
					dataSB.append(rs.getInt(i + 1));
				} else if (rs.getString(i + 1) == null)
					dataSB.append("NULL");
				else {
					dataSB.append("'").append(rs.getString(i + 1)).append("'");
				}

				if (i < columnCount - 1)
					dataSB.append(",");
			}
			dataSB.append(")");

			if (rs.isLast())
				dataSB.append(";");
			else {
				dataSB.append(",");
			}

			out.write(dataSB.toString());
			out.newLine();
		}

		close(conn, st, rs);

		out.close();
		formatPrint("操作结束");
	}

	//执行带参数的查询操作
	public static void execBackup(String tableName, String whereColNames,String dataParams) throws Exception {
		
		if ((dataParams == null) || ("".equals(dataParams))) {
			formatPrint("表[" + tableName + "]的参数dataParams为空！");
			return;
		}

		String[] columns = getColumns(tableName);

		StringBuilder sql = new StringBuilder(256);
		sql.append("select ");
		sql.append(middSQL(columns));
		sql.append(" from ").append(tableName);

		if (!whereColNames.contains(",")) {
			sql.append(" where " + whereColNames + " in (").append(dataParams).append(")");
		} else {
			String[] whereColNameArr = whereColNames.split(",");

			sql.append(" where " + whereColNameArr[0] + " in (").append(dataParams).append(") ");

			for (int i = 1; i < whereColNameArr.length ; i++) {
				sql.append(" or " + whereColNameArr[i] + " in (").append(dataParams).append(")");
			}

		}

		saveSql(columns, tableName, sql.toString());
	}
	
	//执行插入操作
	public static void execInsert(String sql){
		Connection conn = null ;
		Statement st = null ;
		
		try {
			conn = Utils.getConn();
			st = conn.createStatement();
			st.executeUpdate(sql);
		} catch (Exception e) {
			Utils.formatPrint("插入数据失败,SQL:\n"+sql);
			Utils.formatPrint(e.getMessage());
		}finally{
			close(conn, st, null);
		}
		
		
	}

	//执行表查询操作
	public static void execBackup(String tableName,String where) throws Exception {
		String[] columns = getColumns(tableName);

		StringBuilder sql = new StringBuilder(256);
		sql.append("select ");
		sql.append(middSQL(columns));
		sql.append(" from ").append(tableName).append(" ");
		
		if(where != null && !"".equals(where.trim())){
			sql.append(where.trim());
		}

		saveSql(columns, tableName, sql.toString());
	}

	//格式化打印
	public static void formatPrint(String msg) {
		StringBuilder info = new StringBuilder();

		info.append("当前执行[");
		info.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		info.append("]------->");
		info.append(msg);

		System.out.println(info.toString());
	}
	
	public static String getCurrTime(){
		return new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
	}
	
	//关闭资源
	public static void close(InputStreamReader read) {
		try {
			if (read != null)
				read.close();
		} catch (IOException e) {
			Utils.formatPrint("关闭InputStreamReader流出错!");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
		}
	}
	
	public static void close(InputStream input) {
		try {
			if (input != null)
				input.close();
		} catch (IOException e) {
			Utils.formatPrint("关闭InputStream流出错!");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
		}
	}
}