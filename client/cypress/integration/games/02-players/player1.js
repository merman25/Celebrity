describe('Player 1', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        cy.get('[id="nameField"]').type('Marvin the Paranoid Android');
        cy.get('[id="nameSubmitButton"]').click();
        cy.get('[id=host]').click();

        cy.get('.teamlessPlayerLiClass').contains('Marvin the Paranoid Android');
        cy.get('.teamlessPlayerLiClass').next({timeout: 60000}).contains('Bender O\'Neill');
    });
});