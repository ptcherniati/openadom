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
            response).as('getMonsoere')

        const resolveAuthorization= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/dataType/pem/grantable',
            response).as('getGrantable')

        const responseAuthorization= response => cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/dataType/pem/authorization',
            response).as('getShowAuthorizations')

        const postAuthorization= response => cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/applications/monsore/dataType/pem/authorization',
            response).as('getPostAuthorizations')

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

        cy.fixture('applications/ore/monsore/monsoere.json').then(resolveDataTypes)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/show_authorization_table.json').then(responseAuthorization)

        cy.visit(Cypress.env('monsore_table_authorization_url'))
        //cy.get('select').select('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9')
        cy.wait('@getShowAuthorizations')
        cy.wait(10)

        cy.fixture('applications/ore/monsore/datatypes/authorisation/new_authorization_request.json').then(resolveAuthorization)
        cy.fixture('applications/ore/monsore/references/sites.json').then(responseSites)
        cy.fixture('applications/ore/monsore/references/projet.json').then(responseProjet)
        cy.fixture('applications/ore/monsore/references/type_de_sites.json').then(responseTypeSites)

        cy.visit(Cypress.env('monsore_new_authorization_url'))
        cy.wait(['@getGrantable','@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("8b48a812-7da7-462a-8012-3e93b696d14b")
        cy.get("input[type=text]").type("Une authorization")
        cy.contains('Projet Atlantique').click()
        cy.get("div[field=extraction] span.icon").eq(2).click()
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization)
        cy.get('.buttons > .button').click()
    })
})