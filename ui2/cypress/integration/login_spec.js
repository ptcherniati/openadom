/// <reference types="Cypress" />
//import { UserPreferencesService } from "../../src/services/UserPreferencesService";

describe('test login', () => {
    it('login visitor', () => {
        cy.setLocale('fr');
        cy.login("visitor")
        cy.url().should('include', '/login')

    })
    it('login admin', () => {
        cy.setLocale('fr');
        cy.visit('url', {
            headers: {
                'Accept-Language': 'de',
            },
        });
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.url().should('include', '/application')
        cy.get('.card-header-title.createApplication').first().contains(" Créer l'application ")

    })
})