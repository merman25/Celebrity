import { DOMSpecs } from "./dom-specs.js";

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
    if (clientState.iAmHosting) {
        startHostingNewGame(clientState.playerName, clientState.gameID);
        checkDOMContent(DOMSpecs, clientState);
    }
    else {
        joinGame(clientState.playerName, clientState.gameID, clientState.hostName);
        checkDOMContent(DOMSpecs, clientState);
    }

    // cy.window().then(window => {
    //     window.resizeTo(800, 600);
    //     window.moveTo(index * 200, 0);
    // });

    checkTeamlessPlayerList(clientState.otherPlayers);
    if (clientState.iAmHosting) {
        // Give everyone time to check the DOM content before it changes
        cy.wait(2000);
        setGameParams(clientState.playerName);
    }

    checkDOMContent(DOMSpecs, clientState);
    assert('[id="teamList"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });

    // Give everyone time to check the DOM content before it changes
    cy.wait(2000);

    if (clientState.iAmHosting) {
        allocateTeams();
    }
    checkDOMContent(DOMSpecs, clientState);

    checkTeamList(clientState);

    if (clientState.iAmHosting) {
        requestNames();
    }
    cy.get('[id="startGameButton"]').should('not.be.visible');

    submitNames(clientState.celebrityNames);

    if (clientState.iAmHosting) {
        startGame();
    }

    clientState.counter = 0;
    waitForWakeUpTrigger(clientState);
}

export function hostRestoredGame(clientState) {
    cy.get('[id="nameField"]').type(clientState.playerName);
    cy.get('[id="nameSubmitButton"]').click();

    // Wait for client to send session ID to server via websocket, so that server can associate socket to session
    cy.wait(2000);

    cy.get('[id="join"]').click();
    cy.get('[id="gameIDField"]').type(clientState.gameID);
    cy.get('[id="gameIDSubmitButton"]').click();

    checkDOMContent(DOMSpecs, clientState);
    cy.contains('.teamlessPlayerLiClass', clientState.playerName);

    checkTeamlessPlayerList(clientState.otherPlayers);

    cy.wait(2000); // give the other players time to check the teamless player list

    const allPlayers = [clientState.playerName, ...clientState.otherPlayers];

    for (let i = 0; i < allPlayers.length; i++) {
        const player = allPlayers[i];
        selectContextMenuItemForPlayer(player, '.teamlessPlayerLiClass', 'contextMenuForTeamlessPlayer', `changeToTeam${i % 2}`);
    }

    checkTeamList(clientState);

    checkDOMContent(DOMSpecs, clientState);
    clientState.counter = 0;
    waitForWakeUpTrigger(clientState);
}

export function joinRestoredGame(clientState) {
    cy.get('[id="nameField"]').type(clientState.playerName);
    cy.get('[id="nameSubmitButton"]').click();

    // Wait for client to send session ID to server via websocket, so that server can associate socket to session
    cy.wait(2000);

    cy.get('[id="join"]').click();
    cy.get('[id="gameIDField"]').type(clientState.gameID);
    cy.get('[id="gameIDSubmitButton"]').click();

    cy.contains('.teamlessPlayerLiClass', clientState.playerName);

    checkDOMContent(DOMSpecs, clientState);
    checkTeamlessPlayerList(clientState.otherPlayers);

    checkTeamList(clientState, { timeout: 30000 });

    checkDOMContent(DOMSpecs, clientState);
    clientState.counter = 0;
    waitForWakeUpTrigger(clientState);
}

// wait for wake up trigger
// when it appears, check game status. If game over, end.
// If not, wait for clickable button and click it.
// Check if the button I just clicked was the start turn button. If yes, play my turn.
// Goto 1.

export function waitForWakeUpTrigger(clientState) {
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
            else if (triggerElement.innerText === 'You\'re done, bot!') {
                console.log('Finished!!');
            }
            else if (triggerElement.innerText === 'Start turn, bot!') {
                cy.get('[id="startTurnButton"]').click();
                const numPlayers = clientState.otherPlayers.length + 1;
                getNames(clientState.counter, numPlayers, clientState, clientState.turnIndexOffset, clientState.turns);

                // WARNING: if you do anything else after the call to waitForWakeUpTrigger, don't forget
                // that the counter value is now wrong. And you can't increment it back, since that code
                // will be executed before the cy.get() in the new call to waitForWakeUpTrigger.
                clientState.counter = clientState.counter + 1;
                waitForWakeUpTrigger(clientState);
            }
            else if (triggerElement.innerText === 'Start next round, bot!') {
                cy.wait(2000); // Give player who finished the round time to click the scores div
                cy.get('[id="startNextRoundButton"]').click();
                waitForWakeUpTrigger(clientState);
            }
        });
}

function getNames(counter, numPlayers, teamInfoObject, turnIndexOffset, turns) {
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
    for (const move of turnToTake) {
        cy.wait(500);
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

    cy.get('[id="scoresDiv"]').click(); // scroll here to look at scores (for video)
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
export function assert(selector, predicate, { unique = true, describeExpected = 'ok' } = {}) {
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

    cy.contains('.teamlessPlayerLiClass', playerName);
}

function checkTeamlessPlayerList(otherPlayers) {
    for (const player of otherPlayers) {
        cy.contains('.teamlessPlayerLiClass', player, { timeout: 120000 });
    }
}

function setGameParams() {
    // Wait before clicking, to give other players time to verify player list
    cy.wait(1000);

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

    cy.contains('.teamlessPlayerLiClass', playerName);
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