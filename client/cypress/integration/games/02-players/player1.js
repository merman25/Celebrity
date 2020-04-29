import * as common from "../../common-test-functions";

describe('Player 1', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        const playerName = 'Marvin the Paranoid Android';
        const gameID = '4795';
        const otherPlayers = ['Bender'];
        const celebrityNames = ['Neil Armstrong', 'Marilyn Monroe', 'John F. Kennedy', 'Audrey Hepburn', 'Paul McCartney', 'Rosa Parks'];
        const iAmHosting = true;

        common.playGame(playerName, iAmHosting, playerName, gameID, otherPlayers, celebrityNames);
    });
});