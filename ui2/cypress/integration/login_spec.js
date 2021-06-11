/// <reference types="Cypress" />

describe('test login', () => {

    it('login visitor', () => {
        cy.login("visitor")
        cy.url().should('include', '/login')

    })
    it('login admin', () => {
        cy.login("admin")
        cy.url().should('include', '/application')

    })
})
