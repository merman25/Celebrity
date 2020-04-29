import * as common from "../../common-test-functions";

describe('Player 2', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        const playerName = 'Bender';
        const hostName = 'Marvin the Paranoid Android';
        const gameID = '4795';
        const otherPlayers = ['Marvin the Paranoid Android'];
        const celebrityNames = ['Hippolyta', 'Alexander the Great', 'Hypatia', 'Xerxes', 'Helen of Troy', 'Archimedes'];

        common.joinGame(playerName, gameID, hostName);
        common.checkTeamlessPlayerList(otherPlayers);
        common.checkGameParams();
        common.checkHostControlsAreNotVisible();
        common.checkTeamList(playerName, otherPlayers);

        cy.get('[id="nameListForm"]').should('be.visible');
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 1 player(s)', {timeout: 60000});
        common.submitNames(celebrityNames);

        cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 0 player(s)', {timeout: 60000});
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="startNextRoundButton"]').should('not.be.visible');
        
        common.startTurnAndGetAllNames();
        common.waitForMyTurn(60);
        common.startTurnAndGetAllNames();
    });
});