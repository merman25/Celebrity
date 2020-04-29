describe('Player 1', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        cy.get('[id="nameField"]').type('Marvin the Paranoid Android');
        cy.get('[id="nameSubmitButton"]').click();
        cy.wait(1000);
        cy.get('[id=host]').click();

        cy.get('.testClass_teamless0').contains('Marvin the Paranoid Android');
        cy.get('.testClass_teamless1', {timeout: 60000}).contains('Bender O\'Neill');
    });
});