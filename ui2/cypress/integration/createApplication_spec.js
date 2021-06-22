/// <reference types="Cypress" />

describe('test create application', () => {
    beforeEach(() => {
        cy.login("admin", ['applications/acbb/acbb_application_description.json'])
    });

    it('Test creation site', () => {

        cy.get('.buttons button.is-primary').contains(' Créer l\'application ').click()
        const testYaml = 'applications/fake/fakeYaml_testCreateAplication.json'
        const yamlSite = 'applications/sites/site.yaml'
        const nameApplication = 'site'

        cy.visit(Cypress.env('aplications_url'))
        cy.get('.buttons button.is-primary').contains(" Créer l'application ").click()

        cy.visit(Cypress.env('applicationCreation_url'))
        cy.get('input[placeholder = "Entrer le nom de l\'application"]').type(nameApplication)
            //cy.intercept('POST', 'http://localhost:8081/api/v1/applications/' + nameApplication, { fixture: testYaml }).as('btnTestYaml')

        cy.fixture(yamlSite).then(fileContent => {
            cy.get('input[type = "file"]').attachFile({
                fileContent: fileContent.toString(),
                fileName: yamlSite,
                mimeType: 'text/yaml'
            })
        })
    })
})