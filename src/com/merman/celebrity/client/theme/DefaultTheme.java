package com.merman.celebrity.client.theme;

import java.util.Arrays;
import java.util.List;

public class DefaultTheme extends Theme {
	public DefaultTheme() {
		super( IconType.EMOJI, "default" );
	}

	@Override
	public List<String> getIconList() {
		return Arrays.asList(
				"🐨",
				"🐙",
				"🐧",
				"🐢",
				"🐅",
				"🐇",
				"🐊",
				"🐒",
				"🐋",
				"🐘",
				"🐗",
				"🐍",
				"🐈",
				"🐉",
				"🦈",
				"🦍",
				"🦉",
				"🦇",
				"🦋",
				"🦖"
				);
	}

	@Override
	public boolean useTheme() {
		return true;
	}
}
