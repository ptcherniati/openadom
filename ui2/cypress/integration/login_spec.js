describe('test login', () => {
    it('login good User', () => {
        const loginPoussin = 'login_poussin.json'
        cy.intercept('POST', 'http://localhost:8081/api/v1/login', { fixture: loginPoussin }).as('userSuccess')

        cy.visit(Cypress.env('login_url'))
        cy.wait(5000)
        cy.userLogin({ login: "poussin", password: "xxxxxxxx" })
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.navbar-burger').click()
        cy.get('.buttons button').contains("Se déconnecter").click()
    })
})
