package com.java7.nio.FileHandling;

public class ShutDownImpl implements ShutDownMXBean {

	@Override
	public void shutDown() {
		System.out.println("shutting down...");
		App.shutdown = true;
		
		
	}

	
}
