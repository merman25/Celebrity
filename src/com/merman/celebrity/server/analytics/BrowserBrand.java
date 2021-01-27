package com.merman.celebrity.server.analytics;

/**
 * Elements of this enum are listed in the order they will be tested for. Changing order would change (and break) the behaviour.
 * <p>
 * This is because browsers have a <strong>lot</strong> of overlap in their User-Agent strings, see e.g.
 * <a href="https://www.w3.org/community/webed/wiki/Optimizing_content_for_different_browsers:_the_RIGHT_way#A_brief_history_of_browser_sniffing">https://www.w3.org/community/webed/wiki/Optimizing_content_for_different_browsers:_the_RIGHT_way#A_brief_history_of_browser_sniffing</a>
 * or <a href="https://www.seanmcp.com/articles/how-to-get-the-browser-version-in-javascript/">https://www.seanmcp.com/articles/how-to-get-the-browser-version-in-javascript/</a>
 */
public enum BrowserBrand {
	FIREFOX("Firefox", "Firefox/"),
	EDGE("Edge", "Edg/"),
	OPERA("Opera", "OPR/"),
	ELECTRON("Electron", "Electron/"),
	CHROME("Chrome", "Chrome/"),
	SAFARI("Safari", "Safari/"),
	IE("Internet Explorer", "rv:"),
	UNKNOWN(null, null);
	
	public final String displayName;
	public final String markerString;
	
	private BrowserBrand(String aDisplayName, String aMarkerString) {
		displayName = aDisplayName;
		markerString = aMarkerString;
	}
}