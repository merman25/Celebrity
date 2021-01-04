package com.merman.celebrity.server.analytics;

public class Browser {
	private BrowserBrand browserBrand;
	private String versionString;
	private String userAgentString;
	
	public Browser() {
		
	}
	
	public Browser(BrowserBrand aBrowserBrand, String aVersionString, String aUserAgentString) {
		setBrowserBrand(aBrowserBrand);
		setVersionString(aVersionString);
		setUserAgentString(aUserAgentString);
	}

	public String toString() {
		if (browserBrand == BrowserBrand.UNKNOWN) {
			return "UNKNOWN_BROWSER";
		}
		else return String.format("%s/%s", browserBrand.displayName, versionString == null ? "UNKNOWN_VERSION" : versionString);
	}

	public BrowserBrand getBrowserBrand() {
		return browserBrand;
	}

	public void setBrowserBrand(BrowserBrand aBrowserBrand) {
		browserBrand = aBrowserBrand;
	}

	public String getVersionString() {
		return versionString;
	}

	public void setVersionString(String aVersionString) {
		versionString = aVersionString;
	}

	public String getUserAgentString() {
		return userAgentString;
	}

	public void setUserAgentString(String aUserAgentString) {
		userAgentString = aUserAgentString;
	}
}
