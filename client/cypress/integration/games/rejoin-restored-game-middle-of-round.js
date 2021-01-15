import * as common from "../celebrity-tests";
import { g, p, e } from "../constants";

const index = Cypress.env('PLAYER_INDEX');

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
        ['Carl Friedrich Gauss', 'Leonhard Euler', 'John von Neumann', 'Kurt Godel', 'Gottfried Leibniz', 'Joseph Fourier'],
        ['Emily Blunt', 'Emma Thompson', 'Judi Dench', 'Carey Mulligan', 'Cate Blanchett', 'Rhea Seehorn']
    ],

    turnIndexOffset: 11,
    fastModeOverride: true,

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
            // Each time we move a player, we check that they were successfully moved. This doesn't only check the move itself, but
            // also ensures there was time to re-render the DOM - without that, for the next right-click, it may try to click on
            // the player's old table cell (the one that's going to be removed during re-render), instead of the new one.
            cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="2"]:contains("${clientState.playerName}")`);


            // Moves Johnny 5 to Team1 and Bender to Team0
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam1');
            cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="3"]:contains("${clientState.otherPlayers[1]}")`);
            cy.get('[id="teamList"]').click(); // scroll to check visually

            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam0');
            cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.otherPlayers[0]}")`);

            // Moves R2D2 to Team0
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[2], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'changePlayerInTeamToTeam0');
            cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="1"]:contains("${clientState.otherPlayers[2]}")`);

            // Moves Johnny 5 up, moves Bender down
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'moveUp');
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'moveDown');

            cy.get('[id="teamList"]').click(); // scroll to check visually

            cy.get('.playerInTeamTDClass').first().contains(clientState.otherPlayers[2]);
            // the others report that they've verified by writing a file
            cy.readFile(`${common.tempDir}/1.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/1.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/1.3`, { timeout: 10000 });

            cy.get('[id="gameIDDiv"]').click(); // scroll to check visually
            cy.contains('.teamlessPlayerLiClass', clientState.otherPlayers[1]);
            cy.readFile(`${common.tempDir}/2.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/2.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/2.3`, { timeout: 10000 });

            // Johnny 5 refreshes his browser as part of his custom actions, and will need to be put back in the team.
            // This removes his old session from the game
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'removePlayerInTeamFromGame')
            cy.get('[id="teamList"]').not(`:contains("${clientState.otherPlayers[1]}")`);
            cy.readFile(`${common.tempDir}/3.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/3.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/3.3`, { timeout: 10000 });

            // Put teamless Johnny 5, his new session, into Team1 and move him down
            // (actually he's already at the bottom of the list, so this moves him to the top since it wraps around)
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.teamlessPlayerLiClass', 'contextMenuForTeamlessPlayer', 'changeToTeam1');
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'moveDown');
            cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.otherPlayers[1]}")`);
            cy.readFile(`${common.tempDir}/4.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/4.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/4.3`, { timeout: 10000 });

            // Make Bender next in Team0
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerNextInTeam');
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[0]} to start turn`);
            cy.readFile(`${common.tempDir}/5.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/5.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/5.3`, { timeout: 10000 });

            // Make R2D2 next in Team0
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[2], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerNextInTeam');
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`);
            cy.readFile(`${common.tempDir}/6.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/6.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/6.3`, { timeout: 10000 });

            cy.get('[id="teamList"]').click(); // scroll to check visually

            // Make Team 1 go next (clicking on Johnny 5)
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makeTeamGoNext');
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[1]} to start turn`);
            cy.get('[id="gameIDDiv"]').click(); // scroll to check visually
            cy.readFile(`${common.tempDir}/7.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/7.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/7.3`, { timeout: 10000 });

            
            // Make Team 0 go next (clicking on Bender, making R2 the next player)
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makeTeamGoNext');
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`);
            cy.get('[id="gameIDDiv"]').click(); // scroll to check visually
            cy.readFile(`${common.tempDir}/8.1`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/8.2`, { timeout: 10000 });
            cy.readFile(`${common.tempDir}/8.3`, { timeout: 10000 });

            // Make Johnny 5 the host
            cy.on('window:confirm', (alertText) => {
                assert(alertText.startsWith('Are you sure you want to stop hosting'), 'Alert should ask if you\'re sure');
                return true;
              })
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerHost');
            cy.get('[id="gameIDDiv"]').click(); // scroll to check visually
            cy.get('[id="gameParamsDiv"]').contains( `${clientState.otherPlayers[1]} is hosting.`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/9.2`, '0');

            // Johnny 5 makes me the host again
            cy.get('[id="gameIDDiv"]').click(); // scroll to check visually
            cy.get('[id="gameParamsDiv"]').contains( 'You\'re the host.', { timeout: 20000 });

            // Something in the stuff above sets Team1.playerIndex to 1, should be 0. Could be a bug, but can't find it right now.
            // Force Johnny 5 (index 0) to go next in team 1.
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[1], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerNextInTeam');

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],

        // Player 1 (Bender).
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 1;
        },
        function (clientState) {
            doneCustomAction = true;

            // Check R2D2 is at [0,0] in the table, after everyone has been initially put back into their teams
            cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/1.1`, '0');

            // Check Johnny 5 is listed as a teamless player, after he was removed and re-entered the game
            cy.contains('.teamlessPlayerLiClass', clientState.otherPlayers[1], { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/2.1`, '0');

            // Check Johnny 5 isn't in the team list
            cy.get('[id="teamList"]').not(`:contains("${clientState.otherPlayers[1]}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/3.1`, '0');

            // Check he's back, at [1,0]
            cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.otherPlayers[1]}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/4.1`, '0');

            // Check it gets set to my (Bender's) turn
            cy.get('[id="gameStatusDiv"]').contains('It\'s your turn!', { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/5.1`, '0');

            // Check it gets set to R2D2's turn
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/6.1`, '0');

            // Check it gets set to Team1's turn (Johnny 5)
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[1]} to start turn`, { timeout: 20000 } );
            cy.writeFile(`${common.tempDir}/7.1`, '0');

            // Check it gets set back to Team0's turn (R2D2)
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/8.1`, '0');

            // Check Johnny 5 becomes host
            cy.get('[id="gameParamsDiv"]').contains( `${clientState.otherPlayers[1]} is hosting.`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/9.1`, '0');

            // Check Marvin goes back to being host
            cy.get('[id="gameParamsDiv"]').contains( `${clientState.otherPlayers[0]} is hosting.`, { timeout: 20000 });

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],

        // Player 2 (Johnny 5).
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 2;
        },
        function (clientState) {
            doneCustomAction = true;

            // Check R2D2 is at [0,0] in the table, after everyone has been initially put back into their teams
            cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/1.2`, '0');

            // refresh the page, to simulate losing connection, then re-join the game
            cy.clearCookies();
            cy.visit(common.URL);
            common.joinGame(clientState);
            cy.request(`${common.URL}/setTesting`);
            cy.writeFile(`${common.tempDir}/2.2`, '0');

            // Check I'm not in the team list
            cy.get('[id="teamList"]').not(`:contains("${clientState.playerName}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/3.2`, '0');

            // Check I'm back, at [1,0]
            cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.playerName}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/4.2`, '0');

            // Check it gets set to Bender's turn
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[1]} to start turn`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/5.2`, '0');

            // Check it gets set to R2D2's turn
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/6.2`, '0');

            // Check it gets set to Team1's turn (Johnny 5)
            cy.get('[id="gameStatusDiv"]').contains('It\'s your turn!', { timeout: 20000 } );
            cy.writeFile(`${common.tempDir}/7.2`, '0');

            // Check it gets set back to Team0's turn (R2D2)
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/8.2`, '0');

            // Check Johnny 5 becomes host
            cy.get('[id="gameParamsDiv"]').contains( 'You\'re the host.', { timeout: 20000 });
            cy.readFile(`${common.tempDir}/9.1`, { timeout: 20000 });
            cy.readFile(`${common.tempDir}/9.2`, { timeout: 20000 });
            cy.readFile(`${common.tempDir}/9.3`, { timeout: 20000 });

            // Make Marvin the host
            cy.on('window:confirm', (alertText) => {
                assert(alertText.startsWith('Are you sure you want to stop hosting'), 'Alert should ask if you\'re sure');
                return true;
              })
            common.selectContextMenuItemForPlayer(clientState.otherPlayers[0], '.playerInTeamTDClass', 'playerInTeamContextMenu', 'makePlayerHost');
            cy.get('[id="gameParamsDiv"]').contains( `${clientState.otherPlayers[0]} is hosting.`, { timeout: 20000 });

            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],

        // Player 3 (R2D2).
        [function (clientState) {
            return !doneCustomAction && clientState.turnCounter === 0 && clientState.index === 3;
        },
        function (clientState) {
            doneCustomAction = true;

            // Check I'm at [0,0] in the table, after everyone has been initially put back into their teams
            cy.get(`.playerInTeamTDClass[teamindex="0"][playerindex="0"]:contains("${clientState.playerName}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/1.3`, '0');

            // Check Johnny 5 is listed as a teamless player, after he was removed and re-entered the game
            cy.contains('.teamlessPlayerLiClass', clientState.otherPlayers[2], { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/2.3`, '0');

            // Check Johnny 5 isn't in the team list
            cy.get('[id="teamList"]').not(`:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/3.3`, '0');

            // Check he's back, at [1,0]
            cy.get(`.playerInTeamTDClass[teamindex="1"][playerindex="0"]:contains("${clientState.otherPlayers[2]}")`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/4.3`, '0');

            // Check it gets set to Bender's turn
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[1]} to start turn`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/5.3`, '0');

            // Check it gets set to my (R2D2's) turn
            cy.get('[id="gameStatusDiv"]').contains('It\'s your turn!', { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/6.3`, '0');

            // Check it gets set to Team1's turn (Johnny 5)
            cy.get('[id="gameStatusDiv"]').contains(`Waiting for ${clientState.otherPlayers[2]} to start turn`, { timeout: 20000 } );
            cy.writeFile(`${common.tempDir}/7.3`, '0');

            // Check it gets set back to Team0's turn (R2D2)
            cy.get('[id="gameStatusDiv"]').contains('It\'s your turn!', { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/8.3`, '0');

            // Check Johnny 5 becomes host
            cy.get('[id="gameParamsDiv"]').contains( `${clientState.otherPlayers[2]} is hosting.`, { timeout: 20000 });
            cy.writeFile(`${common.tempDir}/9.3`, '0');

            // Check Marvin goes back to being host
            cy.get('[id="gameParamsDiv"]').contains( `${clientState.otherPlayers[0]} is hosting.`, { timeout: 20000 });


            // Put the correct teamIndex and playerIndex values into clientState
            common.checkTeamList(clientState);
        }],
    ],
}
