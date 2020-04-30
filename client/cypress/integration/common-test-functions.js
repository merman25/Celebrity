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

export function playGame(index, playerName, iAmHosting, hostName, gameID, otherPlayers, celebrityNames, turns) {
    if (iAmHosting) {
        startHostingNewGame(playerName, gameID);
    }
    else {
        joinGame(playerName, gameID, hostName);
    }

    // cy.window().then(window => {
    //     window.resizeTo(800, 600);
    //     window.moveTo(index * 200, 0);
    // });

    checkTeamlessPlayerList(otherPlayers);
    if (iAmHosting) {
        setGameParams(playerName);
    }
    else {
        cy.wait(1000);
    }

    checkGameParams();
    if (iAmHosting) {
        cy.wait(1000);
        allocateTeams();
    }
    else {
        checkHostControlsAreNotVisible();
    }

    const teamInfoObject = {};
    checkTeamList(playerName, otherPlayers, teamInfoObject);

    if (iAmHosting) {
        requestNames();
    }
    cy.get('[id="nameListForm"]').should('be.visible');
    cy.get('[id="startGameButton"]').should('not.be.visible');

    submitNames(celebrityNames);

    if (iAmHosting) {
        startGame();
    }
    else {
    }

    const numPlayers = (otherPlayers.length + 1);
    const numNames = 6 * numPlayers;
    waitForWakeUpTrigger(numNames, numPlayers, 0, teamInfoObject, turns);
}

// wait for wake up trigger
// when it appears, check game status. If game over, end.
// If not, wait for clickable button and click it.
// Check if the button I just clicked was the start turn button. If yes, play my turn.
// Goto 1.

function waitForWakeUpTrigger(numNames, numPlayers, counter, teamInfoObject, turns) {
    cy.get('.testTriggerClass', { timeout: 120000 })
        .then(elements => {
            const triggerElement = elements[0];
            console.log(`found test trigger on ${triggerElement.id}`);
            if (triggerElement.innerText === 'You\'re done, bot!') {
                console.log('Finished!!');
            }
            else if (triggerElement.innerText === 'Start turn, bot!') {
                cy.get('[id="startTurnButton"]').click();
                // getAllNames(numNames);
                getNames(counter, numPlayers, teamInfoObject, turns);
                waitForWakeUpTrigger(numNames, numPlayers, counter + 1, teamInfoObject, turns);
            }
            else if (triggerElement.innerText === 'Start next round, bot!') {
                cy.wait(1000); // Give player who finished the round time to click the scores div
                cy.get('[id="startNextRoundButton"]').click();
                waitForWakeUpTrigger(numNames, numPlayers, counter, teamInfoObject, turns);
            }
        });
}

function getNames(counter, numPlayers, teamInfoObject, turns) {
    cy.scrollTo(0, 0);
    // cy.wait(5000); // so I can follow it
    const numTeams = 2;
    const turnIndex = counter * numPlayers + numTeams * teamInfoObject.playerIndex + teamInfoObject.teamIndex;
    console.log(`counter ${counter}, numPlayers ${numPlayers}, teamIndex ${teamInfoObject.teamIndex}, playerIndex ${teamInfoObject.playerIndex} ==> turnIndex ${turnIndex}`);

    const turnToTake = turns[turnIndex];

    // {force: true} disables scrolling, which is nice for videos but not
    // what we want in a real test. When doing {force: true}, we need the
    // should('be.visible') to make sure we at least wait for the button to appear.
    cy.get('[id="gotNameButton"]').should('be.visible');
    cy.get('[id="passButton"]').should('be.visible');
    cy.get('[id="endTurnButton"]').should('be.visible');
    for (let i = 0; i < turnToTake.length; i++) {
        cy.wait(500);
        let move = turnToTake[i];
        if (move === 'got-it') {
            cy.get('[id="gotNameButton"]').click({ force: true });
        }
        else if (move === 'pass') {
            cy.get('[id="passButton"]').click({ force: true });
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

export function startHostingNewGame(playerName, gameID) {
    cy.get('[id="nameField"]').type(playerName);
    cy.get('[id="nameSubmitButton"]').click();
    cy.wait(1000);
    cy.get('[id=host]').click();

    cy.get('[id="gameParamsForm"]').should('be.visible')
    cy.get('[id="gameIDDiv"]').should('be.visible')
    cy.get('[id="gameParamsDiv"]').contains('You\'re the host.');
    cy.get('[id="gameParamsDiv"]').contains('Settings').should('not.exist');

    if (gameID) {
        cy.get('[id="gameIDDiv"]').contains(`Game ID: ${gameID}`)
    }
    cy.get('[id="gameInfoDiv"]').contains('Waiting for others to join...');

    cy.contains('.teamlessPlayerLiClass', playerName);
}

export function checkTeamlessPlayerList(otherPlayers) {
    for (const player of otherPlayers) {
        cy.contains('.teamlessPlayerLiClass', player, { timeout: 120000 });
    }
}

export function setGameParams() {
    // Wait before clicking, to give other players time to verify player list
    cy.wait(1000);

    cy.get('[id="submitGameParamsButton"]').click();
    cy.get('[id="gameParamsForm"]').should('not.be.visible');
}

export function allocateTeams() {
    cy.get('[id="teamsButton"]').click();
    cy.get('[id="teamsButton"]').should('be.visible');
}

export function checkGameParams() {
    cy.get('[id="gameParamsDiv"]').contains('Settings');
    cy.get('[id="gameParamsDiv"]').contains('Rounds: 3');
    cy.get('[id="gameParamsDiv"]').contains('Round duration (sec): 60');

    assert('[id="teamList"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });
}

export function checkHostControlsAreNotVisible() {
    cy.get('[id="teamsButton"]').should('not.be.visible');
    cy.get('[id="requestNamesButton"]').should('not.be.visible');
}

export function checkTeamList(playerName, otherPlayers, teamInfoObjectFCT) {
    cy.get('[id="teamList"]').contains('Teams');
    cy.get('[id="teamList"]').contains(playerName);

    for (const player of otherPlayers) {
        cy.get('[id="teamList"]').contains(player);
    }

    cy.get('.playerInTeamTDClass').contains(playerName)
        .then(elements => {
            const element = elements[0];
            teamInfoObjectFCT.teamIndex = parseInt(element.getAttribute('teamindex'));
            teamInfoObjectFCT.playerIndex = parseInt(element.getAttribute('playerindex'));
        });
}

export function requestNames() {
    cy.get('[id="requestNamesButton"]').click();
    cy.get('[id="teamsButton"]').should('not.be.visible');
    cy.get('[id="requestNamesButton"]').should('not.be.visible');

    cy.get('[id="nameListForm"]').should('be.visible');
    cy.get('[id="startGameButton"]').should('not.be.visible');
}

export function submitNames(celebrityNames) {
    for (let i = 0; i < celebrityNames.length; i++) {
        cy.get(`[id="name${i + 1}"]`).type(celebrityNames[i]);
    }
    cy.get('[id="submitNamesButton"]').click();
}

export function startGame() {
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
    cy.get('[id="join"]').click();
    cy.get('[id="gameIDField"]').type(gameID);
    cy.get('[id="gameIDSubmitButton"]').click();

    cy.get('[id="gameParamsForm"]').should('not.be.visible')
    cy.get('[id="gameIDDiv"').should('be.visible')

    cy.get('[id="gameParamsDiv"]').contains(`${hostName} is hosting.`);
    cy.get('[id="gameParamsDiv"]').contains('Settings').should('not.exist');

    cy.get('[id="gameIDDiv"]').contains(`Game ID: ${gameID}`)
    cy.get('[id="gameInfoDiv"]').contains('Waiting for others to join...');

    cy.contains('.teamlessPlayerLiClass', playerName);
}

export function waitForMyTurn(aTimeoutInSeconds) {
    cy.contains('[id="gameStatusDiv"]', 'It\'s your turn!', { timeout: 1000 * aTimeoutInSeconds });
}