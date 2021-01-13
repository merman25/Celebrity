/* ============================
 * Global constants
*/

/* I wanted to just have g, p, and e defined in celebrity-tests.js and export from there,
 * but that didn't work for some reason. The games files just saw them as empty strings.
 * I guess something to do with the circular dependency (celebrity-tests imports from the
 * games files, and vice versa), but we can import 'lets' and functions from celebrity-tests
 * into the games files, so it doesn't make much sense.
*/
export const g = 'got-it';
export const p = 'pass';
export const e = 'end-turn';
