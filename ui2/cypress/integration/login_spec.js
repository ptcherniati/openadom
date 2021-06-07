describe('test btn yaml', () => {
    beforeEach(() => {
        cy.intercept('POST', 'http://localhost:8081/api/v1/login', { fixture: 'login_poussin.json' }).as('userSuccess')

        cy.visit('/')
        cy.get('input[placeholder = "Entrer l\'identifiant"]').type('poussin')
        cy.get('input[placeholder = "Entrer le mot de passe"]').type('xxxxxxxx')
        cy.get('.buttons button').contains(" Se connecter ").click()
    })
    it('Test fichier yaml page', () => {
        cy.visit('/applications')
        cy.get('.buttons button.is-primary').contains(" Créer l'application ").click()

        // cy.intercept('POST', 'http://localhost:8081/api/v1/applications', { fixture: '' }).as('btnTestYaml')

        cy.visit('/applicationCreation')
        cy.get('input[placeholder = "Entrer le nom de l\'application"]').type('site')
        // cy.get('.upload').contains('Choisir une configuration').click()
    })
})
