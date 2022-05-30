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

Cypress.Commands.add('setLocale', (locale) => {
    Cypress.on('window:before:load', window => {
        Object.defineProperty(window.navigator, 'language', {value: locale});
    });
})
Cypress.Commands.add('login', (userRole, applications) => {
    localStorage.clear()
    let applicationsResponse = []
    if (applications && applications instanceof Array) {
        console.log(applications);
        cy.fixture(applications[0]).as("appli").then((app) => {
            applicationsResponse = app
        })
    }
    cy.setLocale('fr');
    cy.fixture('users/users.json').as('users')
    cy.get('@users').then((users) => {
        const user = users[userRole]
        console.log(userRole, user)
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type(userRole)
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")

        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', user.response).as('postUserResponse')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications', applicationsResponse).as('getApplicationResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
    })
})