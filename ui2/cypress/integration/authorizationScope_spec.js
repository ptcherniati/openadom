/// <reference types="Cypress" />

describe('test authorization application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it('Test authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse','@getApplicationResponse'])

        const resolveDataTypes = response =>cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore',
            response).as('getDataTypes')

        const resolveAuthorization= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/dataType/pem/grantable',
            response).as('getAuthorization')

        const responseAuthorization= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/dataType/pem/authorization',
            response).as('getShowAuthorizations')

        cy.fixture('applications/ore/monsore/dataType_response.json').then(resolveDataTypes)
        cy.fixture('authorisation/monsore/show_authorization_table.json').then(responseAuthorization)

        cy.visit(Cypress.env('monsore_table_authorization_url'))
        cy.wait('@getShowAuthorizations')

        cy.fixture('authorisation/monsore/new_authorization_request.json').then(resolveAuthorization)

        cy.visit(Cypress.env('monsore_new_authorization_url'))
        cy.wait(['@getAuthorization','@getDataTypes'])
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')

        cy.get('.buttons > .button').click()
        //cy.fixture('authorisation/monsore/new_authorization_response.json').then(responseAuthorization)
    })
})