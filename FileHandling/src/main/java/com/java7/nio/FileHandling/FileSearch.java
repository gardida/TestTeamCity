package com.java7.nio.FileHandling;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileSearch {

	

	public void startFileMonitor() {

		
		
		
		
//		try {
//			WatchService watcher = FileSystems.getDefault().newWatchService();
//			Path dir = FileSystems.getDefault().getPath("data/test");
//
//			WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
//					StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
//			
//			
////			handle files currently sitting in directory before polling
////			what happens if file arrives while sorting these files? start poller first
//
//			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
//			    for (Path file: stream) {
//			        System.out.println(file.getFileName());
//			    }
//			} catch (IOException | DirectoryIteratorException x) {
//			    System.err.println(x);
//			}
//			
////			poll for new files that arrive
//			while (!App.shutdown) {
//				System.out.println("searching...");
//				key = watcher.take();
//				for (WatchEvent<?> event : key.pollEvents()) {
//					Path path = (Path) event.context();
//					System.out.println("file name is " + path.toFile().getName());
//					if (path.toFile().getName().equals("shutdown.txt")) {
//						System.out.println("shut down file received...");
////						shutdown = true;
//						break;
//					}
//				}
//				key.reset();
//			}
//
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

}
