import * as common from "../celebrity-tests";

const index = Cypress.env('PLAYER_INDEX');
const g = 'got-it';
const p = 'pass';
const e = 'end-turn';

let doneCustomAction = false;

export const gameSpec = {
    description: 'Rejoining a restored game that was in the middle of a round, and also playing with all the menu items',
    index: index,

    restoredGame: true,
    playerNames: ['Marvin the Paranoid Android', 'Bender', 'Johnny 5', 'R2D2'],
    gameID: '1000',
    celebrityNames: [
        ['Neil Armstrong', 'Marilyn Monroe', 'John F. Kennedy', 'Audrey Hepburn', 'Paul McCartney', 'Rosa Parks'],
        ['Hippolyta', 'Alexander the Great', 'Hypatia', 'Xerxes', 'Helen of Troy', 'Plato'],
        ['Carl Friedrich Gauss', 'Leonhard Euler', 'John von Neumann', 'Kurt Gödel', 'Gottfried Leibniz', 'Joseph Fourier'],
        ['Emily Blunt', 'Emma Thompson', 'Judi Dench', 'Carey Mulligan', 'Cate Blanchett', 'Rhea Seehorn']
    ],

    turnIndexOffset: 11,

    // 24 total
    turns: [
        // Round 1
        [g, g, p, g, p, g, g, e], // 5
        [g, g, g, g, e], // 9
        [p, g, g, g, g, g, g, e], // 15
        [g, g, g, p, e], // 18
        [e], //18
        [g, g, g, g, e], // 22
        [g, g], // 24

        // Round 2
        [g, g, g, g, e], // 9
        [g, g, g, g, e], // 22
        [g, g, p, g, p, g, g, e], // 5
        [g, g, e], // 24
        [g, g, g, p, e], // 18
        [e], //18
        [p, g, g, g, g, g, g], // 15

        // Round 3
        [g, g, p, g, p, g, g, e], // 5
        [e], //18
        [g, g, g, g, e], // 9
        [g, g, e], // 24
        [p, g, g, g, g, g, g, e], // 15
        [g, g, g, g, e], // 22
        [g, g, g], // 18
    ],

    // Custom actions to test the right-click menus as part of this test. Could be put into a separate test.
    // Each custom action is a pair of functions. If the first function returns true, the second function is called.
    // Each of them is only executed once, by making use of the flag 'doneCustomAction' declared in this file.
    // Each one is only executed when the turnCounter is 0 (i.e. before the player's first turn, and there is one per player.)
    customActions: [

        // Player 0 (Marvin).
        // Since he's the host, he does all the right-click actions.
        // The others just check that they see the expected results.
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 0;
        },
        function (clientState) {
            doneCustomAction = true;

            // Moves himself to Team1
            common.selectContextMenuItemForPlayer(clientState.playerName, '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam1');

            // give time for it to process that Marvin has changed teams, otherwise it'll try to right-click on Johnny 5's old table cell (at index 1),
            // instead of the new one (at index 0) that appears after the teams table is re-rendered.
            cy.wait(500);

            // Moves Johnny 5 to Team1 and Bender to Team0
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam1');
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam0');

            // give time to re-render the table
            cy.wait(500);

            // Moves R2D2 to Team0
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[2], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam0');

            // Moves Johnny 5 up, moves Bender down
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'moveUp');
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'moveDown');

            if (!clientState.fastMode) {
                cy.get('.playerInTeamTDClass').first().contains(clientState.otherPlayers[2]);
                cy.wait(2000); // give the others time to verify

                cy.contains('.teamlessPlayerLiClass', clientState.otherPlayers[1]);
                cy.wait(4000); // give the others time to verify

                // Johnny 5 refreshes his browser as part of his custom actions, and will need to be put back in the team.
                // This removes his old session from the game
                common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'removePlayerInTeamFromGame')
                cy.get('[id="teamList"]').not(`:contains("${clientState.otherPlayers[1]}")`);
                cy.wait(4000); // give the others time to verify

                // Put teamless Johnny 5, his new session, into Team1 and move him down
                // (actually he's already at the bottom of the list, so this moves him to the top since it wraps around)
                common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.teamlessPlayerLiClass', 'contextMenuForTeamlessPlayer', 'changeToTeam1');
                common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'moveDown');
                cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.otherPlayers[1]}")`);
                cy.wait(4000); // give the others time to verify

                // Make Bender next in Team0
                common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerNextInTeam');
                cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[0]} to start turn`);
                cy.wait(4000); // give the others time to verify

                // Make R2D2 next in Team0
                common.selectContextMenuItemForPlayer(clientState.otherPlayers[2], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerNextInTeam');
                cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`);
            }
            else {
                // give the others time to verify
                cy.wait(4000);
            }

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],

        // Player 1 (Bender).
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 1;
        },
        function (clientState) {
            doneCustomAction = true;

            if (!clientState.fastMode) {
                // Check R2D2 is at [0,0] in the table, after everyone has been initially put back into their teams
                cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });

                // Check Johnny 5 is listed as a teamless player, after he was removed and re-entered the game
                cy.contains('.teamlessPlayerLiClass', clientState.otherPlayers[1], { timeout: 20000 });

                // Check Johnny 5 isn't in the team list
                cy.get('[id="teamList"]').not(`:contains("${clientState.otherPlayers[1]}")`, { timeout: 20000 });

                // Check he's back, at [1,0]
                cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.otherPlayers[1]}")`, { timeout: 20000 });

                // Check it gets set to my (Bender's) turn
                cy.get('[id="gameStatusDiv"]').contains('It\'s your turn!', { timeout: 20000 });

                // Check it gets set to R2D2's turn
                cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 });
            }
            else {
                // Check I (Bender) am at [0,1], which is the end state of all the team manipulations.
                cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="1"]:contains("${clientState.playerName}")`, { timeout: 20000 });
            }

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],

        // Player 2 (Johnny 5).
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 2;
        },
        function (clientState) {
            doneCustomAction = true;

            if (!clientState.fastMode) {
                // Check R2D2 is at [0,0] in the table, after everyone has been initially put back into their teams
                cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });

                // refresh the page, to simulate losing connection, then re-join the game
                cy.clearCookies();
                cy.visit('http://192.168.1.17:8000');
                common.joinGame(clientState.playerName, clientState.gameID, clientState.hostName);

                // Check I'm not in the team list
                cy.get('[id="teamList"]').not(`:contains("${clientState.playerName}")`, { timeout: 20000 });

                // Check I'm back, at [1,0]
                cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.playerName}")`, { timeout: 20000 });

                // Check it gets set to Bender's turn
                cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[1]} to start turn`, { timeout: 20000 });

                // Check it gets set to R2D2's turn
                cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 });
            }
            else {
                // Check Bender is at [0,1], which is the end state of all the team manipulations.
                cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="1"]:contains("${clientState.otherPlayers[1]}")`, { timeout: 20000 });
            }

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],

        // Player 3 (R2D2).
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 3;
        },
        function (clientState) {
            doneCustomAction = true;

            if (!clientState.fastMode) {
                // Check I'm at [0,0] in the table, after everyone has been initially put back into their teams
                cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.playerName}")`, { timeout: 20000 });

                // Check Johnny 5 is listed as a teamless player, after he was removed and re-entered the game
                cy.contains('.teamlessPlayerLiClass', clientState.otherPlayers[2], { timeout: 20000 });

                // Check Johnny 5 isn't in the team list
                cy.get('[id="teamList"]').not(`:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });

                // Check he's back, at [1,0]
                cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });

                // Check it gets set to Bender's turn
                cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[1]} to start turn`, { timeout: 20000 });

                // Check it gets set to my (R2D2's) turn
                cy.get('[id="gameStatusDiv"]').contains('It\'s your turn!', { timeout: 20000 });
            }
            else {
                // Check Bender is at [0,1], which is the end state of all the team manipulations.
                cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="1"]:contains("${clientState.otherPlayers[1]}")`, { timeout: 20000 });
            }

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],
    ],
}
