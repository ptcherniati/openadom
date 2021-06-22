/// <reference types="Cypress" />

describe('test login', () => {

    it('login visitor', () => {
        cy.login("visitor")
        cy.url().should('include', '/login')

    })
    it('login admin', () => {
        cy.login("admin", ['applications/acbb/acbb_application_description.json'])
        cy.url().should('include', '/application')
        cy.get('[type=button').contains(" Créer l'application ")

    })
})