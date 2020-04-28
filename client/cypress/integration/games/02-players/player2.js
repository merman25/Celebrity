describe('Player 2', () => {
    it('Plays a game', () => {
        cy.visit('http://192.168.1.17:8080/celebrity.html');

        cy.get('[id="nameField"]').type('Bender O\'Neill');
        cy.get('[id="nameSubmitButton"]').click();
        cy.get('[id=join]').click();
        cy.get('[id=gameIDField]').type('4795');
        cy.get('[id=gameIDSubmitButton]').click();
    });
});