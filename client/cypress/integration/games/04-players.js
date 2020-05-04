import * as common from "../common-test-functions";

const index = Cypress.env('PLAYER_INDEX');
const g = 'got-it';
const p = 'pass';
const e = 'end-turn';


export const gameSpec = {
    index: index,
    restoredGame: false,
    playerNames: ['Marvin the Paranoid Android', 'Bender', 'Johnny 5', 'R2D2'],
    gameID: '4795',
    celebrityNames: [
        ['Neil Armstrong', 'Marilyn Monroe', 'John F. Kennedy', 'Audrey Hepburn', 'Paul McCartney', 'Rosa Parks'],
        ['Hippolyta', 'Alexander the Great', 'Hypatia', 'Xerxes', 'Helen of Troy', 'Plato'],
        ['Carl Friedrich Gauss', 'Leonhard Euler', 'John von Neumann', 'Kurt Gödel', 'Gottfried Leibniz', 'Joseph Fourier'],
        ['Emily Blunt', 'Emma Thompson', 'Judi Dench', 'Carey Mulligan', 'Cate Blanchett', 'Rhea Seehorn']
    ],
    turnIndexOffset: 0,

    // 24 total
    turns: [
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
    ],

}

// common.openSiteAndPlayGame(gameSpec);