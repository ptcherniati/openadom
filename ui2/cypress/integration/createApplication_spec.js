describe('test create application', () => {
    it('Test fichier yaml page', () => {
        const fakeYaml = 'fakeYaml_testCreateAplication.json'
        const yamlSite = 'site.yaml'
        const fixtureLogin = cy.fixture('login_poussin.json')
        localStorage.setItem("authenticatedUser", fixtureLogin)

        cy.visit(Cypress.env('aplications_url'))
        cy.wait(5000)
        cy.get('.buttons button.is-primary').contains(" Créer l'application ").click()

        cy.intercept('POST', 'http://localhost:8081/api/v1/applications/site', { fixture: fakeYaml }).as('btnTestYaml')

        cy.visit(Cypress.env('applicationCreation_url'))
        cy.wait(5000)
        cy.get('input[placeholder = "Entrer le nom de l\'application"]').type('site')

        cy.fixture(yamlSite).then(fileContent => {
            cy.get('input[type = "file"]').attachFile({
                fileContent: fileContent.toString(),
                fileName: yamlSite,
                mimeType: 'text/yaml'
            })
        })

        cy.get('.buttons button.is-primary').contains(' Créer l\'application ').click()
    })
})