/// <reference types="Cypress" />

let errors = require('../fixtures/applications/errors/errors.json');
describe('test create application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });
    it('Test jsonDeserializationError', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 500,
                body: {
                    "message": "jsonDeserializationError",
                    "params": {
                        "json": "json",
                        "objectMapper": "un objet",
                        "message": "un message",
                    }
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Impossible de transformer la chaîne json json : un message")

    });
    it('Test requestMapperSerializationError', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "requestMapperSerializationError",
                    "params": {
                        "requestClient": "un requestClient",
                        "objectMapper": "un objectMapper",
                        "message": "un message",
                    }
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Impossible de transformer la chaîne json un requestClient : un message")

    });
    it('Test IOException', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "IOException",
                    "params": {
                        "message": "un message",
                    }
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Une erreur de lecture de fichier est survenue. un message")

    });
    it('Test sqlConvertExceptionForClass', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "sqlConvertExceptionForClass",
                    "params": {
                        "message": "un message",
                    }
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Une erreur sql d'évaluation de valeur est survenue. ")

    });
    it('Test badBoundTypeForInterval', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "badBoundTypeForInterval",
                    "params": {
                        "boundType": "{",
                        "knownBoundType": ["[", "("],
                    }
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("L'intervalle est borné avec la valeur { : les valeurs acceptées sont [,(")

    });
    it('Test badBoundsForInterval', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "badBoundsForInterval",
                    "params": {
                        "boundValue": "{",
                        "lowerBound": "lowerBound",
                        "upperBound": "upperBound",
                        "acceptedValues": ["[", "("]
                    },
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("L'intervalle lowerBound,upperBound est borné avec la valeur upperBound: les valeurs acceptées sont [,(")

    });
    it('Test badGroovyExpressionChecker', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "badGroovyExpressionChecker",
                    "params": {
                        "expression": "Une expression",
                        "lineNumber": 12,
                        "columnNumber": 4,
                        "message": "Une erreur est survenue"
                    },
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("L'évaluation de l'expression Groovy Une expression renvoie l'erreur Une erreur est survenue ligne 12 colonne 4.")

    });
    it('Test badGroovyExpressionCheckerReturnType', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "badGroovyExpressionCheckerReturnType",
                    "params": {
                        "value": "Une valeur",
                        "expression": "une expression",
                        "context": {"value1": "toto", "value2": "titi"},
                        "knownCheckerReturnType": ["boolean", "integer"]
                    },
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("L'évaluation de l'expression Groovy une expression renvoie la valeur . Les valeurs de retour acceptés sont : boolean,integer ")

    });
    it('Test badCheckerType', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "badCheckerType",
                    "params": {"checkerType": "Float", "knownCheckerType": ["GroovyExpression", "Date"]},
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Le checker de type Float n'est pas valide. Vous pouvez utiliser l'un de ces checkers : GroovyExpression,Date ")

    });
    it('Test badStoreValueType', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "badStoreValueType",
                    "params": {
                        "storeValueType": "storeValueType",
                        "referenceDatumKey": "une clef",
                        "knownStoreValueType": ["String", "Set<String>", "Map<String, String>"]
                    },
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Impossible d'obtenir une valeur pour la clef une clef. Le type de la valeur storeValueType n'est pas l'un des types de retour acceptés : String,Set,Map")

    });
    it('Test sqlConvertException', () => {
        cy.visit(Cypress.env('login_url'))
        cy.get(':nth-child(1) > .field > .control > input').first().type('toto')
        cy.get(':nth-child(2) > .field > .control > input').first().type("password")
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/login', {
                statusCode: 400,
                body: {
                    "message": "sqlConvertException",
                    "params": {
                        "locationColumnNumber": 25,
                        "locationLineNumber": 12,
                        "message": "Message d'erreur sql",
                        "originalMessage": "original message"
                    },
                    "localizedMessage": "sqlConvertException"
                }
            }).as('postUserResponse')
        cy.get('.buttons button').contains(" Se connecter ").click()
        cy.get('.toast >div').contains("Une erreur dans une requête sql est survenue ligne 12 colonne 25" )
        cy.get('.toast >div').contains("Message d'erreur sql" )

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
                .then(function (error) {
                    returnErrors[methodName] = error
                    console.log("Message d'erreur pour l'erreur " + methodName, error)
                });
        }
    })
})