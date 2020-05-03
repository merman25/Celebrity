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

        const clientState = common.retrievePlayerParameters(index, defs.playerNames, defs.celebrityNames);
        clientState.index = index;
        clientState.gameID = defs.gameID;
        clientState.turnIndexOffset = 0;
        clientState.turns = defs.turns;
        clientState.customActions = defs.customActions;
        clientState.restoredGame = defs.restoredGame;

        common.playGame(clientState);
    });
});