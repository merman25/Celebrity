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

/* ============= PRNG
*/

/* You can't set the seed of the Javascript native random number generator.
*  So, for the Cypress tests, we use a seedable one based on part of
*  https://stackoverflow.com/a/47593316
*/

// Returns a function which generates a new hash each time (good for seeding)
function xmur3(str) {
    for(var i = 0, h = 1779033703 ^ str.length; i < str.length; i++)
        h = Math.imul(h ^ str.charCodeAt(i), 3432918353),
        h = h << 13 | h >>> 19;
    return function() {
        h = Math.imul(h ^ h >>> 16, 2246822507);
        h = Math.imul(h ^ h >>> 13, 3266489909);
        return (h ^= h >>> 16) >>> 0;
    }
}

// Takes 4 32-bit seeds and returns a function which generates random numbers
function sfc32(a, b, c, d) {
    return function() {
      a >>>= 0; b >>>= 0; c >>>= 0; d >>>= 0; 
      var t = (a + b) | 0;
      a = b ^ b >>> 9;
      b = c + (c << 3) | 0;
      c = (c << 21 | c >>> 11);
      d = d + 1 | 0;
      t = t + d | 0;
      c = c + t | 0;
      return (t >>> 0) / 4294967296;
    }
}

// Returns a function which returns, on each call, a new number drawn from a uniform distribution between 0 (inclusive) and 1 (exclusive).
// If a string is passed as the stringSeed, it is used to seed the PRNG. If not, a seed based on the current time in milliseconds is used.
export function generateRandomFunction(stringSeed = null) {
	if (! stringSeed) {
		stringSeed = new Date().getTime().toString();
	}
	const seedFn = xmur3(stringSeed);
	const randFn = sfc32(seedFn(), seedFn(), seedFn(), seedFn());

	return randFn;
}
