package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;

public class CelebrityMain {

	public static void main(String[] args) throws IOException {
		File gameFile = null;
		if ( args.length > 0 ) {
			gameFile = new File( args[0] );
		}
		new Server(gameFile).start();
		
	}

}
