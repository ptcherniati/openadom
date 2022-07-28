/// <reference types="Cypress" />


require('cypress-plugin-tab')
import Assert from "assert";
const test1= function (response){
    Assert.equal(true, parseInt(response.request.body.match('66b3cbb7-2f3f-4db0-b63a-856ad7c2f006'))>0)
}
const test2= function (response){
    Assert.equal('other', response.request.body.match('other'))
    Assert.equal(true, parseInt(response.request.body.match('66b3cbb7-2f3f-4db0-b63a-856ad7c2f006'))>0)
}
const test3= function (response){
    Assert.equal(true, parseInt(response.request.body.match('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'))>0)
}
const test4= function (response){
    Assert.equal(true, parseInt(response.request.body.match('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'))>0)
    Assert.equal('titi', response.request.body.match('titi'))
}
describe('test high authorization application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it.skip('Test authorization ajout', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        cy.get('.clickable').click()
        cy.get('.navbar-burger').click()
        cy.contains('poussin').click()
        cy.wait(50)
        cy.fixture('users/authorizations/highAuthorizations.json').then(authorization=>{
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/authorization',
                authorization
            )
        })
        let user = {
            "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
            "login": "poussin",
            "authorizedForApplicationCreation": true,
            "superadmin": true,
            "authorizations": [
                ".*"
            ]
        }
        cy.contains('Autorisations').click()
        cy.wait(50)
        cy.contains('lambda')
        cy.contains('poussin')
        cy.get(':nth-child(1) > [data-label="Administration"] > .b-checkbox > .check').first().click(   )
        cy.get(':nth-child(1) > [data-label="Applications"] > .taginput > .taginput-container > .autocomplete > .control > .input').type('other').type('{enter}')
        cy.intercept('PUT','http://localhost:8081/api/v1//authorization/superadmin', user).as('superadmin')
        cy.intercept('PUT','http://localhost:8081/api/v1//authorization/applicationCreator',user).as('applicationCreator')
        cy.get('.button > :nth-child(2)').click()
        cy.wait('@superadmin').then(test1)
        cy.wait('@applicationCreator').then(test2)
    })

    it('Test authorization suppression', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        cy.get('.clickable').click()
        cy.get('.navbar-burger').click()
        cy.contains('poussin').click()
        cy.wait(50)
        cy.fixture('users/authorizations/highAuthorizations.json').then(authorization=>{
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/authorization',
                authorization
            )
        })
        let user = {
            "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
            "login": "poussin",
            "authorizedForApplicationCreation": true,
            "superadmin": true,
            "authorizations": [
                ".*"
            ]
        }
        cy.contains('Autorisations').click()
        cy.wait(50)
        cy.contains('lambda')
        cy.contains('poussin')
        cy.get(':nth-child(2) > [data-label="Administration"] > .b-checkbox > .check').first().click()
        cy.get('[title="titi"] > .delete').click()

        cy.intercept('DELETE','http://localhost:8081/api/v1//authorization/superadmin', user).as('superadmin2')
        cy.intercept('DELETE','http://localhost:8081/api/v1//authorization/applicationCreator',user).as('applicationCreator2')

        cy.get('.button > :nth-child(2)').click()
        cy.wait('@superadmin2').then(test3)
        cy.wait('@applicationCreator2').then(test4)
    })
})