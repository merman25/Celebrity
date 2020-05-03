export const restoredGame = false;
export const playerNames = ['Marvin the Paranoid Android', 'Bender', 'Johnny 5', 'R2D2'];
export const gameID = '4795';
export const celebrityNames = [
    ['Neil Armstrong', 'Marilyn Monroe', 'John F. Kennedy', 'Audrey Hepburn', 'Paul McCartney', 'Rosa Parks'],
    ['Hippolyta', 'Alexander the Great', 'Hypatia', 'Xerxes', 'Helen of Troy', 'Plato'],
    ['Carl Friedrich Gauss', 'Leonhard Euler', 'John von Neumann', 'Kurt Gödel', 'Gottfried Leibniz', 'Joseph Fourier'],
    ['Emily Blunt', 'Emma Thompson', 'Judi Dench', 'Carey Mulligan', 'Cate Blanchett', 'Rhea Seehorn']
];

const g = 'got-it';
const p = 'pass';
const e = 'end-turn';

// 24 total
export const turns = [
    [g, g, p, g, p, g, g, e], // 5
    [g, g, g, g, e], // 9
    [p, g, g, g, g, g, g, e], // 15
    [g, g, g, p, e], // 18
    [e], //18
    [g, g, g, g, e], // 22
    [g, g], // 24

    [g, g, g, g, e], // 9
    [g, g, g, g, e], // 22
    [g, g, p, g, p, g, g, e], // 5
    [g, g, e], // 24
    [g, g, g, p, e], // 18
    [e], //18
    [p, g, g, g, g, g, g], // 15

    [g, g, p, g, p, g, g, e], // 5
    [e], //18
    [g, g, g, g, e], // 9
    [g, g, e], // 24
    [p, g, g, g, g, g, g, e], // 15
    [g, g, g, g, e], // 22
    [g, g, g], // 18
];