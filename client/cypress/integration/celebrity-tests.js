import { DOMSpecs } from "./dom-specs";
import * as spec4Players from "./games/04-players"
import * as specRestoredMiddle from "./games/rejoin-restored-game-middle-of-round"
import * as specRestoredEnd from "./games/rejoin-restored-game-end-of-round"

let allCelebNames = null;
let fastMode = false;
let includeRestoredGames = false;
export let URL = 'http://localhost:8000';
const testTempFile = 'temp_test_data.txt';
let createdTestTempFile = false;

if (Cypress.env('FAST_MODE')) {
    fastMode = true;
}
if (Cypress.env('INC_RESTORED')) {
    includeRestoredGames = true;
}
const envURL = Cypress.env('URL');
if (envURL
    && envURL !== '') {
    URL = envURL;
}
const OS = Cypress.env('OS');

describe('Initialisation', () => {
    it('Checks mandatory environment variables are set', () => {
        assert.typeOf(Cypress.env('PLAYER_INDEX'), 'number', 'PLAYER_INDEX should be set to a number');
        assert(OS === 'linux' || OS === 'win', 'OS should be set to \'linux\' or \'win\'');
    });
});

const gameSpecs = [spec4Players.gameSpec];
if (includeRestoredGames) {
  gameSpecs.push(specRestoredMiddle.gameSpec);
  gameSpecs.push(specRestoredEnd.gameSpec);
}

for (let i = 0; i < gameSpecs.length; i++) {
    const gameSpec = gameSpecs[i];
    describe(`Player ${gameSpec.index + 1}`, () => {
        it(`Plays spec ${i}: ${gameSpec.description}`, () => {
            cy.visit(URL);

            if (gameSpec.index !== 0) {
                // Since the player at index 0 is hard-coded to be the host, make sure they have time to join the game first.
                cy.wait(10000);
            }

            const index = gameSpec.index;
            const playerName = gameSpec.playerNames[index];
            const clientState = {
                index: index,
                hostName: gameSpec.playerNames[0],
                playerName: playerName,
                otherPlayers: gameSpec.playerNames.filter(name => name !== playerName),
                celebrityNames: gameSpec.celebrityNames[index],
                iAmHosting: index === 0,
                gameID: gameSpec.gameID,
                turnIndexOffset: gameSpec.turnIndexOffset,
                turns: gameSpec.turns,
                restoredGame: gameSpec.restoredGame,
                namesSeen: [],
                fastMode: fastMode
            }
            if (gameSpec.customActions)
                clientState.customActions = gameSpec.customActions;


            allCelebNames = gameSpec.celebrityNames.reduce((flattenedArr, celebNameArr) => flattenedArr.concat(celebNameArr), []);

            playGame(clientState);

            if (createdTestTempFile
                && testTempFile
                && testTempFile !== '') {
                if (OS === 'linux') {
                    cy.exec(`rm ${testTempFile}`);
                }
                else if (OS === 'win') {
                    // file deletion fails on windows for some reason
                    // cy.exec(`del ${testTempFile}`);
                }
            }
        });
    });
}

// Play the game through to the end
export function playGame(clientState) {
    if (!clientState.iAmHosting
        || clientState.restoredGame) {
        joinGame(clientState.playerName, clientState.gameID, clientState.hostName);
        createdTestTempFile = false;
    }
    else {
        startHostingNewGame(clientState.playerName, clientState.gameID);
        createdTestTempFile = true;
    }
    checkDOMContent(DOMSpecs, clientState);

    // Check I and the other players are listed, ready to be put into teams
    cy.contains('.teamlessPlayerLiClass', clientState.playerName, { timeout: 20000 });
    checkTeamlessPlayerList(clientState.otherPlayers);

    // Set the game parameters (num rounds, num names per player, etc)
    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        // Give everyone time to check the DOM content before it changes
        cy.wait(2000);
        setGameParams(clientState.playerName);
    }

    checkDOMContent(DOMSpecs, clientState);
    if (!clientState.restoredGame
        && !fastMode) {
        cy.get('[id="teamList"]')
            .then(elements => {
                const teamList = elements[0];
                expect(teamList.innerText.trim(), 'Team list should be empty').to.equal('');
            });
    }

    if (!fastMode) {
        // Give everyone time to check the DOM content before it changes
        cy.wait(2000);
    }

    if (clientState.iAmHosting) {
        if (!clientState.restoredGame) {
            allocateTeams();
        }
        else {
            // Put the players back into teams using the menu items
            const allPlayers = [clientState.playerName, ...clientState.otherPlayers];

            for (let i = 0; i < allPlayers.length; i++) {
                const player = allPlayers[i];
                selectContextMenuItemForPlayer(player, '.teamlessPlayerLiClass', 'contextMenuForTeamlessPlayer', `changeToTeam${i % 2}`);
                cy.get('[id="teamList"]').contains(player);
            }
        }
    }
    checkDOMContent(DOMSpecs, clientState);

    // Check all players are in teams, and find out my own team index and player index
    checkTeamList(clientState);

    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        requestNames();
    }

    if (!fastMode) {
        cy.get('[id="startGameButton"]').should('not.be.visible');
    }

    if (!clientState.restoredGame) {
        submitNames(clientState.celebrityNames);
    }

    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        startGame();
    }

    // The turnCounter keeps track of the number of times we've taken a turn, which tells us where
    // in the turn list to look for the next turn.
    clientState.turnCounter = 0;
    waitForWakeUpTrigger(clientState);
}

// Wait until a hidden element in the DOM tells the testing bot it should do something, then do it.
// Same method is called recursively until the end of the game.
function waitForWakeUpTrigger(clientState) {
    cy.get('.testTriggerClass', { timeout: 120000 })
        .then(elements => {
            const triggerElement = elements[0];

            checkDOMContent(DOMSpecs, clientState);

            // If custom actions need to be taken, take them.
            let tookAction = false;
            if (clientState.customActions) {
                for (const [predicate, action] of clientState.customActions) {
                    if (predicate(clientState)) {
                        tookAction = true;
                        action(clientState);
                    }
                }
            }

            if (tookAction) {
                waitForWakeUpTrigger(clientState);
            }
            else if (triggerElement.innerText === 'bot-game-over') {
                // Finished game, stop here
                if (!fastMode) {
                    checkFinalScoreForRound(clientState);
                }
                console.log('Finished!!');
            }
            else if (triggerElement.innerText === 'bot-start-turn') {
                // It's my turn, get the names I'm supposed to get on this turn
                cy.get('[id="startTurnButton"]').click();
                getNames(clientState);

                // WARNING: if you do anything else after the call to waitForWakeUpTrigger, don't forget
                // that the turnCounter value is now wrong. And you can't increment it back, since that code
                // will be executed before the cy.get() in the new call to waitForWakeUpTrigger.
                clientState.turnCounter = clientState.turnCounter + 1;
                waitForWakeUpTrigger(clientState);
            }
            else if (triggerElement.innerText === 'bot-ready-to-start-next-round') {
                // Ready to start a new round.
                // Only the host needs to click something now, but all players will see the same trigger text.

                if (!fastMode) {
                    checkFinalScoreForRound(clientState);
                }

                if (clientState.iAmHosting) {
                    // Host clicks the start next round button
                    if (!fastMode) {
                        cy.wait(5000); // Give player who finished the round time to check the scores div
                    }
                    cy.get('[id="startNextRoundButton"]').click();
                }
                else {
                    // Non-host just waits until the trigger text isn't there any more, to avoid endlessly re-entering this method
                    cy.get('.testTriggerClass').not(':contains("bot-ready-to-start-next-round")', { timeout: 10000 });
                }
                waitForWakeUpTrigger(clientState);
            }
        });
}

// Get the names I'm supposed to get on this turn
function getNames(clientState) {
    const turnCounter = clientState.turnCounter;
    const numPlayers = clientState.otherPlayers.length + 1;
    const teamInfoObject = clientState;
    const turnIndexOffset = clientState.turnIndexOffset;
    const turns = clientState.turns;
    const namesSeen = clientState.namesSeen;

    cy.scrollTo(0, 0); // Looks nicer when watching (only useful if we use {force: true} when clicking the buttons)
    // cy.wait(5000); // so I can follow it when watching

    // 'turns' is an array containing all turns taken by all players. Calculate the index we want.
    const numTeams = 2;
    const turnIndex = turnIndexOffset + turnCounter * numPlayers + numTeams * teamInfoObject.playerIndex + teamInfoObject.teamIndex;
    console.log(`turnCounter ${turnCounter}, numPlayers ${numPlayers}, teamIndex ${teamInfoObject.teamIndex}, playerIndex ${teamInfoObject.playerIndex}, turnIndexOffset ${turnIndexOffset} ==> turnIndex ${turnIndex}`);

    const turnToTake = turns[turnIndex];

    let options = {};

    // {force: true} disables scrolling, which is nice for videos but not
    // what we want in a real test. When doing {force: true}, we need the
    // should('be.visible') to make sure we at least wait for the button to appear.

    // options = {force: true};
    if (!fastMode || options.force) {
        cy.get('[id="gotNameButton"]').should('be.visible');
        cy.get('[id="passButton"]').should('be.visible');
        cy.get('[id="endTurnButton"]').should('be.visible');
    }

    if (!fastMode) {
        // In complete test (non-fast mode), we check that the names we see during this turn, and during other turns, appear in the Scores.
        // We also check that the names we see are elements of the celebName list rather than other strings
        retrieveTestBotInfo()
            .then(testBotInfo => {
                expect(turnIndex - turnIndexOffset, 'calculated turn index').to.equal(testBotInfo.turnCount - 1);

                const namesSeenOnThisTurn = new Set();
                const roundIndex = testBotInfo.roundIndex;

                let namesSeenOnThisRound = namesSeen[roundIndex];
                if (namesSeenOnThisRound == null) {
                    namesSeenOnThisRound = new Set();
                    namesSeen[roundIndex] = namesSeenOnThisRound;
                }

                for (const move of turnToTake) {

                    cy.wait(500);
                    if (move === 'got-it') {
                        cy.get('[id="currentNameDiv"]')
                            .then(elements => {
                                let currentNameDiv = elements[0];
                                const nameDivText = currentNameDiv.innerText;
                                const prefixString = 'Name: ';
                                assert(nameDivText.startsWith(prefixString), 'name div starts with prefix');

                                const celebName = nameDivText.substring(prefixString.length);
                                assert(allCelebNames.includes(celebName), `Celeb name '${celebName}' should be contained in celeb name list`);

                                assert(!namesSeenOnThisRound.has(celebName), `Celeb name '${celebName}' should not have been seen before on this round`);
                                assert(!namesSeenOnThisTurn.has(celebName), `Celeb name '${celebName}' should not have been seen before on this turn`);
                                namesSeenOnThisTurn.add(celebName);
                            });

                        cy.get('[id="gotNameButton"]').click(options);
                    }
                    else if (move === 'pass') {
                        cy.get('[id="passButton"]').click(options);
                    }
                    else if (move === 'end-turn') {
                        cy.get('[id="endTurnButton"]').click();
                    }
                }

                cy.get('[id="scoresDiv"]').click() // scroll here to look at scores (only needed if we're watching or videoing)
                    .then(elements => {
                        // This code has to be in a 'then' to make sure it's executed after the Sets of names are updated
                        namesSeenOnThisTurn.forEach(name => namesSeenOnThisRound.add(name));
                        namesSeenOnThisRound.forEach(name => cy.get('[id="scoresDiv"]').contains(name));
                    });
            });
    }
    else {
        // In fast mode, just click the buttons, no 0.5s wait and no checking of names
        for (const move of turnToTake) {
            if (move === 'got-it') {
                cy.get('[id="gotNameButton"]').click(options);
            }
            else if (move === 'pass') {
                cy.get('[id="passButton"]').click(options);
            }
            else if (move === 'end-turn') {
                cy.get('[id="endTurnButton"]').click();
            }
        }
    }
}

function startHostingNewGame(playerName, gameID) {
    cy.request(`${URL}/setTesting`);
    cy.get('[id="nameField"]').type(playerName);
    cy.get('[id="nameSubmitButton"]').click();
    // Wait for client to send session ID to server via websocket, so that server can associate socket to session
    cy.wait(2000);
    cy.get('[id=host]').click();

    cy.get('[id=gameIDDiv]')
        .children()
        .contains('Game ID: ')
        .then(elements => {
            const gameIDText = elements[0].innerText;
            const prefixString = 'Game ID: ';
            assert(gameIDText.startsWith(prefixString), 'game ID text starts with prefix');

            const gameID = gameIDText.substring(prefixString.length);

            assert(/^[0-9]{4}/.test(gameID), 'game ID is 4 digits');

            cy.writeFile(testTempFile, gameID)
        });
}

function checkTeamlessPlayerList(otherPlayers) {
    for (const player of otherPlayers) {
        cy.contains('.teamlessPlayerLiClass', player, { timeout: 120000 });
    }
}

function setGameParams() {
    cy.get('[id="submitGameParamsButton"]').click();
}

function allocateTeams() {
    cy.get('[id="teamsButton"]').click();

    // Should still be visible after clicking - we're allowed to allocate again until Request Names is clicked
    cy.get('[id="teamsButton"]').should('be.visible');
}

export function checkTeamList(clientState) {
    // Check I'm in a team
    cy.get('[id="teamList"]').contains('Teams');
    cy.get('[id="teamList"]').contains(clientState.playerName);

    // Check everyone else is in a team
    for (const player of clientState.otherPlayers) {
        cy.get('[id="teamList"]').contains(player);
    }

    // Find out my team index and player index, so I know which turn to select from the turns array
    cy.get('.playerInTeamTDClass').contains(clientState.playerName)
        .then(elements => {
            const element = elements[0];
            clientState.teamIndex = parseInt(element.getAttribute('teamindex'));
            clientState.playerIndex = parseInt(element.getAttribute('playerindex'));
        });
}

function requestNames() {
    cy.get('[id="requestNamesButton"]').click();
    cy.get('[id="teamsButton"]').should('not.be.visible');
    cy.get('[id="requestNamesButton"]').should('not.be.visible');

    cy.get('[id="nameListForm"]').should('be.visible');
    cy.get('[id="startGameButton"]').should('not.be.visible');
}

function submitNames(celebrityNames) {
    for (let i = 0; i < celebrityNames.length; i++) {
        cy.get(`[id="name${i + 1}"]`).type(celebrityNames[i]);
    }
    cy.get('[id="submitNamesButton"]').click();
}

function startGame() {
    cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 0 player(s)', { timeout: 60000 });

    // Wait before clicking, to give other players time to verify gameStatus
    cy.wait(1000);
    cy.get('[id="startGameButton"]').click();

    if (!fastMode) {
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="startNextRoundButton"]').should('not.be.visible');
    }
}

export function joinGame(playerName, gameID, hostName) {
    cy.get('[id="nameField"]').type(playerName);
    cy.get('[id="nameSubmitButton"]').click();
    // Wait for client to send session ID to server via websocket, so that server can associate socket to session
    cy.wait(2000);
    cy.get('[id="join"]').click();

    if (gameID) {
        cy.get('[id="gameIDField"]').type(gameID);
    }
    else {
        // read gameID from file, if it's not specified in the specs
        cy.readFile(testTempFile)
            .then(content => cy.get('[id="gameIDField"]').type(content));
    }

    cy.get('[id="gameIDSubmitButton"]').click();
}

export function selectContextMenuItemForPlayer(player, playerElementSelector, contextMenuID, menuItemID) {
    cy.get(playerElementSelector).contains(player).rightclick();

    cy.get(`[id="${contextMenuID}"]`).should('be.visible');
    cy.get(`[id="${menuItemID}"]`).click();
    cy.get(`[id="${contextMenuID}"]`).should('not.be.visible');

}

// Retrieve the info object that the client puts into a hidden div to give us data about the current game
function retrieveTestBotInfo() {
    return cy.get('[id="testBotInfoDiv"]')
        .then(elements => {
            const testBotInfoDiv = elements[0];
            const testBotInfoText = testBotInfoDiv.innerText;
            if (testBotInfoText === null
                || testBotInfoText.trim() === '') {
                return {};
            }
            const testBotInfo = JSON.parse(testBotInfoText);

            return testBotInfo;
        })
}

// Check that all constraints specified by DOMSpecs are satisfied
function checkDOMContent(DOMSpecs, clientState) {
    if (!fastMode) {
        retrieveTestBotInfo()
            .then(testBotInfo => {
                DOMSpecs.forEach(spec => {
                    const selector = spec.selector;
                    if (spec.visibleWhen) {
                        if (spec.visibleWhen.find(s => s.predicate(testBotInfo, clientState)))
                            cy.get(selector).should('be.visible');

                        if (spec.visibleWhen.find(s => s.invertible && !s.predicate(testBotInfo, clientState)))
                            cy.get(selector).should('not.be.visible');
                    }

                    if (spec.invisibleWhen) {
                        if (spec.invisibleWhen.find(s => s.predicate(testBotInfo, clientState)))
                            cy.get(selector).should('not.be.visible');
                    }

                    if (spec.notExistWhen) {
                        if (spec.notExistWhen.find(s => s.predicate(testBotInfo, clientState)))
                            cy.get(selector).should('not.exist');
                    }

                    if (spec.containsWhen) {
                        const groupSpecsByTrueFalse = groupBy(spec.containsWhen, s => s.predicate(testBotInfo, clientState));
                        if (groupSpecsByTrueFalse[true])
                            groupSpecsByTrueFalse[true].forEach(s => {
                                const text = s.text != null ? s.text : s.textFunction(testBotInfo, clientState);
                                cy.get(selector).contains(text);
                            });

                        if (groupSpecsByTrueFalse[false])
                            groupSpecsByTrueFalse[false].filter(s => s.invertible)
                                .forEach(s => {
                                    const text = s.text != null ? s.text : s.textFunction(testBotInfo, clientState);
                                    cy.get(selector).contains(text).should('not.exist');
                                });

                    }
                });
            });
    }
}

// Util function to group the elements of an array by their output, when passed as an input to groupingFunction
function groupBy(inputArray, groupingFunction) {
    const groupedArray = inputArray.reduce((groupMap, value) => {
        const mapKey = groupingFunction(value);
        groupMap[mapKey] = groupMap[mapKey] || [];
        groupMap[mapKey].push(value);

        return groupMap;
    },
        {});

    return groupedArray;
}

function checkFinalScoreForRound(clientState) {
    allCelebNames.forEach(name => cy.get('[id="scoresDiv"]').contains(name));
    cy.get('.achievedNameLi.team0')
        .then(elements => {
            const team0Score = elements.length;

            cy.get('.scoreRowClass').last().prev()
                .contains(team0Score.toString());
            cy.get('.achievedNameLi.team1')
                .then(elements => {
                    const team1Score = elements.length;

                    cy.get('.scoreRowClass').last()
                        .contains(team1Score.toString());

                    expect(team0Score + team1Score, 'scores should add up to total number of celebrities').to.equal(allCelebNames.length);
                });

        });
}