/// <reference types="Cypress" />

describe('test create application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it.skip('Test creation site', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])

        cy.get('.card-header-title.createApplication').first().contains(' Créer l\'application ').click()
        const yamlSite = 'applications/sites/site.yaml'

        cy.visit(Cypress.env('applications_url'))
        cy.get('.card-header-title.createApplication').first().contains(" Créer l'application ").click()

        cy.visit(Cypress.env('applicationCreation_url'))
        //cy.get('input[type = text]').first().type(nameApplication)
        //cy.intercept('POST', 'http://localhost:8081/api/v1/applications/' + nameApplication, { fixture: testYaml }).as('btnTestYaml')

        cy.fixture(yamlSite).then(fileContent => {
            cy.get('input[type = "file"]').attachFile({
                fileContent: fileContent.toString(),
                fileName: yamlSite,
                mimeType: 'text/yaml'
            })
        })
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/validate-configuration', {
                statusCode: 200,
                body: {
                    "validationCheckResults": [],
                    "result": {
                        "requiredAuthorizationsAttributes": [],
                        "version": 1,
                        "internationalization": {
                            "application": {
                                "internationalizationName": {
                                    "fr": "application test",
                                    "en": "test application",
                                    "es": "el superba application"
                                }
                            }, "references": {}, "dataTypes": {}
                        },
                        "comment": null,
                        "application": {
                            "internationalizationName": {
                                "fr": "application test",
                                "en": "test application",
                                "es": "el superba application"
                            },
                            "internationalizedColumns": null,
                            "name": "applicationtest",
                            "version": 1,
                            "defaultLanguage": null,
                            "internationalization": {
                                "internationalizationName": {
                                    "fr": "application test",
                                    "en": "test application",
                                    "es": "el superba application"
                                }
                            }
                        },
                        "references": {},
                        "compositeReferences": {},
                        "dataTypes": {}
                    },
                    "valid": true
                },
            }).as('validateResponse')
        cy.get('.button > :nth-child(2)').first().click();
        cy.wait(100)
        cy.get('.textarea').first().type("un commentaire")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/applications/applicationtest', {
                statusCode: 200,
                body: {"id":"49ff0469-c277-4525-a99e-6e404fd99954"}
            }).as('getApplicationResponse')
        cy.get('.buttons > .button > :nth-child(2)').first().click();
        cy.get('.columns').children().its('length').should('be.gt', 0)
    })
})