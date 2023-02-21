/// <reference types="Cypress" />


require('cypress-plugin-tab')
const test1= function (response){
    Assert.equal('lambda', response.request.body.match('lambda'))
    Assert.equal('pro', response.request.body.match('pro'))
}
const test2= function (response){
    Assert.equal('poussin', response.request.body.match('poussin'))
    Assert.equal('ola', response.request.body.match('ola'))
}
import Assert from "assert";
describe('test high authorization application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it('Test authorization ajout', () => {
        cy.login("applicationCreator", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        cy.get('.clickable').click()
        cy.get('.navbar-burger').click()
        cy.contains('acreator').click()
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
        cy.get(':nth-child(1) > [data-label="Applications"] > .columns > :nth-child(3) ').click()
        cy.get(':nth-child(2) > [data-label="Applications"] > .columns > :nth-child(2) ').click()
        cy.intercept('PUT','http://localhost:8081/api/v1/authorization/applicationCreator',user).as('applicationCreator')
        cy.intercept('DELETE','http://localhost:8081/api/v1/authorization/applicationCreator',user).as('applicationCreator2')
        cy.get('.button > :nth-child(2)').click()
        cy.wait('@applicationCreator').then(test1)
        cy.wait('@applicationCreator2').then(test2)
    })
})