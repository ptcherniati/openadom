/// <reference types="Cypress" />

import Assert from "assert";

const verify = function (req, response) {
    console.log('req', req.body, 'response', response)
    Assert.equal('monsore', req.body.applicationNameOrId)
    Assert.equal('pem', req.body.dataType)
    Assert.equal('8b48a812-7da7-462a-8012-3e93b696d14b', req.body.usersId[0])
    Assert.equal("Une authorization", req.body.name)
    Assert.equal('projet_atlantique', req.body.authorizations.extraction[0].requiredauthorizations.projet)
    cy.expect(req.body.authorizations.extraction[0].requiredauthorizations.localization).to.be.undefined
    req.reply({
        statusCode: 201,
        body: response,
    })
}

const verify2 = function (req, response) {
    console.log('req2', req.body, 'response2', response)
    Assert.equal('monsore', req.body.applicationNameOrId)
    Assert.equal('pem', req.body.dataType)
    Assert.equal('8b48a812-7da7-462a-8012-3e93b696d14b', req.body.usersId[0])
    Assert.equal("Une authorization", req.body.name)
    Assert.equal('referentiel', req.body.authorizations.extraction[0].dataGroup[0])
    Assert.equal('projet_atlantique', req.body.authorizations.extraction[0].requiredauthorizations.projet)
    Assert.equal('plateforme', req.body.authorizations.extraction[0].requiredauthorizations.localization)
    req.reply({
        statusCode: 201,
        body: response,
    })
}

const resolveDataTypes = response => cy.intercept(
    'GET',
    'http://localhost:8081/api/v1/applications/monsore',
    response).as('getMonsoere')

const resolveAuthorization = response => cy.intercept(
    'GET',
    'http://localhost:8081/api/v1/applications/monsore/dataType/pem/grantable',
    response).as('getGrantable')

const responseAuthorization = response => cy.intercept(
    'GET',
    'http://localhost:8081/api/v1/applications/monsore/dataType/pem/authorization',
    response).as('getShowAuthorizations')

const postAuthorization = response => cy.intercept(
    'POST',
    'http://localhost:8081/api/v1/applications/monsore/dataType/pem/authorization',
    (req) => verify(req, response)
).as('getPostAuthorizations')

const postAuthorization2 = response => cy.intercept(
    'POST',
    'http://localhost:8081/api/v1/applications/monsore/dataType/pem/authorization',
    (req) => verify2(req, response)
).as('getPostAuthorizations')

const responseSites = response => cy.intercept(
    'GET',
    'http://localhost:8081/api/v1/applications/monsore/references/sites',
    response).as('getSites')

const responseProjet = response => cy.intercept(
    'GET',
    'http://localhost:8081/api/v1/applications/monsore/references/projet',
    response).as('getProjet')
const responseTypeSites = response => cy.intercept(
    'GET',
    'http://localhost:8081/api/v1/applications/monsore/references/type_de_sites',
    response).as('getTypeSites')
describe('test authorization application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    /*it('Test authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])

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
        cy.wait(['@getGrantable', '@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("8b48a812-7da7-462a-8012-3e93b696d14b")
        cy.get("input[type=text]").type("Une authorization")
        cy.contains('Projet Atlantique').click()
        cy.get("div[field=extraction] span.icon").eq(2).click()
        cy.get('div.rows > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').get(['data-icon=minus-square'])
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().click()
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get(':nth-child(1) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().click()
        cy.get(':nth-child(1) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get('div.rows > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').get(['data-icon=check-square'])
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization)
        cy.get('.buttons > .button').click()
    })*/



   /* it('Test une autre authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])

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
        cy.wait(['@getGrantable', '@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("8b48a812-7da7-462a-8012-3e93b696d14b")
        cy.get("input[type=text]").type("Une authorization")
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().click()
        cy.contains('Projet Atlantique').click()
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get(':nth-child(1) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get('div.rows > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').get(['data-icon=check-square'])
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization)
        cy.get('.buttons > .button').click()
    })*/
    it('Test une autre authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])

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
        cy.wait(['@getGrantable', '@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("8b48a812-7da7-462a-8012-3e93b696d14b")
        cy.get("input[type=text]").type("Une authorization")
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().click()
        cy.contains('Projet Atlantique').click()
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get(':nth-child(1) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get('div.rows > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').get(['data-icon=check-square'])
        cy.get(':nth-child(1) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().click()
        cy.get(':nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=square'])
        cy.get(':nth-child(1) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').first().get(['data-icon=check-square'])
        cy.get('div.rows > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2) > .field > .column > .icon').get(['data-icon=minus-square'])
        cy.get('.autocomplete > .control > .input').click()
        cy.get('.dropdown-content > :nth-child(3) > span').click()
        cy.wait(100)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization2)
        cy.get('.buttons > .button').click()
    })
})