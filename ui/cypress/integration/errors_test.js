/// <reference types="Cypress" />

let errors = require('../fixtures/applications/errors/errors.json');
describe('test create application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it('Test creation site', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])

        cy.get('.card-header-title.createApplication').first().contains(' Créer l\'application ').click()
        const testYaml = 'applications/fake/fakeYaml_testCreateAplication.json'
        const yamlSite = 'applications/sites/site.yaml'
        const nameApplication = 'site'

        cy.visit(Cypress.env('applications_url'))
        cy.get('.card-header-title.createApplication').first().contains(" Créer l\'application ").click()

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
        let returnErrors = {};
        for (const methodName in errors) {
            cy.intercept(
                'POST',
                'http://localhost:8081/api/v1/validate-configuration', {
                    statusCode: 200,
                    body: {validationCheckResults: errors[methodName].validationCheckResults}
                }).as('validateResponse')
            cy.get('.button > :nth-child(2)').first().click();
            cy.get('.media-content')
                .invoke('text')
                .then(function(error){
                    returnErrors[methodName]=error
                console.log("Message d'erreur pour l'erreur " + methodName, error)
            });
        }
    })
})