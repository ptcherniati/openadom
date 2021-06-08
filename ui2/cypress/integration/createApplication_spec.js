//import { Fetcher, LOCAL_STORAGE_AUTHENTICATED_USER } from "../../src/services/Fetcher";

describe('test create application', () => {
    //localStorage.setItem(LOCAL_STORAGE_AUTHENTICATED_USER, poussin)
    it('Test fichier yaml page', () => {
        const poussin = cy.fixture('login_poussin.json')
        localStorage.setItem("authenticatedUser", poussin)
        cy.visit('/applications')
        cy.get('.buttons button.is-primary').contains(" Créer l'application ").click()

        cy.intercept('POST', 'http://localhost:8081/api/v1/applications/site', { fixture: 'fakeYaml_testCreateAplication.json' }).as('btnTestYaml')

        cy.visit('/applicationCreation')
        cy.get('input[placeholder = "Entrer le nom de l\'application"]').type('site')

        cy.fixture('site.yaml').then(fileContent => {
            cy.get('input[type = "file"]').attachFile({
                fileContent: fileContent.toString(),
                fileName: 'site.yaml',
                mimeType: 'text/yaml'
            })
        })

        cy.get('.buttons button.is-primary').contains(' Créer l\'application ').click()
    })
})