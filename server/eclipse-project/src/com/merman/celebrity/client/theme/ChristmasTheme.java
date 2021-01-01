package com.merman.celebrity.client.theme;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.merman.celebrity.server.Server;
import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.info.LogInfo;

public class ChristmasTheme
extends Theme {
	private List<String> iconList = new ArrayList<>();
	private boolean themeLoadedOK;
	
	public ChristmasTheme() {
		super(IconType.ICON, "christmas");
		try {
			Files.list( Server.CLIENT_FILE_DIRECTORY.resolve( Paths.get( "icons", "themes", "christmas" ) ) )
			.filter( path -> path.getFileName().toString().toLowerCase().endsWith(".svg" ) )
			.map( path -> Server.CLIENT_FILE_DIRECTORY.relativize( path ).toString() )
			.map( pathString -> pathString.replace( File.separator, "/" ) )
			.forEach( path -> iconList.add( path.toString() ) );
			
			themeLoadedOK = true;
		}
		catch (IOException e) {
			Log.log(LogInfo.class, "Exception when trying to load Christmas icons", e);
		}
	}

	@Override
	public List<String> getIconList() {
		return iconList;
	}

	@Override
	public boolean useTheme() {
		if (themeLoadedOK) {
			Calendar calendar = Calendar.getInstance();
			boolean closeToChristmas =  ( calendar.get(Calendar.MONTH) == 11
											&& calendar.get(Calendar.DAY_OF_MONTH) >= 14 )
										|| ( calendar.get(Calendar.MONTH) == 0
											&& calendar.get(Calendar.DAY_OF_MONTH) <= 6 );

			return closeToChristmas;
		}
		else {
			return false;
		}
	}
}
