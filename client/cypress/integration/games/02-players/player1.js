import * as common from "../../common-test-functions";

describe('Player 1', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        const playerName = 'Marvin the Paranoid Android';
        const gameID = '4795';
        const otherPlayers = ['Bender'];
        const celebrityNames = ['Neil Armstrong', 'Marilyn Monroe', 'John F. Kennedy', 'Audrey Hepburn', 'Paul McCartney', 'Rosa Parks'];

        common.startHostingNewGame(playerName, gameID);
        common.checkTeamlessPlayerList(otherPlayers);
        common.setGameParamsAndAllocateTeams(playerName);
        common.checkTeamList(playerName, otherPlayers);
        common.requestNames();
        common.submitNames(celebrityNames);
        common.startGame();

        cy.get('[id="startTurnButton"]').should('not.be.visible');

        // Alexander the Great, Marilyn Monroe, Audrey Hepburn, Paul McCartney, Rosa Parks, Archimedes, Hypatia, Helen of Troy, Neil Armstrong, Xerxes, Hippolyta, John F. Kennedy

        cy.get('[id="startNextRoundButton"]').click({ timeout: 60000 });
        common.startTurnAndGetAllNames();
        cy.get('[id="startNextRoundButton"]').click();

    });
});