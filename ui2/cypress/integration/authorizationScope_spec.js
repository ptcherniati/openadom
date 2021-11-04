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

        const responseSites= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/references/sites',
            response).as('getSites')

        const responseProjet= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/references/projet',
            response).as('getProjet')
        const responseTypeSites= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/references/type_de_sites',
            response).as('getTypeSites')

        cy.fixture('applications/ore/monsore/dataType_response.json').then(resolveDataTypes)
        cy.fixture('authorisation/monsore/show_authorization_table.json').then(responseAuthorization)

        cy.visit(Cypress.env('monsore_table_authorization_url'))
        cy.wait('@getShowAuthorizations')

        cy.fixture('authorisation/monsore/new_authorization_request.json').then(resolveAuthorization)
        cy.fixture('references/monsore/sites.json').then(responseSites)
        cy.fixture('references/monsore/projet.json').then(responseProjet)
        cy.fixture('references/monsore/type_de_sites.json').then(responseTypeSites)

        cy.visit(Cypress.env('monsore_new_authorization_url'))
        cy.wait(['@getAuthorization','@getDataTypes'])
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')

        cy.get('.buttons > .button').click()
        //cy.fixture('authorisation/monsore/new_authorization_response.json').then(responseAuthorization)
    })
})