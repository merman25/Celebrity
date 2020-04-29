import * as common from "./common-test-functions";

describe('Check initial visibility', () => {
  it('Start Page', () => {
    cy.visit('http://192.168.1.17:8080/celebrity.html');

    // divs
    common.assert('[id="instructions"]', common.isVisible, { describeExpected: 'visible' });
    common.assert('[id="divChooseName"]', common.isVisible, { describeExpected: 'visible' });
    common.assert('[id="divJoinOrHost"]', common.not(common.isVisible), { describeExpected: 'invisible' });

    cy.get('[id="divChooseName"').should('be.visible');
    cy.get('[id="divJoinOrHost"').should('not.be.visible');

    common.assert('[id="gameIDDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });
    common.assert('[id="gameInfoDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });

    common.assert('[id="hostGameDiv"]', common.not(common.isVisible), { describeExpected: 'invisible' });
    common.assert('[id="playGameDiv"]', common.not(common.isVisible), { describeExpected: 'invisible' });

    common.assert('[id="teamlessPlayerContextMenuDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });
    common.assert('[id="playerInTeamContextMenuDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible common.and empty' });

    // fields
    common.assert('[id="nameField"]', common.isVisible, { describeExpected: 'visible' });
    common.assert('[id="nameSubmitButton"]', common.isVisible, { describeExpected: 'visible' });
    common.assert('[id="gameIDField"]', common.not(common.isVisible), { describeExpected: 'invisible' });

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
    common.assert('[id="instructions"]', common.isVisible, { describeExpected: 'visible' });
    common.assert('[id="divChooseName"]', common.not(common.isVisible), { describeExpected: 'invisible' });
    common.assert('[id="divJoinOrHost"]', common.isVisible, { describeExpected: 'visible' });

    common.assert('[id="gameIDDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });
    common.assert('[id="gameInfoDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });

    common.assert('[id="hostGameDiv"]', common.not(common.isVisible), { describeExpected: 'invisible' });
    common.assert('[id="playGameDiv"]', common.not(common.isVisible), { describeExpected: 'invisible' });

    common.assert('[id="teamlessPlayerContextMenuDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });
    common.assert('[id="playerInTeamContextMenuDiv"]', common.and(common.isVisible, common.not(common.hasContent)), { describeExpected: 'visible and empty' });

    // fields
    common.assert('[id="nameField"]', common.not(common.isVisible), { describeExpected: 'invisible' });
    common.assert('[id="nameSubmitButton"]', common.not(common.isVisible), { describeExpected: 'invisible' });
    common.assert('[id="gameIDField"]', common.not(common.isVisible), { describeExpected: 'invisible' });

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