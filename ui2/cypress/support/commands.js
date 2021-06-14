// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })
import 'cypress-file-upload';


Cypress.Commands.add('login', (userRole) => {
    localStorage.clear()
    cy.fixture('users.json').as('users')
    cy.get('@users').then((users) => {
        const user = users[userRole]
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > .input').type(userRole)
        cy.get(':nth-child(2) > .field > .control > .input').type("password")

        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', user.response)
        cy.get('.buttons button').contains(" Se connecter ").click()
    })
})