package com.easylinking.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import com.easylinking.tools.Utils;
import com.huawei.uds.services.UdsConfiguration;
import com.huawei.uds.services.UdsService;
import com.huawei.uds.services.model.LifecycleConfiguration;
import com.huawei.uds.services.model.PutObjectResult;

public class FileService {
	
	public static String productIp = null ;
	public static String productUser = null ;
	public static String productPasswd = null ;
	public static String productDB = null ;
	public static String productDbDir = null ;// 存放备份的远程生产环境数据库文件的目录
	
	public  static String AK = null;
	public  static String SK = null;
	public  static String bucketName = null ;//桶名称
	public  static String server = null ;//华为云服务器地址
	
	public  static String currentFile = null ;
	public  static String currentDir = null ;
	
	public  static boolean error = false ;
	
	static {
		
		System.out.println("\n");
		Utils.formatPrint("开始读取cloud.properties......");
		
		Properties prop = new Properties();
		InputStream in = null;
		try {
			in = FileService.class.getClassLoader().getResourceAsStream("cloud.properties");
			prop.load(in);
			
			productIp = prop.getProperty("productIp").trim();
			productUser = prop.getProperty("productUser").trim();
			productPasswd = prop.getProperty("productPasswd").trim();
			productDB = prop.getProperty("productDB").trim();
			productDbDir = prop.getProperty("productDbDir").trim();
			
			AK = prop.getProperty("AK").trim();
			SK = prop.getProperty("SK").trim();
			bucketName = prop.getProperty("bucketName").trim();
			server = prop.getProperty("server").trim();
			
			Utils.formatPrint("读取cloud.properties结束......");
		} catch (IOException e) {
			Utils.formatPrint("读取cloud.properties出错,退出定时任务!");
			Utils.formatPrint("异常信息:\n" + e.getMessage());
			
			error = true ;
			
		}finally{
			Utils.close(in);
		}
		
	}
	
	//备份远程数据库部分表数据
	public void dumpProductDB() {
		
		if(error)
			return ;

		System.out.println("\n");
		Utils.formatPrint("开始备份生产环境数据库[" + productDB + "]......");

		File dir = new File(productDbDir);

		if (!dir.exists()) {
			dir.mkdirs();
		}

		// 当前备份的数据库文件名称
		currentFile = Utils.getCurrTime() + "_" + productDB + ".sql";

		StringBuilder cmd = new StringBuilder();

		cmd.append("mysqldump").append(" -h").append(productIp);
		cmd.append(" --user=").append(productUser);
		cmd.append(" --password=").append(productPasswd);
		cmd.append(" --result-file=").append(productDbDir + currentFile);
		cmd.append(" --default-character-set=utf8 ").append(productDB);

		try {
			Utils.formatPrint("开始备份生产环境数据库[" + productDB + "]中,请稍等......");
			Process process = Runtime.getRuntime().exec(cmd.toString());

			if (process.waitFor() == 0) {// 0 表示线程正常终止。
				Utils.formatPrint("备份生产环境数据库[" + productDB + "],成功......");
			}else{
				Utils.formatPrint("备份生产环境数据库[" + productDB + "],失败......");
				throw new Exception("备份生产环境数据库[" + productDB + "],失败......");
			}

		} catch (Exception e) {
			Utils.formatPrint("开始备份生产环境数据库[" + productDB + "]时,发生异常!");
			Utils.formatPrint("异常信息如下:" + e.getMessage());
			error = true ;
		}
	}
	
	public void upload() {

		try {
			if(error)
				return ;
			
			System.out.println("\n");
			Utils.formatPrint("开始上传生产环境数据库文件["+currentFile+"]操作成功......");
			
			String filePath = productDbDir + currentFile;

			File file = new File(filePath);

			if (!file.exists()) {
				Utils.formatPrint("需要上传的生产环境备份文件不存在，请检查！");
				Utils.formatPrint("数据库文件路径：" + filePath);

				return;
			}
			
			UdsConfiguration config = new UdsConfiguration();
			config.setEndPoint(server);
			config.setEndpointHttpPort(5080);
			config.setHttpsOnly(false);
			config.setDisableDnsBucket(true);
			
			UdsService service = new UdsService(AK, SK, config);
			
			
			LifecycleConfiguration lifecycleConfig = new LifecycleConfiguration();
			
			
			String prefix = "" ;
			boolean enabled = true ;
			
			LifecycleConfiguration.Rule rule = lifecycleConfig.newRule("mysqlTask2015",prefix, enabled);
			
			//周期配置
			LifecycleConfiguration.Expiration expiration = lifecycleConfig.new Expiration();
			
			//设置有效期
			expiration.setDays(Utils.avlidDays);
			
			rule.setExpiration(expiration );
			lifecycleConfig.addRule(rule);
			
			
			//设置同的生命周期
			
			PutObjectResult result = service.putObject(bucketName,file.getName(), file);
			
			Utils.formatPrint("etag=" + result.getEtag());

			Utils.formatPrint("上传到生产环境文件["+currentFile+"]操作成功......");
			
		} catch (Exception e) {
			Utils.formatPrint("上传生产环境数据库文件["+currentFile+"]发生异常......");
			Utils.formatPrint("异常信息："+e.getMessage());
			
			error = true ;
		}

	}
	
}
