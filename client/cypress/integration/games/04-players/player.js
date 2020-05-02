import * as common from "../../common-test-functions";
import * as defs from "./game-defs";

const index = Cypress.env('PLAYER_INDEX');

describe('Initialisation', () => {
    it('Checks environment variables are set', () => {
        assert.typeOf(index, 'number', 'PLAYER_INDEX should be set to a number');
    });
});

describe(`Player ${index + 1}`, () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        const { playerName, otherPlayers, celebrityNames, iAmHosting, hostName } = common.retrievePlayerParameters(index, defs.playerNames, defs.celebrityNames);

        common.playGame(index, playerName, iAmHosting, hostName, defs.gameID, otherPlayers, celebrityNames, defs.turns);
    });
});