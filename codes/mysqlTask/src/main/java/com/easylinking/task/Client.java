package com.easylinking.task;

import com.easylinking.tools.Utils;

public class Client {

	public static void main(String[] args) {
		
		String time = Utils.time;
		
		QuartzManager.addJob(DBJob.class.getSimpleName(), DBJob.class.getName(), time);
		QuartzManager.addJob(FileJob.class.getSimpleName(), FileJob.class.getName(), time);
		
		QuartzManager.startJobs();

	}

}
