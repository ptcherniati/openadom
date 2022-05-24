/// <reference types="Cypress" />
//import { UserPreferencesService } from "../../src/services/UserPreferencesService";

describe('test login', () => {
    it('login admin', () => {
        cy.visit('url', {
            headers: {
                'Accept-Language': 'de',
            },
        });
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.url().should('include', '/application')
        cy.get('.card-header-title.createApplication').first().contains(" Créer l'application ")

    })
    it('login visitor', () => {
        cy.login("visitor")
        cy.url().should('include', '/login')

    })
})