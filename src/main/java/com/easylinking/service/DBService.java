package com.easylinking.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.easylinking.tools.Utils;

public class DBService {
	public static Set<String> bAssistIdsSet = new HashSet<String>();
	public static Set<String> phones = new HashSet<String>();
	public static Set<String> syncTables = new HashSet<String>();

	public static String parentDir = null;
	public static String dbDir = null ;
	
	public static String remoteIp = null ;
	public static String remoteUser = null;
	public static String remotePasswd = null ;
	public static String remoteDB = null ;
	public static String remoteDbDir = null; // 存放备份的远程数据库文件的目录
	
	public static String currentFile = null ;
	public static String currentDir = null;
	
	public static boolean local_error = false ;
	public static boolean remote_error = false ;

	static {
		preStart();
		readCfg();
	}

	public static void preStart() {
		InputStreamReader read = null;
		try {
			InputStream input = Object.class.getResourceAsStream("/phones.txt");

			if (input != null) {
				read = new InputStreamReader(input, "UTF-8");
				BufferedReader br = new BufferedReader(read);

				String lineTxt = null;

				Utils.formatPrint("读取属性文件[phones.txt]信息!\n");

				while ((lineTxt = br.readLine()) != null) {
					phones.add(lineTxt);
					Utils.formatPrint(lineTxt);
				}
			} else {
				Utils.formatPrint("属性文件[phones.txt]不存在!");
				
				throw new Exception("属性文件[phones.txt]不存在!");
			}
			
			Utils.close(read);
			
			input = Object.class.getResourceAsStream("/syncTables.txt");
			if (input != null) {
				read = new InputStreamReader(input, "UTF-8");
				BufferedReader br = new BufferedReader(read);

				String lineTxt = null;

				Utils.formatPrint("读取属性文件[tables.txt]信息!\n");

				while ((lineTxt = br.readLine()) != null) {
					syncTables.add(lineTxt);
					Utils.formatPrint(lineTxt);
				}
			} 
			
		} catch (Exception e) {
			Utils.formatPrint("读取文件[phones.txt]内容出错,退出定时任务!");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
			
			local_error = true ;
			remote_error =true ;

		} finally {
			Utils.close(read);
		}
		Utils.formatPrint("读取属性文件结束！");
	}
	
	public static void readCfg() {
		if(local_error || remote_error)
			return ;
		
		Properties prop = new Properties();
		InputStream in = null;
		try {
			in = DBService.class.getClassLoader().getResourceAsStream("config.properties");
			prop.load(in);
			
			parentDir = prop.getProperty("parentDir").trim();
			dbDir = prop.getProperty("dbDir").trim();
			
			remoteIp = prop.getProperty("remoteIp").trim();
			remoteUser = prop.getProperty("remoteUser").trim();
			remotePasswd = prop.getProperty("remotePasswd").trim();
			remoteDB = prop.getProperty("remoteDB").trim();
			remoteDbDir = prop.getProperty("remoteDbDir").trim();
			
		} catch (IOException e) {
			Utils.formatPrint("读取config.properties出错,退出定时任务!");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
			
			local_error = true ;
			remote_error = true ;
		}finally{
		    Utils.close(in);
		}
	}

	public void backupLocalDB() {
		
		if(local_error || remote_error)
			return ;
		
		try{
			Utils.formatPrint("开始备份本地数据库["+Utils.dbName+"]......");
			
			DBService.currentDir = Utils.getCurrTime();
	
			Iterator<String> it = phones.iterator();
	
			StringBuilder mobiles = new StringBuilder(256);
			String mobileStr = new String();
	
			while (it.hasNext()) {
				mobiles.append(",'").append((String) it.next()).append("'");
			}
	
			mobileStr = mobiles.substring(1).toString();
	
			StringBuilder sql = new StringBuilder(256);
			sql.append("SELECT ref_business_id,ref_business_origin_id FROM e_link_user  WHERE RegMobile in ( ");
			sql.append(mobileStr);
			sql.append(")");
	
			Connection conn = Utils.getConn();
			Statement st = conn.createStatement();
			ResultSet rs = st.executeQuery(sql.toString());
	
			while (rs.next()) {
				bAssistIdsSet.add(rs.getString(1));
				bAssistIdsSet.add(rs.getString(2));
			}
	
			Utils.close(null, st, rs);
	
			sql = new StringBuilder(512);
	
			sql.append("SELECT  b.id FROM b_assist_user b ");
			sql.append(" WHERE EXISTS (");
			sql.append("    SELECT t2.ref_company_id FROM ( ");
			sql.append("         SELECT a.ref_company_id FROM b_assist_user a WHERE EXISTS  ( ");
			sql.append("             SELECT 1 FROM (");
			sql.append("                 SELECT  ref_business_id  FROM e_link_user  WHERE RegMobile IN (")
					.append(mobileStr).append(") ");
			sql.append("          ) t  WHERE a.id = t.ref_business_id) ");
			sql.append("   ) t2 ");
			sql.append("WHERE b.ref_company_id = t2.ref_company_id) ");
	
			conn = Utils.getConn();
	
			st = conn.createStatement();
			rs = st.executeQuery(sql.toString());
			while (rs.next()) {
				bAssistIdsSet.add(rs.getString(1));
			}
	
			Utils.close(conn, st, rs);
	
			StringBuilder assistIdSB = new StringBuilder();
			it = bAssistIdsSet.iterator();
	
			while (it.hasNext()) {
				assistIdSB.append(",'").append((String) it.next()).append("'");
			}
	
			String bAssistIds = null;
			if(assistIdSB.length()>0){
				bAssistIds = assistIdSB.substring(1);
			}
	
			//1.1备份b_assist_user所有id in bAssistIds的记录
			Utils.execBackup("b_assist_user", "id", bAssistIds);
	
			//1.2 备份b_assist_user_relation所有ref_owner_user_id或ref_target_user_id in bAssistIds的记录 
			Utils.execBackup("b_assist_user_relation","ref_owner_user_id,ref_target_user_id", bAssistIds);
	
			//1.3 "备份e_link_user所有ref_business_id in bAssistIds的记录 并将id汇总成userIds"
			Utils.execBackup("e_link_user", "ref_business_id", bAssistIds);
	
			
			//######################
			sql = new StringBuilder(32);
			sql.append("SELECT DISTINCT ref_company_id  FROM b_assist_user ") ;
			sql.append(" where id in ( "+bAssistIds +" )");
	
			StringBuilder companyIdSB = new StringBuilder(512);
	
			conn = Utils.getConn();
			st = conn.createStatement();
			rs = st.executeQuery(sql.toString());
			while (rs.next()) {
				companyIdSB.append(",'").append(rs.getString(1)).append("'");
			}
	
			Utils.close(conn, st, rs);
	
			String companyIds = null ;
			
			if(companyIdSB.length()>0)
				companyIds = companyIdSB.substring(1);
	
			// 2.1 备份e_link_app_company表中所有ref_company_id in companyIds的记录
			Utils.execBackup("e_link_app_company", "ref_company_id", companyIds);
	
			//2.2 备份e_link_company所有id in companyIds的记录
			Utils.execBackup("e_link_company", "id", companyIds);
	
			//2.3备份e_link_company_shop所有ref_company_id in companyIds的记录并将ref_shop_id汇总成shopIds"
			Utils.execBackup("e_link_company_tag", "ref_company_id", companyIds);
	
			//2.4 备份e_link_company_tag所有ref_company_id in companyIds的记录
			Utils.execBackup("e_link_company_shop", "ref_company_id", companyIds);
	
			//################
			sql = new StringBuilder(512);
			sql.append("select ref_shop_id from e_link_company_shop ");
			sql.append("where ref_company_id in ( ").append(companyIds).append(" )");
	
			conn = Utils.getConn();
			st = conn.createStatement();
			rs = st.executeQuery(sql.toString());
	
			StringBuilder shopIdSB = new StringBuilder();
			if (rs != null) {
				while (rs.next()) {
					shopIdSB.append(",'").append(rs.getString(1)).append("'");
				}
			}
	
			Utils.close(conn, st, rs);
	
			String shopIds = null;
			if(shopIdSB.length() > 0) {
				shopIds = shopIdSB.substring(1).toString();
			}
	
			//3.1备份e_link_file所有ref_shop_id in shopIds的记录
			Utils.execBackup("e_link_file", "ref_shop_id", shopIds);
	
			//3.2备份e_link_file_category所有ref_shop_id in shopIds的记录
			Utils.execBackup("e_link_file_category", "ref_shop_id", shopIds);
	
			//3.3 备份e_link_shop所有id in shopIds的记录
			Utils.execBackup("e_link_shop", "id", shopIds);
	
			//3.4备份e_link_shop_port所有ref_shop_id in shopIds的记录
			Utils.execBackup("e_link_shop_port", "ref_shop_id", shopIds);
	
			//######################
			sql = new StringBuilder(128);
			sql.append("select id from e_link_user where ref_business_id in (").append(bAssistIds).append(")");
	
			conn = Utils.getConn();
	
			st = conn.createStatement();
			rs = st.executeQuery(sql.toString());
	
			StringBuilder userIdSB = new StringBuilder();
			if (rs != null) {
				while (rs.next()) {
					userIdSB.append(",'").append(rs.getString(1)).append("'");
				}
			}
	
			Utils.close(conn, st, rs);
	
			String userIds = null;
			if (userIdSB.length() > 0) {
				userIds = userIdSB.substring(1).toString();
			}
	
			//4.1备份e_link_user_permission所有UserId in userIds的记录
			Utils.execBackup("e_link_user_permission", "UserId", userIds);
	
			//4.2备份e_link_user_profile所有ref_user_id in userIds的记录
			Utils.execBackup("e_link_user_profile", "ref_user_id", userIds);
	
			//#########
			//5.1备份e_link_sequence Name=AndroidVersionID或IOSVersionID的记录
			Utils.execBackup("e_link_sequence", " where Name = 'AndroidVersionID' or Name = 'IOSVersionID'");
			
			Utils.formatPrint("备份本地数据库成功["+Utils.dbName+"]......");
		
        }catch(Exception e){
        	Utils.formatPrint("备份本地数据库异常......");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
			
			local_error = true ;
			remote_error = true ;
		}

	}

	//备份远程数据库部分表数据
	public void backRemoteDB() {
		
		if(local_error || remote_error)
			return ;

		System.out.println("\n");
		Utils.formatPrint("开始备份远程数据库[" + remoteDB + "]......");

		File dir = new File(remoteDbDir);

		if (!dir.exists()) {
			dir.mkdirs();
		}

		currentFile = Utils.getCurrTime()+ "_"+remoteDB+ ".sql";
				
		StringBuilder cmd = new StringBuilder();

		cmd.append("mysqldump").append(" -h").append(remoteIp);
		cmd.append(" --user=").append(remoteUser);
		cmd.append(" --password=").append(remotePasswd);
		cmd.append(" --result-file=").append(remoteDbDir + currentFile);
		cmd.append(" --default-character-set=utf8 ").append(remoteDB);
		
		Iterator<String> it = syncTables.iterator();
	
		StringBuilder tableSB = new StringBuilder(512);
		while(it.hasNext()){
			tableSB.append(" "+it.next());
		}
		
		if(tableSB.length() ==0){
			Utils.formatPrint("没有需要同步的远程数据库表.....");
			return ;
		}
		
		cmd.append(tableSB.toString());
		try {
			Utils.formatPrint("开始备份远程数据库[" + remoteDB + "]中,请稍等......");
			Process process = Runtime.getRuntime().exec(cmd.toString());

			if (process.waitFor() == 0) {// 0 表示线程正常终止。
				Utils.formatPrint("备份远程数据库[" + remoteDB + "],成功......");
			}else{
				Utils.formatPrint("备份远程数据库[" + remoteDB + "],失败......");
				local_error = true ;
				remote_error = true ;
			}

		} catch (Exception e) {
			Utils.formatPrint("备份远程数据库[" + remoteDB + "]时,发生异常!");
			Utils.formatPrint("异常信息如下:" + e.getMessage());

			local_error = true ;
			remote_error = true ;
		}
	}
	
	//恢复本地备份的数据
	public void restoreLocalDB() {
		
		if(local_error || remote_error)
			return ;
		
		InputStreamReader read = null ;
		try {
			System.out.println("\n\n");
			
			Utils.formatPrint("恢复本地备份的数据开始......");

			String localFileDir = parentDir+DBService.currentDir+"/";

			File fileDir = new File(localFileDir);
			if (!fileDir.exists() || !fileDir.isDirectory()) {
				Utils.formatPrint("不存在本地的表数据文件目录......");
				throw new Exception("不存在本地的表数据文件目录:"+localFileDir);
			}

			File[] files = fileDir.listFiles();
			
			String lineTxt = null;
			for (File file : files) {
				
				Utils.formatPrint(file.getName());

				read = new InputStreamReader(new FileInputStream(file), "UTF-8");// 考虑到编码格式
				BufferedReader bufferedReader = new BufferedReader(read);
				
				
				StringBuilder sqlSB = new StringBuilder(512);
				while ((lineTxt = bufferedReader.readLine()) != null) {
					sqlSB.append(lineTxt);
				}
				
				Utils.execInsert(sqlSB.toString());

				Utils.close(read);
			}
			
			Utils.formatPrint("恢复本地备份的数据成功......");
			
		} catch (Exception e) {
			Utils.formatPrint("读取文件内容出错:");
			Utils.formatPrint("异常信息如下:" + e.getMessage());
			
			local_error = true ;
			remote_error = true ;
		}

	}
	
	//恢复远程备份的数据(必须先一恢复本地备份数据文件)
	public void restoreRemoteDB(){
		try{
			if(local_error || remote_error)
				return ;
			
			System.out.println("\n\n");
			Utils.formatPrint("开始恢复远程备份的数据......");
			
			Runtime runtime = Runtime.getRuntime();
			
			String dbFile = remoteDbDir + currentFile ;
			
			if(!new File(dbFile).exists()){
				Utils.formatPrint("需要恢复的数据库文件["+currentFile+"]不存在!");
				
				throw new Exception("需要恢复的数据库文件["+currentFile+"]不存在!");
			}
			
			
			StringBuilder cmd = new StringBuilder();

			cmd.append("mysql").append(" -h").append(Utils.dbIp);
			cmd.append(" -u").append(Utils.dbUser);
			cmd.append(" -p").append(Utils.dbPasswd);
			cmd.append("  ").append(Utils.dbName);
			cmd.append(" < ").append(dbFile);
			
			
		    String[] command = new String[]{"/bin/bash","-c",cmd.toString()};
		    Process process = runtime.exec(command);    
		     
		    // 输出执行结果
		    InputStreamReader in = new InputStreamReader(process.getInputStream());
		    BufferedReader br = new BufferedReader(in);
		    String line = null;
		    while((line = br.readLine()) != null){
		        Utils.formatPrint(line);
		    }
		    br.close();
		    in.close();
		     
		    // 输出错误信息
		    InputStreamReader in2 = new InputStreamReader(process.getErrorStream());
		    BufferedReader br2 = new BufferedReader(in2);
		    String line2 = null;
		    while((line2 = br2.readLine()) != null){
		        Utils.formatPrint("="+line2);
		    }
		    br2.close();
		    in2.close();
		    
		    Utils.formatPrint("恢复远程备份的数据成功......");
		    
		}catch(Exception e){
			Utils.formatPrint("恢复远程备份的数据");
			Utils.formatPrint("异常信息如下:" + e.getMessage());
			
			local_error = true ;
			remote_error = true ;
		}
		
	}

}