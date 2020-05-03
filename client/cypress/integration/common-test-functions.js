import { DOMSpecs } from "./dom-specs.js";

let allCelebNames = null;

export function openSiteAndPlayGame(gameSpec) {
    describe('Initialisation', () => {
        it('Checks environment variables are set', () => {
            assert.typeOf(gameSpec.index, 'number', 'PLAYER_INDEX should be set to a number');
        });
    });

    describe(`Player ${gameSpec.index + 1}`, () => {
        it('Plays a game', () => {
            cy.visit('http://192.168.1.17:8080/celebrity.html');

            const clientState = retrievePlayerParameters(gameSpec.index, gameSpec.playerNames, gameSpec.celebrityNames);
            clientState.index = gameSpec.index;
            clientState.gameID = gameSpec.gameID;
            clientState.turnIndexOffset = gameSpec.turnIndexOffset;
            clientState.turns = gameSpec.turns;
            if (gameSpec.customActions)
                clientState.customActions = gameSpec.customActions;
            clientState.restoredGame = gameSpec.restoredGame;
            clientState.namesSeen = [];

            allCelebNames = gameSpec.celebrityNames.reduce((flattenedArr, celebNameArr) => flattenedArr.concat(celebNameArr), []);

            playGame(clientState);
        });
    });
}

export function retrievePlayerParameters(index, playerNames, celebrityNames) {
    const hostName = playerNames[0];
    const playerName = playerNames[index];
    const otherPlayers = playerNames.filter(name => name !== playerName);
    const celebrityList = celebrityNames[index];
    const iAmHosting = index == 0;

    return {
        hostName: hostName,
        playerName: playerName,
        otherPlayers: otherPlayers,
        celebrityNames: celebrityList,
        iAmHosting: iAmHosting,
    }
}

export function playGame(clientState) {
    if (!clientState.iAmHosting
        || clientState.restoredGame) {
        joinGame(clientState.playerName, clientState.gameID, clientState.hostName);
    }
    else {
        startHostingNewGame(clientState.playerName, clientState.gameID);
        // give time for websocket to transmit game state before checking DOM content. Without this, it sometimes incorrectly thinks
        // Allocate Teams button should not be visible.
        cy.wait(500);
    }
    checkDOMContent(DOMSpecs, clientState);

    cy.contains('.teamlessPlayerLiClass', clientState.playerName);
    checkTeamlessPlayerList(clientState.otherPlayers);

    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        // Give everyone time to check the DOM content before it changes
        cy.wait(2000);
        setGameParams(clientState.playerName);
    }

    checkDOMContent(DOMSpecs, clientState);
    if (!clientState.restoredGame) {
        myAssert('[id="teamList"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });
    }

    // Give everyone time to check the DOM content before it changes
    cy.wait(2000);

    if (clientState.iAmHosting) {
        if (!clientState.restoredGame) {
            allocateTeams();
        }
        else {
            const allPlayers = [clientState.playerName, ...clientState.otherPlayers];

            for (let i = 0; i < allPlayers.length; i++) {
                const player = allPlayers[i];
                selectContextMenuItemForPlayer(player, '.teamlessPlayerLiClass', 'contextMenuForTeamlessPlayer', `changeToTeam${i % 2}`);
            }

        }
    }
    checkDOMContent(DOMSpecs, clientState);

    checkTeamList(clientState);

    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        requestNames();
    }
    cy.get('[id="startGameButton"]').should('not.be.visible');

    if (!clientState.restoredGame) {
        submitNames(clientState.celebrityNames);
    }

    if (clientState.iAmHosting
        && !clientState.restoredGame) {
        startGame();
    }

    clientState.counter = 0;
    waitForWakeUpTrigger(clientState);
}

// wait for wake up trigger
// when it appears, check game status. If game over, end.
// If not, wait for clickable button and click it.
// Check if the button I just clicked was the start turn button. If yes, play my turn.
// Goto 1.

function waitForWakeUpTrigger(clientState) {
    cy.get('.testTriggerClass', { timeout: 120000 })
        .then(elements => {
            const triggerElement = elements[0];

            checkDOMContent(DOMSpecs, clientState);

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
                checkFinalScoreForRound(clientState);
                console.log('Finished!!');
            }
            else if (triggerElement.innerText === 'Start turn, bot!') {
                cy.get('[id="startTurnButton"]').click();
                getNames(clientState);

                // WARNING: if you do anything else after the call to waitForWakeUpTrigger, don't forget
                // that the counter value is now wrong. And you can't increment it back, since that code
                // will be executed before the cy.get() in the new call to waitForWakeUpTrigger.
                clientState.counter = clientState.counter + 1;
                waitForWakeUpTrigger(clientState);
            }
            else if (triggerElement.innerText === 'bot-ready-to-start-next-round') {
                checkFinalScoreForRound(clientState);
                if (clientState.iAmHosting) {
                    cy.wait(5000); // Give player who finished the round time to check the scores div
                    cy.get('[id="startNextRoundButton"]').click();
                }
                else {
                    cy.get('.testTriggerClass').not(':contains("bot-ready-to-start-next-round")', { timeout: 10000 });
                }
                waitForWakeUpTrigger(clientState);
            }
        });
}

function getNames(clientState) {
    const counter = clientState.counter;
    const numPlayers = clientState.otherPlayers.length + 1;
    const teamInfoObject = clientState;
    const turnIndexOffset = clientState.turnIndexOffset;
    const turns = clientState.turns;
    const namesSeen = clientState.namesSeen;

    cy.scrollTo(0, 0);
    // cy.wait(5000); // so I can follow it
    const numTeams = 2;
    const turnIndex = turnIndexOffset + counter * numPlayers + numTeams * teamInfoObject.playerIndex + teamInfoObject.teamIndex;
    console.log(`counter ${counter}, numPlayers ${numPlayers}, teamIndex ${teamInfoObject.teamIndex}, playerIndex ${teamInfoObject.playerIndex}, turnIndexOffset ${turnIndexOffset} ==> turnIndex ${turnIndex}`);

    const turnToTake = turns[turnIndex];

    let options = {};

    // {force: true} disables scrolling, which is nice for videos but not
    // what we want in a real test. When doing {force: true}, we need the
    // should('be.visible') to make sure we at least wait for the button to appear.

    // options = {force: true};
    cy.get('[id="gotNameButton"]').should('be.visible');
    cy.get('[id="passButton"]').should('be.visible');
    cy.get('[id="endTurnButton"]').should('be.visible');

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

            // TODO
            // - Test 'Make player next in team' menu item
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
                            assert(allCelebNames.includes(celebName), 'Celeb name should be contained in celeb name list');

                            assert(!namesSeenOnThisRound.has(celebName), 'Celeb name should not have been seen before on this round');
                            assert(!namesSeenOnThisTurn.has(celebName), 'Celeb name should not have been seen before on this turn');
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

            cy.get('[id="scoresDiv"]').click() // scroll here to look at scores (for video)
                .then(elements => {
                    // This code has to be in a 'then' to make sure it's executed after the Sets of names are updated
                    namesSeenOnThisTurn.forEach(name => namesSeenOnThisRound.add(name));
                    namesSeenOnThisRound.forEach(name => cy.get('[id="scoresDiv"]').contains(name));
                });
        });

}

export function isVisible(element) {
    /* According to https://stackoverflow.com/a/21696585,
     * a more complete test is window.getComputedStyle(el) !== 'none';
     * But this is also expected to be slower, and only necessary for 'position: fixed' elements.
     *
     * UPDATE: checking getComputedStyle is anyway incorrect by itself, because it doesn't check
     * the style of parent elements
    */
    return /* window.getComputedStyle(element).display !== 'none'; */ element.offsetParent !== null;
}
export function hasContent(element) {
    const content = element.innerHTML.trim();
    return content !== '';
}
export function not(predicate) {
    return x => !predicate(x);
}
export function and(...predicates) {
    return function (x) {
        for (let i = 0; i < predicates.length; i++) {
            if (!predicates[i](x)) {
                return false;
            }
        }
        return true;
    };
}
export function myAssert(selector, predicate, { unique = true, describeExpected = 'ok' } = {}) {
    cy.get(selector)
        .then(selectedElements => {
            /* Presumed bug in Cypress, which I've decided is a nice feature.
             * If the message arg passed to `expect` includes 'but' as a whole word,
             * then when the message is printed in red or green in the Cypress plug-in's panel
             * on the left of the browser, the text 'but' and all subsequent text is omitted.
             *
             * It's basically a way to delete the text which the plugin adds at the end, which always
             * says either:
             *  expected true to equal true
             * for successful assertions, or:
             *  expected true to equal false
             * for unsuccessful ones.
             *
             * Might need to change the way I use this if I ever pass anything other than true to `to.equal`.
            */
            const magic = ' but this string is magic';
            if (unique) {
                expect(selectedElements.length, `selector ${selector} gives a unique element${magic}`).to.equal(1);
            }
            for (let i = 0; i < selectedElements.length; i++) {
                const element = selectedElements[i];
                const indexString = selectedElements.length == 1 ? '' : `${i + 1} of ${selectedElements.length} `;
                const assertionText = `field ${selector} ${indexString}is ${describeExpected}${magic}`;
                expect(predicate(element), assertionText).to.equal(true);
            }
        });
}

function startHostingNewGame(playerName, gameID) {
    cy.get('[id="nameField"]').type(playerName);
    cy.get('[id="nameSubmitButton"]').click();
    // Wait for client to send session ID to server via websocket, so that server can associate socket to session
    cy.wait(2000);
    cy.get('[id=host]').click();
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
    cy.get('[id="teamsButton"]').should('be.visible');
}

export function checkTeamList(clientState, options = {}) {
    cy.get('[id="teamList"]').contains('Teams');
    cy.get('[id="teamList"]').contains(clientState.playerName, options);

    for (const player of clientState.otherPlayers) {
        cy.get('[id="teamList"]').contains(player);
    }

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
    cy.get('[id="startGameButton"]').should('not.be.visible');
    cy.get('[id="startNextRoundButton"]').should('not.be.visible');
}

export function joinGame(playerName, gameID, hostName) {
    cy.get('[id="nameField"]').type(playerName);
    cy.get('[id="nameSubmitButton"]').click();
    // Wait for client to send session ID to server via websocket, so that server can associate socket to session
    cy.wait(2000);

    cy.get('[id="join"]').click();
    cy.get('[id="gameIDField"]').type(gameID);
    cy.get('[id="gameIDSubmitButton"]').click();
}

export function selectContextMenuItemForPlayer(player, playerElementSelector, contextMenuID, menuItemID) {
    cy.get(playerElementSelector).contains(player).rightclick();

    cy.get(`[id="${contextMenuID}"]`).should('be.visible');
    cy.get(`[id="${menuItemID}"]`).click();
    cy.get(`[id="${contextMenuID}"]`).should('not.be.visible');

}

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

function checkDOMContent(DOMSpecs, clientState) {
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

            cy.get('.scoreRowClass').last().find('td').first().next() // first() contains the round index
                .contains(team0Score.toString());
            cy.get('.achievedNameLi.team1')
                .then(elements => {
                    const team1Score = elements.length;

                    cy.get('.scoreRowClass').last().find('td').last()
                        .contains(team1Score.toString());

                    expect(team0Score + team1Score, 'scores should add up to total number of celebrities').to.equal(allCelebNames.length);
                });

        });
}