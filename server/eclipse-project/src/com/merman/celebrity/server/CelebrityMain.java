package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CelebrityMain {

	public static void main(String[] args) throws IOException {
		List<File> gameFileList = new ArrayList<>();
		for ( String arg : args ) {
			gameFileList.add(new File(arg));
		}
		new Server(gameFileList).start();
		
	}

}
