package com.easylinking.task;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.easylinking.service.DBService;
import com.easylinking.tools.Utils;

public class DBJob implements Job {

	public void execute(JobExecutionContext arg0) throws JobExecutionException {

		Utils.formatPrint("开始执行定时任务[DBJob]......");

		final DBService service = new DBService();
		
		try {
			// 1.备份本地数据
			service.backupLocalDB();
			
			// 2.备份远程数据
			service.backRemoteDB();

			// 3.还原步骤2备份的数据
			service.restoreRemoteDB();

			// 4.还原步骤1备份的数据(3与4的顺序不能颠倒)
			service.restoreLocalDB();
			
			Utils.formatPrint("执行定时任务[DBJob]完毕......");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
