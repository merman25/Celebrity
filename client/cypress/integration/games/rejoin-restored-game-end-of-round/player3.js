import * as common from "../../common-test-functions";
import * as defs   from "./game-defs";

describe('Player 3', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        const index = 2;
        const {playerName, otherPlayers, celebrityNames, iAmHosting, hostName} = common.retrievePlayerParameters(index, defs.playerNames, defs.celebrityNames);

        common.joinRestoredGame(index, playerName, iAmHosting, hostName, defs.gameID, otherPlayers, celebrityNames, defs.turnIndexOffset, defs.turns);
    });
});