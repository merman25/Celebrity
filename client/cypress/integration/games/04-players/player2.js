import * as common from "../../common-test-functions";

describe('Player 2', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        const playerName = 'Bender';
        const hostName = 'Marvin the Paranoid Android';
        const gameID = '4795';
        const otherPlayers = ['Marvin the Paranoid Android'];
        const celebrityNames = ['Hippolyta', 'Alexander the Great', 'Hypatia', 'Xerxes', 'Helen of Troy', 'Archimedes'];
        const iAmHosting = false;

        common.playGame(playerName, iAmHosting, hostName, gameID, otherPlayers, celebrityNames);

    });
});