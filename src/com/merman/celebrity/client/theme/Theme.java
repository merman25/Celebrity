package com.merman.celebrity.client.theme;

import java.util.List;

public abstract class Theme {
	private IconType iconType;
	private String name;
	public Theme(IconType aIconType, String aName) {
		iconType = aIconType;
		name = aName;
	}
	
	public abstract List<String> getIconList();
	public abstract boolean useTheme();

	public IconType getIconType() {
		return iconType;
	}

	public String getName() {
		return name;
	}
}
