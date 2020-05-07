/* This file is loaded in the head of the HTML doc, because the functions are needed somewhere
 * in the body, so can't be included in the main script file which is loaded at the bottom of the body.
*/

function setCookie(cname, cvalue, exdays) {
	let d = new Date();
	d.setTime(d.getTime() + (exdays * 24 * 60 * 60 * 1000));
	let expires = `expires=${d.toUTCString()}`;
	let cookieString = `${cname}=${cvalue};${expires};path=/`;
	document.cookie = cookieString;
}

function getCookie(cname) {
	let name = `${cname}=`;
	let ca = document.cookie.split(';');
	for (let i = 0; i < ca.length; i++) {
		let c = ca[i];
		while (c.charAt(0) == ' ') {
			c = c.substring(1);
		}
		if (c.indexOf(name) == 0) {
			return c.substring(name.length, c.length);
		}
	}
	return "";
}

function clearCookie(cname) {
	document.cookie = `${cname}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;
}

