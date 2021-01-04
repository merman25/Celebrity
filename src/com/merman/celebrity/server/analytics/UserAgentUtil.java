package com.merman.celebrity.server.analytics;

/**
 * Simple parsing of User Agent strings (part of HTTP header) for common browsers as of December 2020.
 * <p>
 * User Agent strings are a mess, with a complicated history, so don't trust the output of this class too far.
 * Just used for logging, to help track browser-specific bugs.
 */
public class UserAgentUtil {
	
	public static Browser getBrowserFromUserAgent(String aUserAgentString) {
		BrowserBrand guessedBrand 	= BrowserBrand.UNKNOWN;
		String       versionString	= null;
		
		String	userAgentString = aUserAgentString.trim();
		
		for ( BrowserBrand browserBrand : BrowserBrand.values() ) {
			String markerString = browserBrand.markerString;
			if (markerString != null) {
				int indexOfMarkerString = userAgentString.indexOf(markerString);
				if (indexOfMarkerString > 0) {
					guessedBrand = browserBrand;
					
					int indexOfEndOfMarkerString = indexOfMarkerString + markerString.length();
					int indexOfEndOfVersionString = userAgentString.indexOf(" ", indexOfEndOfMarkerString);
					if (indexOfEndOfVersionString < 0) {
						indexOfEndOfVersionString = userAgentString.length();
					}
					
					versionString = userAgentString.substring(indexOfEndOfMarkerString, indexOfEndOfVersionString);
					
					break;
				}
			}
		}
		
		Browser browser = new Browser(guessedBrand, versionString, userAgentString);
		return browser;
	}
	
	public static String getOperatingSystemFromUserAgent(String aUserAgentString) {
		String operatingSystem = null;
		String prefixString = "Mozilla/5.0 (";
		if (aUserAgentString.startsWith(prefixString)) {
			int indexOfCommentEnd = aUserAgentString.indexOf(")", prefixString.length());
			if (indexOfCommentEnd > 0) {
				operatingSystem = aUserAgentString.substring(prefixString.length(), indexOfCommentEnd);
			}
		}
		
		return operatingSystem;
	}
}
