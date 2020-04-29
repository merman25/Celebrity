function isVisible(element) {
    /* According to https://stackoverflow.com/a/21696585,
     * a more complete test is window.getComputedStyle(el) !== 'none';
     * But this is also expected to be slower, and only necessary for 'position: fixed' elements.
     * 
     * UPDATE: checking getComputedStyle is anyway incorrect by itself, because it doesn't check
     * the style of parent elements
    */
    return /* window.getComputedStyle(element).display !== 'none'; */ element.offsetParent !== null;
}

function hasContent(element) {
    const content = element.innerHTML.trim();
    return content !== '';
}

function not(predicate) {
    return x => !predicate(x);
}

function and(...predicates) {
    return function (x) {
        for (let i = 0; i < predicates.length; i++) {
            if (!predicates[i](x)) {
                return false;
            }
        }

        return true;
    }
}

function assert(selector, predicate, {
    unique = true,
    describeExpected = 'ok'
} = {}) {
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

describe('Player 2', () => {
    it('Plays a game', () => {
        cy.window()
            .then(window => {
                window.resizeTo(800, 600);
                window.moveTo(500, 500);
            });

        cy.visit('http://192.168.1.17:8080/celebrity.html');


        cy.get('[id="nameField"]').type('Bender O\'Neill');
        cy.get('[id="nameSubmitButton"]').click();
        cy.get('[id="join"]').click();
        cy.get('[id="gameIDField"]').type('4795');
        cy.get('[id="gameIDSubmitButton"]').click();

        cy.get('[id="gameParamsForm"]').should('not.be.visible')
        cy.get('[id="gameIDDiv"').should('be.visible')
        cy.get('[id="gameParamsDiv"]').contains('Marvin the Paranoid Android is hosting.');
        cy.get('[id="gameParamsDiv"]').contains('Settings').should('not.exist');

        cy.get('[id="gameIDDiv"]').contains('Game ID: 4795')
        cy.get('[id="gameInfoDiv"]').contains('Waiting for others to join...');

        assert('[id="teamList"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });

        cy.get('[id="gameParamsDiv"]').contains('Settings');
        cy.get('[id="gameParamsDiv"]').contains('Rounds: 3');
        cy.get('[id="gameParamsDiv"]').contains('Round duration (sec): 60');

        cy.get('[id="teamsButton"]').should('not.be.visible');
        cy.get('[id="requestNamesButton"]').should('not.be.visible');

        cy.get('[id="teamList"]').contains('Teams');
        cy.get('[id="teamList"]').contains('Marvin the Paranoid Android');
        cy.get('[id="teamList"]').contains('Bender O\'Neill');

        cy.get('[id="nameListForm"]').should('be.visible');
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 1 player(s)', {timeout: 60000});

        cy.get('[id="name1"]').type('Hippolyta');
        cy.get('[id="name2"]').type('Alexander the Great');
        cy.get('[id="name3"]').type('Hypatia');
        cy.get('[id="name4"]').type('Xerxes');
        cy.get('[id="name5"]').type('Helen of Troy');
        cy.get('[id="name6"]').type('Archimedes');
        cy.get('[id="submitNamesButton"]').click();

        cy.get('[id="gameStatusDiv"]').contains('Waiting for names from 0 player(s)', {timeout: 60000});
        cy.get('[id="startGameButton"]').should('not.be.visible');
        cy.get('[id="startNextRoundButton"]').should('not.be.visible');
        cy.get('[id="startTurnButton"]').click();

        for(let i=1; i<=12; i++) {
            cy.get('[id="gotNameButton"]').click();
        }

        cy.get('[id="startTurnButton"]').click({timeout: 60000});
        for(let i=1; i<=12; i++) {
            cy.get('[id="gotNameButton"]').click();
        }

    });
});