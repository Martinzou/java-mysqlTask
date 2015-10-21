package com.easylinking.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.huawei.uds.services.UdsConfiguration;
import com.huawei.uds.services.UdsService;
import com.huawei.uds.services.model.S3Object;

public class DownloadFile {
	
	public final static String AK = "CE290C918BA3BD9FBE37" ;
	public final static String SK = "VF/tYs5T6+iiYWn/e33Ym///LucAAAFPi6O9oMDn" ;
	public final static String server = "s3.hwclouds.com" ;
	
	public static void main(String[] args) throws Exception{
		UdsConfiguration config = new UdsConfiguration();
		
		config.setEndPoint(server);
		config.setEndpointHttpPort(5080);
		config.setHttpsOnly(false);
		config.setDisableDnsBucket(true);
		
		UdsService service = new UdsService(AK,SK,config);
		
//		S3Bucket bucket = service.createBucket("");
		
		String fileName = "2015-10-08_14:20:00_elink_beta.sql" ;
		String bucketName = "mysql-bucket" ;
		
		//
		S3Object obj = service.getObject(bucketName,fileName, null);
		
		if(obj != null){
			InputStream inStream = obj.getObjectContent();
			
			File file = new File("/home/zsh/test/"+fileName);
			if(!file.exists()){
				file.createNewFile();
			}
			
			OutputStream outStream = new FileOutputStream("/home/zsh/test/"+fileName);
		
		     byte[] buffer = new byte[65563];
		     
		     int count ;
		     
		     while((count= inStream.read(buffer)) != -1){
		    	 outStream.write(buffer,0,count);
		     }
		     
		     outStream.close();
		}
		
		
		
	}
	
	

}
