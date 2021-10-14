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
        cy.fixture('applications/ore/monsore/dataType_response.json').then(resolveDataTypes)
        cy.fixture('applications/ore/monsore/authorization.json').then(resolveAuthorization)
        cy.visit(Cypress.env('application_new_authorization_url'))
        cy.wait(['@getAuthorization','@getDataTypes'])
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get(':nth-child(2) > .collapse-trigger > .card-header > .card-header-title').first().click()
        cy.get(':nth-child(1) > .CollapsibleTree-header.clickable > :nth-child(1) > .CollapsibleTree-header-infos > .b-checkbox > .check').click()
    })
})