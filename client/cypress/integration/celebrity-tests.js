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

describe('Check initial visibility', () => {
  it('Start Page', () => {
    cy.visit('http://192.168.1.17:8080/celebrity.html');

    // divs
    assert('[id="instructions"]', isVisible, { describeExpected: 'visible' });
    assert('[id="divChooseName"]', isVisible, { describeExpected: 'visible' });
    assert('[id="divJoinOrHost"]', not(isVisible), { describeExpected: 'invisible' });

    cy.get('[id="divChooseName"').should('be.visible');
    cy.get('[id="divJoinOrHost"').should('not.be.visible');

    assert('[id="gameIDDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });
    assert('[id="gameInfoDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });

    assert('[id="hostGameDiv"]', not(isVisible), { describeExpected: 'invisible' });
    assert('[id="playGameDiv"]', not(isVisible), { describeExpected: 'invisible' });

    assert('[id="teamlessPlayerContextMenuDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });
    assert('[id="playerInTeamContextMenuDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });

    // fields
    assert('[id="nameField"]', isVisible, { describeExpected: 'visible' });
    assert('[id="nameSubmitButton"]', isVisible, { describeExpected: 'visible' });
    assert('[id="gameIDField"]', not(isVisible), { describeExpected: 'invisible' });
    
    // buttons
    cy.get('[id="join"]').should('not.be.visible');
    cy.get('[id="host"]').should('not.be.visible');

  });

  it('Enter Name', () => {
    // Need to visit again inside this 'it', otherwise the session cookie isn't set any more
    cy.visit('http://192.168.1.17:8080/celebrity.html');

    cy.get('[id="nameField"]')
    .type('Otto von Testmark');

    cy.get('[id="nameSubmitButton"]')
    .click();
    // cy.get('[id="nameForm"]')
    // .submit();

    // divs
    assert('[id="instructions"]', isVisible, { describeExpected: 'visible' });
    assert('[id="divChooseName"]', not(isVisible), { describeExpected: 'invisible' });
    assert('[id="divJoinOrHost"]', isVisible, { describeExpected: 'visible' });

    assert('[id="gameIDDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });
    assert('[id="gameInfoDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });

    assert('[id="hostGameDiv"]', not(isVisible), { describeExpected: 'invisible' });
    assert('[id="playGameDiv"]', not(isVisible), { describeExpected: 'invisible' });

    assert('[id="teamlessPlayerContextMenuDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });
    assert('[id="playerInTeamContextMenuDiv"]', and(isVisible, not(hasContent)), { describeExpected: 'visible and empty' });

    // fields
    assert('[id="nameField"]', not(isVisible), { describeExpected: 'invisible' });
    assert('[id="nameSubmitButton"]', not(isVisible), { describeExpected: 'invisible' });
    assert('[id="gameIDField"]', not(isVisible), { describeExpected: 'invisible' });

    // buttons
    cy.get('[id="join"]').should('be.visible');
    cy.get('[id="host"]').should('be.visible');
  });

  it('Host Game', () => {
    // Need to visit again inside this 'it', otherwise the session cookie isn't set any more
    cy.visit('http://192.168.1.17:8080/celebrity.html');

    cy.get('[id="nameField"]')
    .type('Marvin the Paranoid Android');

    cy.get('[id="nameSubmitButton"]')
    .click();

    cy.get('[id="host"]').click();
  });
})