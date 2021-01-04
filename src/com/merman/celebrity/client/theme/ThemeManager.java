package com.merman.celebrity.client.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import com.merman.celebrity.game.Game;

public class ThemeManager {
	public static final long THEME_CHECK_INTERVAL_MILLIS = 86_400_000L;
	private static long lastThemeCheckTimeMillis = -1;
	
	private static Theme currentTheme;
	private static WeakHashMap<Game, Theme> mapGamesToThemes		= new WeakHashMap<>();
	private static List<Theme> themeList = new ArrayList<>();
	
	static  {
		registerTheme(new ChristmasTheme());
		Timer timer = new Timer("Theme-Chooser", true);
		timer.schedule(new MyChooseThemeTimerTask(), 0, 60000);
	}
	
	private static class MyChooseThemeTimerTask
	extends TimerTask {

		@Override
		public void run() {
			long currentTimeMillis = System.currentTimeMillis();
			long currentThemePeriod = currentTimeMillis / THEME_CHECK_INTERVAL_MILLIS;
			long periodOfLastCheckTime = lastThemeCheckTimeMillis / THEME_CHECK_INTERVAL_MILLIS;
			
			if (currentThemePeriod != periodOfLastCheckTime) {
				currentTheme = chooseTheme();
			}
		}
	}

	public static synchronized Theme getTheme( Game aGame ) {
		Theme theme = mapGamesToThemes.get(aGame);
		if (theme == null) {
			theme = getCurrentTheme();
			mapGamesToThemes.put(aGame, theme);
		}
		
		return theme;
	}

	public static synchronized Theme getCurrentTheme() {
		if (currentTheme == null) {
			currentTheme = chooseTheme();
		}
		return currentTheme;
	}

	private static Theme chooseTheme() {
		lastThemeCheckTimeMillis = System.currentTimeMillis();
		
		Theme chosenTheme = null;
		for (Theme theme : themeList) {
			if (theme.useTheme()) {
				chosenTheme = theme;
				break;
			}
		}
		
		if (chosenTheme == null) {
			chosenTheme = new DefaultTheme();
		}
		return chosenTheme;
	}
	
	public static void registerTheme(Theme aTheme) {
		themeList.add(aTheme);
	}
}
