package com.easylinking.task;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.easylinking.service.FileService;
import com.easylinking.tools.Utils;

public class FileJob implements Job {

	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		Utils.formatPrint("开始执行定时任务[FileJob]......");

		final FileService service = new FileService();
		
		try {
			//1.dump生产环境数据库
			service.dumpProductDB();
			
			//2.上传数据库文件到华为云(1,2的顺序不能颠倒)
			service.upload();
			
			Utils.formatPrint("执行定时任务[FileJob]完毕......");
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
