package com.easylinking.task;

public class MongoDBTest {

	public static void main(String[] args) {
		MongoDBTest.execShell();
	}
	
	public static void execShell(){
		String command = "/usr/local/mongodb/bin/mongodump -h 127.0.0.1 --port 27017 -d mydb -o /Users/zsh/test/";
		String[] cmd = {"/bin/sh", "-c", command};		
		
		try{
			Runtime rt = Runtime.getRuntime();
			Process process = rt.exec(cmd);
			
			// 0:正常完成
			System.out.println(process.waitFor());
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		
	}

}
