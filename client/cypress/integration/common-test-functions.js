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

export function playGame(playerName, iAmHosting, hostName, gameID, otherPlayers, celebrityNames) {
    if (iAmHosting) {
        startHostingNewGame(playerName, gameID);
    }
    else {
        cy.wait(10000);
        joinGame(playerName, gameID, hostName);
    }
    checkTeamlessPlayerList(otherPlayers);
    if (iAmHosting) {
        setGameParams(playerName);
    }
    checkGameParams();
    if (iAmHosting) {
        allocateTeams();
    }
    else {
        checkHostControlsAreNotVisible();
    }

    checkTeamList(playerName, otherPlayers);

    if (iAmHosting) {
        requestNames();
    }
    else {
        cy.get('[id="nameListForm"]').should('be.visible');
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 1 player(s)', { timeout: 60000 });
    }

    submitNames(celebrityNames);

    if (iAmHosting) {
        startGame();
        cy.get('[id="startTurnButton"]').should('not.be.visible');

        // Alexander the Great, Marilyn Monroe, Audrey Hepburn, Paul McCartney, Rosa Parks, Archimedes, Hypatia, Helen of Troy, Neil Armstrong, Xerxes, Hippolyta, John F. Kennedy

        cy.get('[id="startNextRoundButton"]').click({ timeout: 60000 });
        startTurnAndGetAllNames();
        cy.get('[id="startNextRoundButton"]').click();
        }
    else {
        cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 0 player(s)', {timeout: 60000});
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="startNextRoundButton"]').should('not.be.visible');
        
        startTurnAndGetAllNames();
        waitForMyTurn(60);
        startTurnAndGetAllNames();
    }
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
        cy.contains('.teamlessPlayerLiClass', player, { timeout: 60000 });
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

export function checkTeamList(playerName, otherPlayers) {
    cy.get('[id="teamList"]').contains('Teams');
    cy.get('[id="teamList"]').contains(playerName);

    for (const player of otherPlayers) {
        cy.get('[id="teamList"]').contains(player);
    }
}

export function requestNames() {
    cy.get('[id="requestNamesButton"]').click();
    cy.get('[id="teamsButton"]').should('not.be.visible');
    cy.get('[id="requestNamesButton"]').should('not.be.visible');

    cy.get('[id="nameListForm"]').should('be.visible');
    cy.get('[id="startGameButton"]').should('not.be.visible');
    cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 2 player(s)');
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

export function startTurnAndGetAllNames() {
    cy.get('[id="startTurnButton"]').click();

    for (let i = 0; i < 12; i++) {
        cy.get('[id="gotNameButton"]').click();
    }
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
    cy.contains('[id="gameStatusDiv"]', 'It\'s your turn!', {timeout: 1000*aTimeoutInSeconds});
}