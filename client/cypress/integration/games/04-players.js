import { g, p, e } from "../constants";
const index = Cypress.env('PLAYER_INDEX');


export const gameSpec = {
    description: 'Normal 4-player game with no problems',
    index: index,
    restoredGame: false,
    playerNames: ['Marvin the Paranoid Android', 'Bender', 'Johnny 5', 'R2D2'],
    celebrityNames: [
        ['Neil Armstrong', 'Marilyn Monroe', 'John F. Kennedy', 'Audrey Hepburn', 'Paul McCartney', 'Rosa Parks'],
        ['Hippolyta', 'Alexander the Great', 'Hypatia', 'Xerxes', 'Helen of Troy', 'Plato'],
        ['Carl Friedrich Gauss', 'Leonhard Euler', 'John von Neumann', 'Kurt Godel', 'Gottfried Leibniz', 'Joseph Fourier'],
        ['Emily Blunt', 'Emma Thompson', 'Judi Dench', 'Carey Mulligan', 'Cate Blanchett', 'Rhea Seehorn']
    ],
    turnIndexOffset: 0,

    // 24 total
    preSetTurns: [
        // Round 1
        [g, g, p, g, p, g, g, e], // 5
        [g, g, g, g, e], // 9
        [p, g, g, g, g, g, g, e], // 15
        [g, g, g, p, e], // 18
        [e], //18
        [g, g, g, g, e], // 22
        [g, g], // 24

        // Round 2
        [g, g, g, g, e],
        [g, g, g, g, e],
        [g, g, p, g, p, g, g, e],
        [g, g, e],
        [g, g, g, p, e],
        [e],
        [p, g, g, g, g, g, g],

        // Round 3
        [g, g, p, g, p, g, g, e],
        [e],
        [g, g, g, g, e],
        [g, g, e],
        [p, g, g, g, g, g, g, e],
        [g, g, g, g, e],
        [g, g, g],
    ],

}