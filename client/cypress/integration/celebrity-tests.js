import { DOMSpecs } from "./dom-specs";
import * as spec4Players from "./games/04-players"
import * as specRestoredMiddle from "./games/rejoin-restored-game-middle-of-round"
import * as specRestoredEnd from "./games/rejoin-restored-game-end-of-round"
import * as randomGame from "./games/random"
import * as util from "./util.js"

/* ============================
 * Global ENV vars
*/

export let URL = 'http://localhost:8000';
const envURL = Cypress.env('URL');
if (envURL) {
    URL = envURL;
}

export const tempDir = Cypress.env('TEMP_DIR');

describe('Initialisation', () => {
    it('Checks mandatory environment variables are set', () => {
        assert.typeOf(Cypress.env('PLAYER_INDEX'), 'number', 'PLAYER_INDEX should be set to a number');
        assert.typeOf(Cypress.env('TEMP_DIR'), 'string', 'TEMP_DIR should be set to a directory path where temp files can be written')

        if (Cypress.env('RANDOM')) {
            assert.notEqual(Cypress.env('SEED'), null, 'SEED should be set to a string if RANDOM is true');
            assert.typeOf(Cypress.env('NUM_PLAYERS'), 'number', 'NUM_PLAYERS should be set to a number if RANDOM is true');
        }
    });
});

/* ============================
 * Default game spec if non-random
*/

let gameSpecs = [spec4Players.gameSpec];
if (Cypress.env('INC_RESTORED')) {
  gameSpecs.push(specRestoredMiddle.gameSpec);
  gameSpecs.push(specRestoredEnd.gameSpec);
}

/* ============================
 * Replace the default with a random game if requested
*/

if (Cypress.env('RANDOM')) {
    const numPlayers = Cypress.env('NUM_PLAYERS');
    const playerIndex = Cypress.env('PLAYER_INDEX');
    let seed = Cypress.env('SEED');
    
    if (seed) {
        /* Not specifying the seed is an error: it means the players will generate
        *  inconsistent series of turns, and not play correctly. But this is checked
        *  in describe('Initialisation') with an informative error message - the check
        *  here is just to avoid the problem of just seeing an NPE instead.
        */

        // util.generateRandomFunction requires a string, which it then hashes to create the numeric seed
        seed = seed.toString();
    }

    let slowMode = Cypress.env('SLOW_MODE');
    const specOptions = {
        seed: seed,
        fastMode: Cypress.env('FAST_MODE'),
        numRounds: Cypress.env('NUM_ROUNDS'),
        numNamesPerPlayer: Cypress.env('NUM_NAMES_PER_PLAYER'),
        slowMode: slowMode,
        fullChecksWhenNotInFastMode: true,
        turnVersion: slowMode ? 'V2' : 'V1',
    };
    if (Cypress.env('MIN_WAIT_TIME_IN_SEC')) {
        specOptions.minWaitTimeInSec = Cypress.env('MIN_WAIT_TIME_IN_SEC');
    }
    if (Cypress.env('MAX_WAIT_TIME_IN_SEC')) {
        specOptions.maxWaitTimeInSec = Cypress.env('MAX_WAIT_TIME_IN_SEC');
    }
    

    const gameSpec = randomGame.generateGame(numPlayers, specOptions);
    gameSpec.index = playerIndex;
    gameSpecs = [gameSpec];
}
for (let i = 0; i < gameSpecs.length; i++) {
    const gameSpec = gameSpecs[i];
    describe(`Player ${gameSpec.index + 1} [${gameSpec.playerNames[gameSpec.index]}]`, () => {
        it(`Plays spec ${i}: ${gameSpec.description}`, () => {
            cy.visit(URL);
            cy.request(`${URL}/setTesting`);

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
                turnsV2: gameSpec.turnsV2,
                restoredGame: gameSpec.restoredGame,
                namesSeen: [],
                fastMode: Cypress.env('FAST_MODE'),
                fullChecksWhenNotInFastMode: gameSpec.fullChecksWhenNotInFastMode,
                roundIndex: 0,
                numNamesPerPlayer: gameSpec.numNamesPerPlayer,
                numRounds: gameSpec.numRounds,
                slowMode: gameSpec.slowMode,
                random: gameSpec.random,
                minWaitTimeInSec: gameSpec.minWaitTimeInSec,
                maxWaitTimeInSec: gameSpec.maxWaitTimeInSec,
                allCelebNames: gameSpec.celebrityNames.reduce((flattenedArr, celebNameArr) => flattenedArr.concat(celebNameArr), []),
            }
            if (gameSpec.customActions)
                clientState.customActions = gameSpec.customActions;

            

            playGame(clientState);
        });
    });
}

// Play the game through to the end
export function playGame(clientState) {
    if (!clientState.iAmHosting
        || clientState.restoredGame) {

        if (clientState.restoredGame) {
            // Since the player at index 0 is hard-coded to be the host, other players must make sure he has time to join the game first.
            if (clientState.index !== 0) {
                cy.readFile(`${tempDir}/host_joined_game_${clientState.gameID}`, {timeout: 60000});
            }
        }

        joinGame(clientState.playerName, clientState.gameID, clientState.hostName, clientState);
        if (clientState.restoredGame) {
            // Since the player at index 0 is hard-coded to be the host, other players must make sure he has time to join the game first.
            if (clientState.index === 0) {
                cy.writeFile(`${tempDir}/host_joined_game_${clientState.gameID}`, '0');
            }
        }
    }
    else {
        startHostingNewGame(clientState.playerName, clientState.gameID, clientState);
    }
    checkDOMContent(DOMSpecs, clientState);

    // Check I and the other players are listed, ready to be put into teams
    cy.contains('.teamlessPlayerLiClass', clientState.playerName, { timeout: 20000 });
    const checkTeamlessPromise = checkTeamlessPlayerList(clientState.otherPlayers);

    // Set the game parameters (num rounds, num names per player, etc)
    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        // Give everyone time to check the DOM content before it changes

        const limit = clientState.otherPlayers.length + 1; // Non-host players are indexed 1 - limit
        for (let otherIndex = 1; otherIndex < limit; otherIndex++) {
            cy.get('html')
            .then(() => {
                // put inside a get/then to make sure there has been enough time to set clientState.gameID
                cy.readFile(`${tempDir}/checked_initial_dom_${clientState.gameID}.${otherIndex}`);
            });
        }

        setGameParams(clientState);
    }
    else {
        cy.get('html')
        .then(() => {
                // put inside a get/then to make sure there has been enough time to set clientState.gameID
                cy.writeFile(`${tempDir}/checked_initial_dom_${clientState.gameID}.${clientState.index}`, '0');
        });
    }



    checkDOMContent(DOMSpecs, clientState);
    if (!clientState.restoredGame
        && !clientState.fastMode) {
        cy.get('[id="teamList"]')
            .then(elements => {
                const teamList = elements[0];
                expect(teamList.innerText.trim(), 'Team list should be empty').to.equal('');
            });
    }

    if (!clientState.fastMode) {
        // Give everyone time to check the DOM content before it changes
        if (clientState.iAmHosting) {
            const limit = clientState.otherPlayers.length + 1; // Non-host players are indexed 1 - limit
            for (let otherIndex = 1; otherIndex < limit; otherIndex++) {
                cy.get('html')
                    .then(() => {
                        // put inside a get/then to make sure there has been enough time to set clientState.gameID
                        cy.readFile(`${tempDir}/checked_dom_after_game_params_${clientState.gameID}.${otherIndex}`);
                    });
            }
        }
        else {
            cy.get('html')
                .then(() => {
                    // put inside a get/then to make sure there has been enough time to set clientState.gameID
                    cy.writeFile(`${tempDir}/checked_dom_after_game_params_${clientState.gameID}.${clientState.index}`, '0');
                });
        }
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

    if (!clientState.fastMode) {
        cy.get('[id="startGameButton"]').should('not.be.visible');
    }

    if (!clientState.restoredGame) {
        submitNames(clientState.celebrityNames);
    }

    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        startGame(clientState);
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

            console.log(util.formatTime(), `found test trigger ${triggerElement.innerText}`);

            if (clientState.fullChecksWhenNotInFastMode) {
                checkDOMContent(DOMSpecs, clientState);
            }

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
                if (!clientState.fastMode && clientState.fullChecksWhenNotInFastMode) {
                    checkFinalScoreForRound(clientState);
                }
                console.log(util.formatTime(), 'Finished!!');
            }
            else if (triggerElement.innerText === 'bot-start-turn') {
                // It's my turn, get the names I'm supposed to get on this turn
                cy.get('[id="startTurnButton"]').click()
                    .then(() => {
                        getNames(clientState);

                        // WARNING: if you do anything else after the call to waitForWakeUpTrigger, don't forget
                        // that the turnCounter value is now wrong. And you can't increment it back, since that code
                        // will be executed before the cy.get() in the new call to waitForWakeUpTrigger.
                        clientState.turnCounter = clientState.turnCounter + 1;
                        waitForWakeUpTrigger(clientState);
                    });

            }
            else if (triggerElement.innerText === 'bot-ready-to-start-next-round') {
                // Ready to start a new round.
                // Only the host needs to click something now, but all players will see the same trigger text.

                clientState.roundIndex = clientState.roundIndex + 1;
                if (!clientState.fastMode && clientState.fullChecksWhenNotInFastMode) {
                    checkFinalScoreForRound(clientState);
                }

                if (clientState.iAmHosting) {
                    // Host clicks the start next round button
                    if (!clientState.fastMode && clientState.fullChecksWhenNotInFastMode) {
                        cy.wait(5000); // Give player who finished the round time to check the scores div
                    }
                    cy.get('[id="startNextRoundButton"]').click();
                }
                else {
                    console.log(util.formatTime(), 'waiting for ready-to-start-next-round to clear');
                    // Non-host just waits until the trigger text isn't there any more, to avoid endlessly re-entering this method
                    cy.get('.testTriggerClass').contains('bot-ready-to-start-next-round', { timeout: 60000 }).should('not.exist');
                }
                waitForWakeUpTrigger(clientState);
            }
            else if (triggerElement.innerText === 'bot-wait-for-turn' ) {
                // Somebody else's turn, wait until the test trigger is cleared, then wait for next one
                cy.get('.testTriggerClass').contains('bot-wait-for-turn', { timeout: 60000 }).should('not.exist');
                waitForWakeUpTrigger(clientState);
            }
        });
}

// Get the names I'm supposed to get on this turn
function getNames(clientState) {
    if (clientState.turnsV2) {
        getNamesV2(clientState);
    }
    else {
        const turnCounter = clientState.turnCounter;
        const numPlayers = clientState.otherPlayers.length + 1;
        const turnIndexOffset = clientState.turnIndexOffset;
        const playerIndex = clientState.playerIndex;
        const teamIndex = clientState.teamIndex;
        const turns = clientState.turns;
        const namesSeen = clientState.namesSeen;

        // 'turns' is an array containing all turns taken by all players. Calculate the index we want.
        const numTeams = 2;
        const numPlayersPerTeam = numPlayers / numTeams; // NB: numPlayers may be odd

        // If numPlayers is odd, team 0 has one more player than team 1.
        const numPlayersInMyTeam = teamIndex == 0 ? Math.ceil(numPlayersPerTeam) : Math.floor(numPlayersPerTeam);
        const numTurnsBetweenMyTurns = numTeams * numPlayersInMyTeam;
        const turnIndex = turnIndexOffset + turnCounter * numTurnsBetweenMyTurns + numTeams * playerIndex + teamIndex;
        console.log(util.formatTime(), `turnCounter ${turnCounter}, numPlayers ${numPlayers}, teamIndex ${teamIndex}, numPlayersInMyTeam, ${numPlayersInMyTeam}, numTurnsBetweenMyTurns ${numTurnsBetweenMyTurns}, playerIndex ${playerIndex}, turnIndexOffset ${turnIndexOffset} ==> turnIndex ${turnIndex}`);

        const turnToTake = turns[turnIndex];
        console.log(util.formatTime(), `turnToTake: ${turnToTake}`);

        if (!clientState.fastMode && clientState.fullChecksWhenNotInFastMode) {
            cy.get('[id="gotNameButton"]').should('be.visible');
            cy.get('[id="passButton"]').should('be.visible');
            cy.get('[id="endTurnButton"]').should('be.visible');
        }

        if (!clientState.fastMode && clientState.fullChecksWhenNotInFastMode) {
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

                    let delayInSec = 0;
                    let totalExpectedWaitTimeInSec = 0;
                    const roundDurationInSec = 60;

                    const scoresArray = testBotInfo.scores;
                    if (scoresArray.find(score => score > 0)) {
                        cy.get('[id="scoresDiv"]')
                            .then(elements => {
                                const [totalScore, namesPreviouslyOnScoresDiv] = readScoresDiv(elements[0]);
                                takeMoves(0, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, false);
                                cy.scrollTo(0, 0); // Looks nicer when watching
                            });
                    }
                    else {
                        takeMoves(0, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, [[], []], false);
                        cy.scrollTo(0, 0); // Looks nicer when watching
                    }
                });
        }
        else {
            // In fast mode, just click the buttons, no 0.5s wait and no checking of names
            for (const move of turnToTake) {
                if (move === 'got-it') {
                    cy.get('[id="gotNameButton"]').click();
                }
                else if (move === 'pass') {
                    cy.get('[id="passButton"]').click();
                }
                else if (move === 'end-turn') {
                    cy.get('[id="endTurnButton"]').click();
                }
                else if (typeof (move) === 'number') {
                    cy.wait(move * 1000);
                }
            }
            cy.scrollTo(0, 0); // Looks nicer when watching
        }
    }
}

// We need a recursive function rather than a loop over turnToTake, so that when we're in slowMode,
// each call to cy.wait knows what the calculated delay is.
function takeMoves(moveIndex, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, gotAtLeastOneName) {
    if (moveIndex >= turnToTake.length) {
        // Reached end of turn - do final check, then terminate recursion
        if (clientState.slowMode) {
            /* In slow mode, we leave a big margin of time at the end of the turn to not click anything, in case delays
            *  during the turn mean that our waits would add up to more than 60s.
            *  
            *  If we get a case where we actually have most of the margin left when we get to the end of our turnToTake array,
            *  we need to wait until the turn has really ended before checking the scores div. Check this by checking the
            *  GotIt button is not visible
            */
            if (namesSeenOnThisRound.length > 0
                || namesSeenOnThisTurn.length > 0) {
                const adjustedTimeRemaining = roundDurationInSec - totalExpectedWaitTimeInSec + 2*delayInSec;
                cy.get('[id="gotNameButton"]', { timeout: 1000 * adjustedTimeRemaining }).should('not.be.visible'); // timeout on a should goes on the parent get()
                cy.get('[id="scoresDiv"]')
                    .then(elements => {
                        // This code has to be in a 'then' to make sure it's executed after the Sets of names are updated
                        console.log(util.formatTime(), 'reached end of turn, checking scores list');
                        namesSeenOnThisTurn.forEach(name => namesSeenOnThisRound.add(name));
                        const [totalScore, namesSeenNowOnScoresDiv] = readScoresDiv(elements[0]);
                        assert.deepEqual(namesSeenNowOnScoresDiv.sort(), [...namesPreviouslyOnScoresDiv, ...namesSeenOnThisTurn].sort(), 'Names now listed in scores should be the names we had before, plus the names we saw on this turn');
                    });
            }
        }
        else {
            if (gotAtLeastOneName
                || namesPreviouslyOnScoresDiv.length > 0) {
                cy.get('[id="gotNameButton"]').should('not.be.visible');
                cy.get('[id="scoresDiv"]')
                    .then(elements => {
                        // This code has to be in a 'then' to make sure it's executed after the Sets of names are updated
                        namesSeenOnThisTurn.forEach(name => namesSeenOnThisRound.add(name));

                        console.log(util.formatTime(), 'Checking ScoresDiv has the names I saw on this turn');
                        const [totalScore, namesSeenNowOnScoresDiv] = readScoresDiv(elements[0]);

                        for (let scoresSubListIndex = 0; scoresSubListIndex < namesPreviouslyOnScoresDiv.length; scoresSubListIndex++) {
                            const namesPreviouslySeenInThisSubList = namesPreviouslyOnScoresDiv[scoresSubListIndex];
                            const namesNowSeenInThisSubList        = namesSeenNowOnScoresDiv[scoresSubListIndex];
                            let namesExpectedOnScoresDiv;
                            if (scoresSubListIndex == clientState.teamIndex) {
                                namesExpectedOnScoresDiv = [...namesPreviouslySeenInThisSubList, ...namesSeenOnThisTurn];
                            }
                            else {
                                namesExpectedOnScoresDiv = namesPreviouslySeenInThisSubList;
                            }
                            assert.equal(namesNowSeenInThisSubList.length, namesExpectedOnScoresDiv.length, 'There should be the right number of names now listed in scores');
                            for (let nameIndex = 0; nameIndex < namesNowSeenInThisSubList.length; nameIndex++) {
                                assert.equal(namesNowSeenInThisSubList[nameIndex], namesExpectedOnScoresDiv[nameIndex], `Expect ${nameIndex}th element of scores list to have the right value`);
                            }
                        }
                    });
            }
        }
    }


    if (!clientState.slowMode) {
        // Need to wait to make sure DOM is updated with new name, otherwise we can check the same name twice.
        // Should be a way to avoid this, but can't find it
        cy.wait(500);
    }

    const move = turnToTake[moveIndex];

    let buttonID = null;
    if (move === 'got-it') {
        buttonID = 'gotNameButton';
        gotAtLeastOneName = true;

        cy.get('[id="currentNameDiv"]')
            .then(elements => {
                let currentNameDiv = elements[0];
                const nameDivText = currentNameDiv.innerText;
                const prefixString = 'Name: ';
                assert(nameDivText.startsWith(prefixString), 'name div starts with prefix');

                const celebName = nameDivText.substring(prefixString.length);
                assert(clientState.allCelebNames.includes(celebName), `Celeb name '${celebName}' should be contained in celeb name list`);

                assert(!namesSeenOnThisRound.has(celebName), `Celeb name '${celebName}' should not have been seen before on this round`);
                assert(!namesSeenOnThisTurn.has(celebName), `Celeb name '${celebName}' should not have been seen before on this turn`);
                namesSeenOnThisTurn.add(celebName);

                cy.get(`[id="${buttonID}"]`).click()
                .then(() => {
                    console.log(util.formatTime(), `took move ${move}`);
                    if (clientState.slowMode
                        && moveIndex < turnToTake.length - 1) {
                        cy.get('[id="gameStatusDiv"]').contains('Seconds remaining:')
                            .then(elements => {
                                const gameStatusDiv = elements[0];
                                const statusDivText = gameStatusDiv.innerText;
                                const prefixString = 'Seconds remaining: ';
                                assert(statusDivText.startsWith(prefixString), `status div starts with ${prefixString}`);
    
                                const secondsRemainingText = statusDivText.substring(prefixString.length);
                                const secondsRemaining = parseInt(secondsRemainingText);
                                const expectedSecondsRemaining = roundDurationInSec - totalExpectedWaitTimeInSec;
                                delayInSec = Math.max(0, expectedSecondsRemaining - secondsRemaining);
    
                                console.log(util.formatTime(), `Read ${secondsRemaining}s remaining from page. Total expected wait time was ${totalExpectedWaitTimeInSec}, so expected ${expectedSecondsRemaining}s. Delay ${delayInSec}s.`)
    
                                takeMoves(moveIndex + 1, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, gotAtLeastOneName);
                            });
                    }
                    else {
                        takeMoves(moveIndex + 1, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, gotAtLeastOneName);
                    }
                });
            });

    }
    else if (move === 'pass') {
        buttonID = 'passButton';
    }
    else if (move === 'end-turn') {
        buttonID = 'endTurnButton';
    }
    else if (typeof (move) === 'number') {
        const adjustedWaitTime = Math.max(0.5, move - delayInSec); // always wait at least 500ms, same as in block 'if (!clientState.slowMode)' above
        cy.wait(adjustedWaitTime * 1000)
            .then(() => {
                totalExpectedWaitTimeInSec += move;
                console.log(util.formatTime(), `Had a ${move}-second wait specified, with ${delayInSec}s delay. Waited ${adjustedWaitTime} seconds. Total expected wait time now ${totalExpectedWaitTimeInSec}s.`);

                takeMoves(moveIndex+1, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, gotAtLeastOneName);
            });
    }

    if (move === 'pass' || move === 'end-turn') {
        cy.get(`[id="${buttonID}"]`).click()
            .then(() => {
                console.log(util.formatTime(), `took move ${move}`);
                if (clientState.slowMode
                    && moveIndex < turnToTake.length - 1) {
                    cy.get('[id="gameStatusDiv"]').contains('Seconds remaining:')
                        .then(elements => {
                            const gameStatusDiv = elements[0];
                            const statusDivText = gameStatusDiv.innerText;
                            const prefixString = 'Seconds remaining: ';
                            assert(statusDivText.startsWith(prefixString), `status div starts with ${prefixString}`);

                            const secondsRemainingText = statusDivText.substring(prefixString.length);
                            const secondsRemaining = parseInt(secondsRemainingText);
                            const expectedSecondsRemaining = roundDurationInSec - totalExpectedWaitTimeInSec;
                            delayInSec = Math.max(0, expectedSecondsRemaining - secondsRemaining);

                            console.log(util.formatTime(), `Read ${secondsRemaining}s remaining from page. Total expected wait time was ${totalExpectedWaitTimeInSec}, so expected ${expectedSecondsRemaining}s. Delay ${delayInSec}s.`)

                            takeMoves(moveIndex + 1, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, gotAtLeastOneName);
                        });
                }
                else {
                    takeMoves(moveIndex + 1, turnToTake, clientState, roundDurationInSec, delayInSec, totalExpectedWaitTimeInSec, namesSeenOnThisRound, namesSeenOnThisTurn, namesPreviouslyOnScoresDiv, gotAtLeastOneName);
                }
            });

    }
}

function getNamesV2(clientState) {
    retrieveTestBotInfo()
        .then(testBotInfo => {
            const clickIndex = testBotInfo.gameGlobalNameIndex;
            const prevIndex  = clientState.prevGlobalNameIndex ? clientState.prevGlobalNameIndex : 0;

            let passCount = 0;
            let randomInvocationCount = 0;
            for (let otherPlayerClickIndex=prevIndex; otherPlayerClickIndex < clickIndex; otherPlayerClickIndex++) {
                clientState.random(); // Other player invoked the random to decide how long to wait
                randomInvocationCount++;

                // TODO other player may have ended round

                // Other player invoked the random to decide whether or not to pass
                if (clientState.random() < 0.1) {
                    // other player passed, decrement the index to go through another iteration of the loop and call the random again
                    otherPlayerClickIndex--;
                    passCount++;
                }
                randomInvocationCount++;
            }
            const indexDelta = clickIndex - prevIndex;
            console.log(util.formatTime(), `Between the last turn and this turn, global index has gone from ${prevIndex} to ${clickIndex}, a change of ${indexDelta}. There were also ${passCount} passes, so the random has been invoked ${randomInvocationCount} times.`)

            const roundDurationInSec = 60;
            let roundMarginInSec = 3;
            if (!clientState.fastMode && clientState.fullChecksWhenNotInFastMode) {
                roundMarginInSec = 5;
            }
            const playDurationInSec = roundDurationInSec - roundMarginInSec;

            const namesSeenOnThisTurn = new Set();
            const roundIndex = testBotInfo.roundIndex;

            let namesSeenOnThisRound = clientState.namesSeen[roundIndex];
            if (namesSeenOnThisRound == null) {
                namesSeenOnThisRound = new Set();
                clientState.namesSeen[roundIndex] = namesSeenOnThisRound;
            }

            let delayInSec = 0;
            let totalExpectedWaitTimeInSec = 0;

            const scoresArray = testBotInfo.scores;
            if (scoresArray.find(score => score > 0)) {
                cy.get('[id="scoresDiv"]')
                    .then(elements => {
                        const [totalScore, namesPreviouslyOnScoresDiv] = readScoresDiv(elements[0]);
                        takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, false, null, namesPreviouslyOnScoresDiv);
                        cy.scrollTo(0, 0); // Looks nicer when watching
                    });
            }
            else {
                takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, false, null, [[], []]);
            }
        });
}

function takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, gotAtLeastOneName, secondsRemainingFromDOM, namesPreviouslyOnScoresDiv) {
    const numNames = clientState.allCelebNames.length;
    if (gotAtLeastOneName
        && clickIndex % numNames === 0) {
            clientState.prevGlobalNameIndex = clickIndex;
            console.log(util.formatTime(), `Reached end of round, not taking any more turns. Global index now ${clientState.prevGlobalNameIndex}.`);
    }
    else {
        const waitTimeRange = clientState.maxWaitTimeInSec - clientState.minWaitTimeInSec;
        const waitDuration = clientState.minWaitTimeInSec + Math.floor(waitTimeRange * clientState.random());

        const totalActualWaitTimeInSec = totalExpectedWaitTimeInSec + delayInSec;
        const estimatedTotalWaitTimeAfterThisWaitInSec = waitDuration + totalActualWaitTimeInSec;
        const estimatedSecondsRemainingAfterThisWaitInSec = playDurationInSec - estimatedTotalWaitTimeAfterThisWaitInSec;
        const adjustedEstimatedTotalWaitTimeAfterThisWaitInSec = estimatedTotalWaitTimeAfterThisWaitInSec + delayInSec; // Add the delay on a second time, to give a bigger margin
        console.log(util.formatTime(), `totalExpectedWaitTime [${totalExpectedWaitTimeInSec}s], delay [${delayInSec}s], actual wait time [${totalActualWaitTimeInSec}s], wait time for this turn [${waitDuration}s], estimated total after this wait [${estimatedTotalWaitTimeAfterThisWaitInSec}s], adjusted to [${adjustedEstimatedTotalWaitTimeAfterThisWaitInSec}s], play duration [${playDurationInSec}s], seconds remaining after estimate [${estimatedSecondsRemainingAfterThisWaitInSec}s, margin applied], seconds remaining from DOM [${secondsRemainingFromDOM}s]`);

        if (adjustedEstimatedTotalWaitTimeAfterThisWaitInSec >= playDurationInSec) {
            clientState.prevGlobalNameIndex = clickIndex;
            console.log(util.formatTime(), `Total expected wait time ${totalExpectedWaitTimeInSec}s, delay ${delayInSec}s, new wait duration ${waitDuration} would take us to or beyond the play duration of ${playDurationInSec}s. Not taking any more turns.  Global index now ${clientState.prevGlobalNameIndex}.`);

            if (gotAtLeastOneName
                || namesPreviouslyOnScoresDiv.length > 0) {
                const adjustedTimeRemaining = roundDurationInSec - totalExpectedWaitTimeInSec + 2 * delayInSec;
                cy.get('[id="gotNameButton"', { timeout: 1000 * adjustedTimeRemaining }).should('not.be.visible')
                    .then(() => {
                        cy.get('[id="scoresDiv"]')
                            .then(elements => {
                                // This code has to be in a 'then' to make sure it's executed after the Sets of names are updated
                                namesSeenOnThisTurn.forEach(name => namesSeenOnThisRound.add(name));

                                console.log(util.formatTime(), 'Checking ScoresDiv has the names I saw on this turn');
                                const [totalScore, namesSeenNowOnScoresDiv] = readScoresDiv(elements[0]);

                                for (let scoresSubListIndex = 0; scoresSubListIndex < namesPreviouslyOnScoresDiv.length; scoresSubListIndex++) {
                                    const namesPreviouslySeenInThisSubList = namesPreviouslyOnScoresDiv[scoresSubListIndex];
                                    const namesNowSeenInThisSubList = namesSeenNowOnScoresDiv[scoresSubListIndex];
                                    let namesExpectedOnScoresDiv;
                                    if (scoresSubListIndex == clientState.teamIndex) {
                                        namesExpectedOnScoresDiv = [...namesPreviouslySeenInThisSubList, ...namesSeenOnThisTurn];
                                    }
                                    else {
                                        namesExpectedOnScoresDiv = namesPreviouslySeenInThisSubList;
                                    }
                                    assert.equal(namesNowSeenInThisSubList.length, namesExpectedOnScoresDiv.length, 'There should be the right number of names now listed in scores');
                                    for (let nameIndex = 0; nameIndex < namesNowSeenInThisSubList.length; nameIndex++) {
                                        assert.equal(namesNowSeenInThisSubList[nameIndex], namesExpectedOnScoresDiv[nameIndex], `Expect ${nameIndex}th element of scores list to have the right value`);
                                    }
                                }
                            });
                    });

            }
        }
        else {
            cy.wait(waitDuration * 1000)
            .then(() => {
                totalExpectedWaitTimeInSec += waitDuration;
                let buttonID = 'gotNameButton';
                if (clientState.random() < 0.1) {
                    buttonID = 'passButton';

                    cy.get(`[id="${buttonID}"]`).click()
                    .then(() => {
                        console.log(util.formatTime(), `Waited ${waitDuration}s and clicked ${buttonID}. Total expected wait time now ${totalExpectedWaitTimeInSec}s.`);
                        if (buttonID === 'gotNameButton') {
                            clickIndex++;
                            gotAtLeastOneName = true;
                        }
                        if (clickIndex % numNames !== 0) {
                            cy.get('[id="gameStatusDiv"]').contains('Seconds remaining:')
                                .then(elements => {
                                    const gameStatusDiv = elements[0];
                                    const statusDivText = gameStatusDiv.innerText;
                                    const prefixString = 'Seconds remaining: ';
                                    assert(statusDivText.startsWith(prefixString), `status div starts with ${prefixString}`);
        
                                    const secondsRemainingText = statusDivText.substring(prefixString.length);
                                    secondsRemainingFromDOM = parseInt(secondsRemainingText);
                                    const expectedSecondsRemaining = roundDurationInSec - totalExpectedWaitTimeInSec;
                                    delayInSec = Math.max(0, expectedSecondsRemaining - secondsRemainingFromDOM);
        
                                    console.log(util.formatTime(), `Read ${secondsRemainingFromDOM}s remaining from page. Total expected wait time was ${totalExpectedWaitTimeInSec}, so expected ${expectedSecondsRemaining}s. Delay ${delayInSec}s.`)
        
                                    takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, gotAtLeastOneName, secondsRemainingFromDOM, namesPreviouslyOnScoresDiv);
                                });
                        }
                        else {
                            takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, gotAtLeastOneName, secondsRemainingFromDOM, namesPreviouslyOnScoresDiv);
                        }
                    });
                }
                else {
                    cy.get('[id="currentNameDiv"]')
                    .then(elements => {
                        let currentNameDiv = elements[0];
                        const nameDivText = currentNameDiv.innerText;
                        const prefixString = 'Name: ';
                        assert(nameDivText.startsWith(prefixString), 'name div starts with prefix');
        
                        const celebName = nameDivText.substring(prefixString.length);
                        assert(clientState.allCelebNames.includes(celebName), `Celeb name '${celebName}' should be contained in celeb name list`);
        
                        assert(!namesSeenOnThisRound.has(celebName), `Celeb name '${celebName}' should not have been seen before on this round`);
                        assert(!namesSeenOnThisTurn.has(celebName), `Celeb name '${celebName}' should not have been seen before on this turn`);
                        namesSeenOnThisTurn.add(celebName);

                        cy.get(`[id="${buttonID}"]`).click()
                        .then(() => {
                            console.log(util.formatTime(), `Waited ${waitDuration}s and clicked ${buttonID}. Total expected wait time now ${totalExpectedWaitTimeInSec}s.`);
                            if (buttonID === 'gotNameButton') {
                                clickIndex++;
                                gotAtLeastOneName = true;
                            }
                            if (clickIndex % numNames !== 0) {
                                cy.get('[id="gameStatusDiv"]').contains('Seconds remaining:')
                                    .then(elements => {
                                        const gameStatusDiv = elements[0];
                                        const statusDivText = gameStatusDiv.innerText;
                                        const prefixString = 'Seconds remaining: ';
                                        assert(statusDivText.startsWith(prefixString), `status div starts with ${prefixString}`);
            
                                        const secondsRemainingText = statusDivText.substring(prefixString.length);
                                        secondsRemainingFromDOM = parseInt(secondsRemainingText);
                                        const expectedSecondsRemaining = roundDurationInSec - totalExpectedWaitTimeInSec;
                                        delayInSec = Math.max(0, expectedSecondsRemaining - secondsRemainingFromDOM);
            
                                        console.log(util.formatTime(), `Read ${secondsRemainingFromDOM}s remaining from page. Total expected wait time was ${totalExpectedWaitTimeInSec}, so expected ${expectedSecondsRemaining}s. Delay ${delayInSec}s.`)
            
                                        takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, gotAtLeastOneName, secondsRemainingFromDOM, namesPreviouslyOnScoresDiv);
                                    });
                            }
                            else {
                                takeMovesV2(delayInSec, totalExpectedWaitTimeInSec, clickIndex, playDurationInSec, roundDurationInSec, clientState, namesSeenOnThisRound, namesSeenOnThisTurn, gotAtLeastOneName, secondsRemainingFromDOM, namesPreviouslyOnScoresDiv);
                            }
                        });
                    });
                }
            });
        }
    }
}



function startHostingNewGame(playerName, gameID, clientState) {
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

            clientState.gameID = gameID;
            cy.writeFile(`${tempDir}/gameID`, gameID)
        });
}

function checkTeamlessPlayerList(otherPlayers) {
    let returnObject = null;
    for (const player of otherPlayers) {
        returnObject = cy.contains('.teamlessPlayerLiClass', player, { timeout: 120000 });
    }

    return returnObject;
}

function setGameParams(clientState) {
    if (clientState.numRounds) {
        cy.get('[id="numRoundsField"').clear().type(clientState.numRounds);
    }
    if (clientState.numNamesPerPlayer) {
        cy.get('[id="numNamesField"]').clear().type(clientState.numNamesPerPlayer);
    }
    cy.get('[id="submitGameParamsButton"]').click();
}

function allocateTeams() {
    cy.get('[id="teamsButton"]').click();

    // Should still be visible after clicking - we're allowed to allocate again until Request Names is clicked
    cy.get('[id="teamsButton"]').should('be.visible');
}

export function checkTeamList(clientState) {
    // Check I'm in a team
    cy.get('[id="teamList"]').contains('Teams', {timeout: 30000});
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
        cy.get(`[id="name${i + 1}"]`, {timeout: 30000}).type(celebrityNames[i]);
    }
    cy.get('[id="submitNamesButton"]').click();
}

function startGame(clientState) {
    cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 0 player(s)', { timeout: 60000 });

    // Wait before clicking, to give other players time to verify gameStatus
    cy.wait(1000);
    cy.get('[id="startGameButton"]').click();

    if (!clientState.fastMode) {
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="startNextRoundButton"]').should('not.be.visible');
    }
}

export function joinGame(playerName, gameID, hostName, clientState) {
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
        cy.readFile(`${tempDir}/gameID`, {timeout: 60000})
            .then(content => {
                cy.get('[id="gameIDField"]').type(content);
                clientState.gameID = content;
           });
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
    if (!clientState.fastMode) {
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
    cy.get('[id="scoresDiv"]')
        .then(elements => {
            const scoresDiv = elements[0];
            const [totalScore, seenNames] = readScoresDiv(scoresDiv);

            assert.equal(totalScore, clientState.allCelebNames.length, 'Total score should match total number of names');
            const allCelebNamesSorted = [...clientState.allCelebNames];
            allCelebNamesSorted.sort();
            const allSeenNames = seenNames.reduce((leftArr, rightArr) => leftArr.concat(rightArr));
            allSeenNames.sort();
            assert.deepEqual(allSeenNames, allCelebNamesSorted, 'Names in score list should match list of all celeb names');
        });
}

function readScoresDiv(scoresDiv) {
    const scoresLines = scoresDiv.innerText.split('\n'); // NB: innerText includes newlines, textContent does not

    let thisTeamsScore;
    let totalScore = 0;
    let prevTeamOffset = 0;
    let seenNames = [[]];
    for(let scoreLineIndex=0; scoreLineIndex<scoresLines.length; scoreLineIndex++) {
        const text = scoresLines[scoreLineIndex];
        if (scoreLineIndex === 0) {
            assert.equal( text, 'Scores' );
        }
        else if (scoreLineIndex === 1) {
            assert.equal(text, 'Team 1');
        }
        else if (scoreLineIndex === 2) {
            const prefix = 'Score: ';
            assert(text.startsWith(prefix), `text '${text} should start with '${prefix}'`);
            const scoreString = text.substring(prefix.length);
            thisTeamsScore = parseInt(scoreString);
            totalScore += thisTeamsScore;
        }
        else if (scoreLineIndex < prevTeamOffset + thisTeamsScore + 3) {
            seenNames[seenNames.length - 1].push(text);
        }
        else if (scoreLineIndex === thisTeamsScore + 3) {
            assert.equal(text, 'Team 2');
            seenNames.push([]);
        }
        else if (scoreLineIndex === thisTeamsScore + 4) {
            const prefix = 'Score: ';
            assert(text.startsWith(prefix), `text '${text} should start with '${prefix}'`);
            const scoreString = text.substring(prefix.length);
            thisTeamsScore = parseInt(scoreString);
            totalScore += thisTeamsScore;
            prevTeamOffset = scoreLineIndex - 2;
        }
    }

    return [totalScore, seenNames];
}