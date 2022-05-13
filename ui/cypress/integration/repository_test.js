describe('test repository', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });
     it('Test repository', () => {
         localStorage.setItem("lang", "fr")
         localStorage.setItem("authenticatedUser", "{\"id\":\"5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9\",\"login\":\"poussin\",\"authorizedForApplicationCreation\":true}")
         //cy.login("admin", ['applications/ore/ore_application_description.json'])
         cy.fixture('applications/ore/foret/applications.json').as('applications')
         cy.get('@applications').then((foret) => {
             cy.intercept(
                 'GET',
                 'http://localhost:8081/api/v1/applications',
                 foret).as('getApplicationsForetResponse')
         })
         cy.fixture('applications/ore/foret/foret.json').as('foret')
         cy.get('@foret').then((foret) => {
             cy.intercept(
                 'GET',
                 'http://localhost:8081/api/v1/applications/foret',
                 foret).as('getApplicationForetResponse')
         })
         cy.fixture('applications/ore/foret/zone_etude.json').as('zone_etude')
         cy.get('@zone_etude').then((zone_etude) => {
             cy.intercept(
                 'GET',
                 'http://localhost:8081/api/v1/applications/foret/references/zones_etudes',
                 zone_etude
             ).as('getForetZoneEtudeResponse')
         })
         cy.visit(Cypress.env('repository_foret_url'))
         cy.wait(500)
         cy.fixture('applications/ore/foret/swc_j.json').as('swc_j')
         cy.get('@swc_j').then((swc_j) => {
             cy.intercept({
                     method: 'GET',
                     url: 'http://localhost:8081/api/v1/applications/foret/filesOnRepository/swc_j?repositoryId=%7B%22datatype%22%3A%22swc_j%22%2C%22requiredauthorizations%22%3A%7B%22localization%22%3A%22fougeres.fougeres__fou_4%22%7D%2C%22from%22%3Anull%2C%22to%22%3Anull%2C%22comment%22%3Anull%7D'
                 },
                 swc_j).as('getSWCJForetResponse')
         })
         cy.fixture('applications/ore/foret/swc_j2.json').as('swc_j2')
         cy.get('@swc_j2').then((swc_j) => {
             cy.intercept({
                     method: 'GET',
                     url: 'http://localhost:8081/api/v1/applications/foret/filesOnRepository/swc_j?repositoryId=%7B%22datatype%22%3A%22swc_j%22%2C%22requiredauthorizations%22%3A%7B%22localization%22%3A%22fougeres.fougeres__fou_2%22%7D%2C%22from%22%3Anull%2C%22to%22%3Anull%2C%22comment%22%3Anull%7D'
                 },
                 swc_j).as('getSWCJForetResponse')
         })
         //case hierarchical key
         cy.get('input[data-cy=changeFileButton]').attachFile({
             fileContent: "toto",
             fileName: 'fougeres.fougeres__fou_4_swc_j_01-01-1999_31-12-1999.csv',
             mimeType: 'text/csv'
         })
         cy.wait(500)
         cy.get('.table > [tabindex="0"] > :nth-child(1)').click()
         cy.get('.tooltip-trigger > a').contains('f7cc4755')
         cy.scrollTo('bottom')

         //case natural key
         cy.get('input[data-cy=changeFileButton]').attachFile({
             fileContent: "titi",
             fileName: 'fougeres__fou_2_swc_j_01-01-2000_31-12-2000.csv',
             mimeType: 'text/csv'
         })
         cy.wait(500)
         cy.get('.table > [tabindex="0"] > :nth-child(1)').click()
         cy.get('.tooltip-trigger > a').contains('4591638b')
         cy.scrollTo('bottom')
     })
});