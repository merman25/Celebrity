/* Util functions used in various places
*/


/* ============= Date/Time formatting
*/

export const formatTime = () => {
	const date = new Date();
	const hours = addLeadingZeroesIfNecessary(date.getHours(), 2);
	const mins = addLeadingZeroesIfNecessary(date.getMinutes(), 2);
	const secs = addLeadingZeroesIfNecessary(date.getSeconds(), 2);
	return `${hours}:${mins}:${secs}`;
}

export const addLeadingZeroesIfNecessary = (number, minDigits) => {
	let formattedNumber;
	if (number === 0) {
		formattedNumber = '0'.repeat(minDigits);
	}
	else if (number < 0) {
		formattedNumber = '-' + addLeadingZeroesIfNecessary(-number, minDigits);
	}
	else {
		const limit 		= 10 ** (minDigits - 1);
		const log10 		= Math.floor(Math.log(number) / Math.log(10));
		const numDigits		= log10 + 1;
		if (numDigits < minDigits) {
			formattedNumber = '0'.repeat(minDigits - numDigits) + number.toString();
		}
		else {
			formattedNumber = number.toString();
		}
	}

	return formattedNumber;
}