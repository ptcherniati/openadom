/// <reference types="Cypress" />

describe('test authorization application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it('Test authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])

        const resolveDataTypes = response =>cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore', response)
        const resolveAuthorization= (response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/dataType/pem/grantable', response))
        cy.fixture('applications/ore/monsore/dataType_response.json').then(resolveDataTypes)
        const monsore = cy.fixture('applications/ore/monsore/authorization.json').then(resolveAuthorization)
        cy.visit(Cypress.env('application_new_authorization_url'))
    })
})