describe('test login', () => {
    it('login good User', () => {
        cy.intercept('POST', 'http://localhost:8081/api/v1/login', { fixture: 'login_poussin.json' }).as('userSuccess')

        cy.visit('/')
        cy.get('input[placeholder = "Entrer l\'identifiant"]').type('poussin')
        cy.get('input[placeholder = "Entrer le mot de passe"]').type('xxxxxxxx')
        cy.get('.buttons button').contains(" Se connecter ").click()
    })
})
