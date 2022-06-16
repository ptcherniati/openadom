/// <reference types="Cypress" />

import Assert from "assert";

require('cypress-plugin-tab')
const verify = function (req, response) {
    console.log('req', req.body, 'response', response)
    Assert.equal('monsore', req.body.applicationNameOrId)
    Assert.equal('pem', req.body.dataType)
    Assert.equal('88b99c65-e5ca-4aeb-b64d-53203bab5838', req.body.usersId[0])
    Assert.equal("Une authorization", req.body.name)
    Assert.equal('projet_atlantique', req.body.authorizations.extraction[0].requiredAuthorizations.projet)
    Assert.equal('plateforme', req.body.authorizations.extraction[0].requiredAuthorizations.localization)
    req.reply({
        statusCode: 201,
        body: response,
    })
}

const verify2 = function (req, response) {
    console.log('req', req.body, 'response', response)
    Assert.equal('monsore', req.body.applicationNameOrId)
    Assert.equal('pem', req.body.dataType)
    Assert.equal('88b99c65-e5ca-4aeb-b64d-53203bab5838', req.body.usersId[0])
    Assert.equal("Une authorization", req.body.name)
    var extraction = req.body.authorizations.extraction;
    cy.expect(extraction).should('have.length', 1)
    var admin = req.body.authorizations.admin;
    cy.expect(admin).should('have.length', 1)
    Assert.equal('projet_atlantique', extraction[0].requiredAuthorizations.projet)
    Assert.equal('bassin_versant', extraction[0].requiredAuthorizations.localization)
    cy.expect(extraction.dataGroups).should('have.length', 1)
    Assert.equal('referentiel', extraction[0].dataGroups[0])
    Assert.equal(new Date([2021, 1, 1]),new Date( extraction[0].fromDay))
    Assert.equal(new Date([2021, 12, 31]),new Date( extraction[0].toDay))


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

    it('Test authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])

        cy.fixture('applications/ore/monsore/monsoere.json').then(resolveDataTypes)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/show_authorization_table.json').then(responseAuthorization)

        cy.visit(Cypress.env('monsore_table_authorization_url'))
        //cy.get('select').select('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9')
        cy.wait('@getShowAuthorizations')
        cy.wait(10)

        cy.fixture('applications/ore/monsore/datatypes/authorisation/grantable.json').then(resolveAuthorization)
        cy.fixture('applications/ore/monsore/references/sites.json').then(responseSites)
        cy.fixture('applications/ore/monsore/references/projet.json').then(responseProjet)
        cy.fixture('applications/ore/monsore/references/type_de_sites.json').then(responseTypeSites)

        cy.visit(Cypress.env('monsore_new_authorization_url'))
        cy.wait(['@getGrantable', '@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("88b99c65-e5ca-4aeb-b64d-53203bab5838")
        cy.get("input[type=text]").type("Une authorization")
        cy.contains('Projet Atlantique').click()
        cy.get("div[field=extraction] span.icon").eq(2).click()
        cy.get('div.rows > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(5) > .field > .icon').get(['data-icon=minus-square'])
        cy.contains('Plateforme').click()
        cy.get("[data-icon=check-square]").should('have.length', 4)
        cy.get("[data-icon=minus-square]").should('have.length', 1)
        cy.get("[data-icon=square]").should('have.length', 30)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization)
        cy.get('.buttons > .button').click()
        cy.wait(100)
        cy.contains('Ma première authorization')
        cy.contains('[ "depot", "extraction" ]')
        cy.contains('[ "poussin" ]')
    })


    it('Test une autre authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])

        cy.fixture('applications/ore/monsore/monsoere.json').then(resolveDataTypes)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/show_authorization_table.json').then(responseAuthorization)

        cy.visit(Cypress.env('monsore_table_authorization_url'))
        //cy.get('select').select('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9')
        cy.wait('@getShowAuthorizations')
        cy.wait(10)

        cy.fixture('applications/ore/monsore/datatypes/authorisation/grantable.json').then(resolveAuthorization)
        cy.fixture('applications/ore/monsore/references/sites.json').then(responseSites)
        cy.fixture('applications/ore/monsore/references/projet.json').then(responseProjet)
        cy.fixture('applications/ore/monsore/references/type_de_sites.json').then(responseTypeSites)

        cy.visit(Cypress.env('monsore_new_authorization_url'))
        cy.wait(['@getGrantable', '@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("88b99c65-e5ca-4aeb-b64d-53203bab5838")
        cy.get("input[type=text]").type("Une authorization")
        cy.get(':nth-child(2) > .columns > :nth-child(5) > .field > .icon').first().click()
        cy.contains('Projet Atlantique').click()
        cy.get(':nth-child(1) > .columns > :nth-child(5) > .column > .field-body > .field > .icon.is-medium').first().click()
        cy.contains('Plateforme').click()
        cy.get("[data-icon=check-square]").should('have.length', 4)
        cy.get("[data-icon=minus-square]").should('have.length', 1)
        cy.get("[data-icon=square]").should('have.length', 30)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization)
        cy.get('.buttons > .button').click()
        cy.contains('Ma première authorization ')
        cy.contains('[ "depot", "extraction" ]')
        cy.contains('[ "poussin" ]')
    })
    it('Test une autre authorization monsore pem', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])

        cy.fixture('applications/ore/monsore/monsoere.json').then(resolveDataTypes)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/show_authorization_table.json').then(responseAuthorization)

        cy.visit(Cypress.env('monsore_table_authorization_url'))
        //cy.get('select').select('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9')
        cy.wait('@getShowAuthorizations')
        cy.wait(10)

        cy.fixture('applications/ore/monsore/datatypes/authorisation/grantable.json').then(resolveAuthorization)
        cy.fixture('applications/ore/monsore/references/sites.json').then(responseSites)
        cy.fixture('applications/ore/monsore/references/projet.json').then(responseProjet)
        cy.fixture('applications/ore/monsore/references/type_de_sites.json').then(responseTypeSites)

        cy.visit(Cypress.env('monsore_new_authorization_url'))
        cy.wait(['@getGrantable', '@getMonsoere'])
        cy.wait(100)
        cy.get('.title.main-title').first().contains('Nouvelle autorisation pour Piégeage en Montée')
        cy.get("select").select("88b99c65-e5ca-4aeb-b64d-53203bab5838")
        cy.get("input[type=text]").type("Une authorization")
        cy.get(':nth-child(2) > .columns > :nth-child(2) > .field > .icon').first().click()
        cy.contains('Projet Atlantique').click()
        cy.get(':nth-child(1) > .columns > :nth-child(5) > .field > .icon').first().click()
        cy.get('.tooltip-trigger > .icon').first().click()

        cy.get('.autocomplete > .control > .input').click()
        cy.get('.dropdown-content > :nth-child(3)').click().tab()
        cy.get('.autocomplete > .control > .input').click().tab()
        cy.get(':nth-child(2) > .datepicker > .dropdown > .dropdown-trigger > .control > .input').type('2021/01/01').tab()
        cy.get(':nth-child(3) > .datepicker > .dropdown > .dropdown-trigger > .control > .input').type('2021/12/31').tab().type('{esc}')
        cy.wait(100)
        cy.fixture('applications/ore/monsore/datatypes/authorisation/post_authorization.json').then(postAuthorization2)
        cy.get('.buttons > .button').click()
    })
})