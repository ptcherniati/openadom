/// <reference types="Cypress" />

describe('test create application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it('Test creation authorization admin', () => {
        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        const olaDataType = 'applications/ore/ola/ola.json'

        /* intercept pour get datatypes*/
        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageDATA')
        })
        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE&filter=SYNTHESIS', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageDATA')
        })
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/zooplancton', {
                statusCode: 200,
                body: {
                    "": [
                        {
                            "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                            "datatype": "zooplancton",
                            "variable": "",
                            "requiredAuthorizations": {
                                "site": "grand_lac.annecy",
                                "projet": "suivi_des_lacs"
                            },
                            "aggregation": "",
                            "ranges": [
                                {
                                    "range": [
                                        "1994-01-04T00:00",
                                        "1994-01-05T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-03-15T00:00",
                                        "1994-03-16T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-04-05T00:00",
                                        "1994-04-06T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-04-20T00:00",
                                        "1994-04-21T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-05-03T00:00",
                                        "1994-05-04T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-05-17T00:00",
                                        "1994-05-18T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-06-13T00:00",
                                        "1994-06-14T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-07-26T00:00",
                                        "1994-07-27T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-08-11T00:00",
                                        "1994-08-12T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-09-13T00:00",
                                        "1994-09-14T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-10-12T00:00",
                                        "1994-10-13T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-11-15T00:00",
                                        "1994-11-16T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "1994-12-06T00:00",
                                        "1994-12-07T00:00"
                                    ]
                                }
                            ]
                        },
                        {
                            "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                            "datatype": "zooplancton",
                            "variable": "",
                            "requiredAuthorizations": {
                                "site": "grand_lac.leman",
                                "projet": "suivi_des_lacs"
                            },
                            "aggregation": "",
                            "ranges": [
                                {
                                    "range": [
                                        "2021-01-26T00:00",
                                        "2021-01-27T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-02-16T00:00",
                                        "2021-02-17T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-03-02T00:00",
                                        "2021-03-03T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-03-16T00:00",
                                        "2021-03-17T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-04-20T00:00",
                                        "2021-04-21T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-05-03T00:00",
                                        "2021-05-04T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-05-19T00:00",
                                        "2021-05-20T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-06-01T00:00",
                                        "2021-06-02T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-06-14T00:00",
                                        "2021-06-15T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-07-07T00:00",
                                        "2021-07-08T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-07-21T00:00",
                                        "2021-07-22T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-08-09T00:00",
                                        "2021-08-10T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-09-02T00:00",
                                        "2021-09-03T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-09-15T00:00",
                                        "2021-09-16T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-09-27T00:00",
                                        "2021-09-28T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-10-19T00:00",
                                        "2021-10-20T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-11-16T00:00",
                                        "2021-11-17T00:00"
                                    ]
                                },
                                {
                                    "range": [
                                        "2021-12-06T00:00",
                                        "2021-12-07T00:00"
                                    ]
                                }
                            ]
                        }
                    ]
                }
            }).as('pageDATAzooplancton')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/chlorophylle', {
                statusCode: 200,
                body: {}
            }).as('pageDATAchlorophylle')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/phytoplancton', {
                statusCode: 200,
                body: {}
            }).as('pageDATAphytoplancton')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/physico-chimie', {
                statusCode: 200,
                body: {}
            }).as('pageDATAphysico-chimie')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/haute_frequence', {
                statusCode: 200,
                body: {}
            }).as('pageDATAhaute_frequence')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/production_primaire', {
                statusCode: 200,
                body: {}
            }).as('pageDATAproduction_primaire')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/sonde_multiparametres', {
                statusCode: 200,
                body: {}
            }).as('pageDATAsonde_multiparametres')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/synthesis/condition_prelevements', {
                statusCode: 200,
                body: {}
            }).as('pageDATA')

        cy.visit(Cypress.env('ola_dataTypes_url'))

        /* get datatype*/
        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE&filter=SYNTHESIS', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageDataAuthorization')
        })
        /* intercept pour get authorizations*/
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/authorization', {
                statusCode: 200,
                body: {
                    "authorizationResults": [
                        {
                            "uuid": "596397ad-0359-43d1-b8c7-fe9eae95bf26",
                            "name": "test chlrophylle",
                            "users": [
                                {
                                    "id": "a5486b95-21f7-4f02-8942-adbd707fcf1b",
                                    "creationDate": 1677508654866,
                                    "updateDate": 1677508654866,
                                    "login": "echo",
                                    "password": "$2a$12$sz6MzU0jQe16yN7xthzYCuUEUThqEHTRzJBXphaqkBergJDpYnQhq",
                                    "authorizations": []
                                }
                            ],
                            "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                            "authorizations": {
                                "phytoplancton": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "delete": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                },
                                "chlorophylle": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "admin": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "delete": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "publication": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                },
                                "zooplancton": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "admin": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "delete": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                }
                            },
                            "publicAuthorizations": {
                                "physico-chimie": {
                                    "extraction": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ]
                                }
                            },
                            "authorizationsForUser": {
                                "authorizationResults": {},
                                "applicationName": "ola",
                                "authorizationByPath": {},
                                "isAdministrator": true
                            }
                        },
                        {
                            "uuid": "479ea4d8-d116-4bbf-8365-c8b7c8552b29",
                            "name": "depot extra phisico",
                            "users": [
                                {
                                    "id": "9032ffe5-bfc1-453d-814e-287cd678484a",
                                    "creationDate": 1677497830455,
                                    "updateDate": 1677497830455,
                                    "login": "_public_",
                                    "password": "",
                                    "authorizations": [
                                        ".*"
                                    ]
                                }
                            ],
                            "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                            "authorizations": {
                                "physico-chimie": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                }
                            },
                            "publicAuthorizations": {
                                "physico-chimie": {
                                    "extraction": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ]
                                }
                            },
                            "authorizationsForUser": {
                                "authorizationResults": {},
                                "applicationName": "ola",
                                "authorizationByPath": {},
                                "isAdministrator": true
                            }
                        }
                    ],
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "authorizationByPath": {},
                        "isAdministrator": true
                    }
                }
            }).as('pageDataAuthorization')

        cy.visit(Cypress.env('ola_dataTypes_authorizations_url'))

        cy.wait(150)
        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageDataAuthorization')
        })

        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/grantable', {
                statusCode: 200,
                body: {
                    "users": [{
                        "id": "9032ffe5-bfc1-453d-814e-287cd678484a",
                        "label": "_public_"
                    }, {
                        "id": "a5486b95-21f7-4f02-8942-adbd707fcf1b",
                        "label": "echo"
                    }, {"id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9", "label": "poussin"}],
                    "dataGroups": {
                        "condition_prelevements": [{
                            "id": "qualitatif",
                            "label": "Données qualitatives"
                        }, {"id": "quantitatif", "label": "Données quantitatives"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }],
                        "phytoplancton": [{"id": "condition", "label": "Contexte"}, {
                            "id": "donnee",
                            "label": "Donnée"
                        }, {"id": "referentiel", "label": "Référentiel"}],
                        "chlorophylle": [{"id": "condition", "label": "Contexte"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }],
                        "production_primaire": [{"id": "condition", "label": "Contexte"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }],
                        "haute_frequence": [{"id": "all", "label": "Toutes les données"}],
                        "zooplancton": [{"id": "condition", "label": "Contexte"}, {
                            "id": "donnée",
                            "label": "Data"
                        }, {"id": "referentiel", "label": "Référentiel"}],
                        "physico-chimie": [{"id": "condition", "label": "Contexte"}, {
                            "id": "dataGroup_variable",
                            "label": "Données"
                        }, {"id": "referentiel", "label": "Référentiel"}],
                        "sonde_multiparametres": [{
                            "id": "condition_prelevement",
                            "label": "Condition de prélèvement"
                        }, {"id": "donnee_prelevement", "label": "Données du prélèvement"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }]
                    },
                    "authorizationScopes": {
                        "condition_prelevements": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "phytoplancton": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "chlorophylle": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "production_primaire": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "haute_frequence": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "zooplancton": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "physico-chimie": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "sonde_multiparametres": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }]
                    },
                    "columnsDescription": {
                        "condition_prelevements": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "phytoplancton": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "chlorophylle": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "production_primaire": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "haute_frequence": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "zooplancton": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "physico-chimie": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "sonde_multiparametres": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        }
                    },
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "authorizationByPath": {},
                        "isAdministrator": true
                    },
                    "publicAuthorizations": {
                        "physico-chimie": {
                            "extraction": [{
                                "timeScope": {"range": {"empty": false}},
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                            }],
                            "depot": [{
                                "timeScope": {"range": {"empty": false}},
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                            }]
                        }
                    }
                }
            }).as('pageDataAuthorizationGrantable')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/ref_site', {
                statusCode: 200,
                body: {
                    "referenceValues": [{
                        "hierarchicalKey": "grand_lac.aiguebelette",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "aiguebelette",
                        "values": {
                            "__display_en": "Aiguebelette",
                            "__display_fr": "Aiguebelette",
                            "nom du site_en": "Aiguebelette",
                            "nom du site_fr": "Aiguebelette",
                            "nom du site_key": "aiguebelette",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "",
                            "description du site_fr": "",
                            "code sandre du Plan d'eau": "DL61",
                            "code sandre de la Masse d'eau plan d'eau": "V1535003"
                        }
                    }, {
                        "hierarchicalKey": "grand_lac.annecy",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "annecy",
                        "values": {
                            "__display_en": "Annecy",
                            "__display_fr": "Annecy",
                            "nom du site_en": "Annecy",
                            "nom du site_fr": "Annecy",
                            "nom du site_key": "annecy",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "lake of Annecy",
                            "description du site_fr": "lac d' Annecy",
                            "code sandre du Plan d'eau": "DL66",
                            "code sandre de la Masse d'eau plan d'eau": "V1235003"
                        }
                    }, {
                        "hierarchicalKey": "grand_lac.bourget",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bourget",
                        "values": {
                            "__display_en": "Bourget",
                            "__display_fr": "Bourget",
                            "nom du site_en": "Bourget",
                            "nom du site_fr": "Bourget",
                            "nom du site_key": "bourget",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "lake of  Bourget",
                            "description du site_fr": "lac du Bourget",
                            "code sandre du Plan d'eau": "DL60",
                            "code sandre de la Masse d'eau plan d'eau": "V1335003"
                        }
                    }, {
                        "hierarchicalKey": "grand_lac.leman",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "leman",
                        "values": {
                            "__display_en": "Leman(Geneva Lake)",
                            "__display_fr": "Léman",
                            "nom du site_en": "Leman(Geneva Lake)",
                            "nom du site_fr": "Léman",
                            "nom du site_key": "leman",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "Geneva lake (Leman)",
                            "description du site_fr": "lac Léman",
                            "code sandre du Plan d'eau": "DL65",
                            "code sandre de la Masse d'eau plan d'eau": "V03-4003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.anterne",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "anterne",
                        "values": {
                            "__display_en": "Anterne",
                            "__display_fr": "Anterne",
                            "nom du site_en": "Anterne",
                            "nom du site_fr": "Anterne",
                            "nom du site_key": "anterne",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Anterne lake",
                            "description du site_fr": "Lac d'Anterne",
                            "code sandre du Plan d'eau": "DL62",
                            "code sandre de la Masse d'eau plan d'eau": "V0115023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.aratilles",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "aratilles",
                        "values": {
                            "__display_en": "Aratilles",
                            "__display_fr": "Aratilles",
                            "nom du site_en": "Aratilles",
                            "nom du site_fr": "Aratilles",
                            "nom du site_key": "aratilles",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.arbu",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "arbu",
                        "values": {
                            "__display_en": "Arbu",
                            "__display_fr": "Arbu",
                            "nom du site_en": "Arbu",
                            "nom du site_fr": "Arbu",
                            "nom du site_key": "arbu",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O1135003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.arpont",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "arpont",
                        "values": {
                            "__display_en": "Arpont",
                            "__display_fr": "Arpont",
                            "nom du site_en": "Arpont",
                            "nom du site_fr": "Arpont",
                            "nom du site_key": "arpont",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Arpont Lake",
                            "description du site_fr": "Lac de l'Arpont",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1015003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.aumar",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "aumar",
                        "values": {
                            "__display_en": "Aumar",
                            "__display_fr": "Aumar",
                            "nom du site_en": "Aumar",
                            "nom du site_fr": "Aumar",
                            "nom du site_key": "aumar",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "This lake is located in the French pyrénées at 2192 m in the Néouvielle Reserve. This lake belongs to the EDF hydropower scheme of Pragnères",
                            "description du site_fr": "Ce lac est situé pyrénées française à une altitude de 2192 m au cœur de la réserve naturelle de Néouvielle. Il fait partie de l'aménagement hydroélectrique de Pragnères (EDF) où ses eaux sont turbinées",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0115123"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.barroude",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "barroude",
                        "values": {
                            "__display_en": "Barroude",
                            "__display_fr": "Barroude",
                            "nom du site_en": "Barroude",
                            "nom du site_fr": "Barroude",
                            "nom du site_key": "barroude",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0105023 (grand) et O0105013 (petit)"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.blanc_du_bramant",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "blanc_du_bramant",
                        "values": {
                            "__display_en": "Blanc du Bramant",
                            "__display_fr": "Blanc du Bramant",
                            "nom du site_en": "Blanc du Bramant",
                            "nom du site_fr": "Blanc du Bramant",
                            "nom du site_key": "blanc du bramant",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Blanc du Bramant Lake",
                            "description du site_fr": "lac Blanc du Bramant",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.blanc_du_carro",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "blanc_du_carro",
                        "values": {
                            "__display_en": "Blanc du Carro",
                            "__display_fr": "Blanc du Carro",
                            "nom du site_en": "Blanc du Carro",
                            "nom du site_fr": "Blanc du Carro",
                            "nom du site_key": "blanc du carro",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Carro Blanc lake",
                            "description du site_fr": "Lac Blanc du Caro",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1005043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.bramant",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bramant",
                        "values": {
                            "__display_en": "Bramant",
                            "__display_fr": "Bramant",
                            "nom du site_en": "Bramant",
                            "nom du site_fr": "Bramant",
                            "nom du site_key": "bramant",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Bramant Lake",
                            "description du site_fr": "Lac Bramant",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.bresses_inferieur",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bresses_inferieur",
                        "values": {
                            "__display_en": "Bresses inférieur",
                            "__display_fr": "Bresses inférieur",
                            "nom du site_en": "Bresses inférieur",
                            "nom du site_fr": "Bresses inférieur",
                            "nom du site_key": "bresses inferieur",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lower Bresse lake",
                            "description du site_fr": "lac de Bresses inférieur",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Y6225043 bis"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.bresses_superieur",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bresses_superieur",
                        "values": {
                            "__display_en": "Bresses supérieur",
                            "__display_fr": "Bresses supérieur",
                            "nom du site_en": "Bresses supérieur",
                            "nom du site_fr": "Bresses supérieur",
                            "nom du site_key": "bresses superieur",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Upper Bresse lake",
                            "description du site_fr": "lac de Bresses supérieur",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Y6225043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.brevent",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "brevent",
                        "values": {
                            "__display_en": "Brevent",
                            "__display_fr": "Brévent",
                            "nom du site_en": "Brevent",
                            "nom du site_fr": "Brévent",
                            "nom du site_key": "brevent",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Brevent lake",
                            "description du site_fr": "Lac du Brévent",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0015023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.corne",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "corne",
                        "values": {
                            "__display_en": "Corne",
                            "__display_fr": "Corne",
                            "nom du site_en": "Corne",
                            "nom du site_fr": "Corne",
                            "nom du site_key": "corne",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Corne lake",
                            "description du site_fr": "Lac de Corne",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2755063"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.cornu",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "cornu",
                        "values": {
                            "__display_en": "Cornu",
                            "__display_fr": "Cornu",
                            "nom du site_en": "Cornu",
                            "nom du site_fr": "Cornu",
                            "nom du site_key": "cornu",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Cornu lake",
                            "description du site_fr": "Lac Cornu",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0015043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.cos",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "cos",
                        "values": {
                            "__display_en": "Cos",
                            "__display_fr": "Cos",
                            "nom du site_en": "Cos",
                            "nom du site_fr": "Cos",
                            "nom du site_key": "cos",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Cos lake",
                            "description du site_fr": "lac de Cos",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1205063"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.espingo",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "espingo",
                        "values": {
                            "__display_en": "Espingo",
                            "__display_fr": "Espingo",
                            "nom du site_en": "Espingo",
                            "nom du site_fr": "Espingo",
                            "nom du site_key": "espingo",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.estany_gros",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "estany_gros",
                        "values": {
                            "__display_en": "Estany Gros",
                            "__display_fr": "Estany Gros",
                            "nom du site_en": "Estany Gros",
                            "nom du site_fr": "Estany Gros",
                            "nom du site_key": "estany gros",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.gentau",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "gentau",
                        "values": {
                            "__display_en": "Gentau",
                            "__display_fr": "Gentau",
                            "nom du site_en": "Gentau",
                            "nom du site_fr": "Gentau",
                            "nom du site_key": "gentau",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.gourg_gaudet",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "gourg_gaudet",
                        "values": {
                            "__display_en": "Gourg Gaudet",
                            "__display_fr": "Gourg Gaudet",
                            "nom du site_en": "Gourg Gaudet",
                            "nom du site_fr": "Gourg Gaudet",
                            "nom du site_key": "gourg gaudet",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.isaby",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "isaby",
                        "values": {
                            "__display_en": "Isaby",
                            "__display_fr": "Isaby",
                            "nom du site_en": "Isaby",
                            "nom du site_fr": "Isaby",
                            "nom du site_key": "isaby",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Isaby lake",
                            "description du site_fr": "lac d'Isaby",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Q4425003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.izourt",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "izourt",
                        "values": {
                            "__display_en": "Izourt",
                            "__display_fr": "Izourt",
                            "nom du site_en": "Izourt",
                            "nom du site_fr": "Izourt",
                            "nom du site_key": "izourt",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lake Izourt",
                            "description du site_fr": "lac d'Izourt",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O1125103"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.jovet",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "jovet",
                        "values": {
                            "__display_en": "Jovet",
                            "__display_fr": "Jovet",
                            "nom du site_en": "Jovet",
                            "nom du site_fr": "Jovet",
                            "nom du site_key": "jovet",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Jovet lake",
                            "description du site_fr": "Lac Jovet",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0025023 et V0025003 (lacs)"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.lauvitel",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "lauvitel",
                        "values": {
                            "__display_en": "Lauvitel",
                            "__display_fr": "Lauvitel",
                            "nom du site_en": "Lauvitel",
                            "nom du site_fr": "Lauvitel",
                            "nom du site_key": "lauvitel",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lauvitel lake",
                            "description du site_fr": "Lac de Lauvitel",
                            "code sandre du Plan d'eau": "DL76",
                            "code sandre de la Masse d'eau plan d'eau": "DL76"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.lauzanier",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "lauzanier",
                        "values": {
                            "__display_en": "Lauzanier",
                            "__display_fr": "Lauzanier",
                            "nom du site_en": "Lauzanier",
                            "nom du site_fr": "Lauzanier",
                            "nom du site_key": "lauzanier",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lauzanier lake",
                            "description du site_fr": "lac du Lauzanier",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "X0415043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.malrif",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "malrif",
                        "values": {
                            "__display_en": "Malrif",
                            "__display_fr": "Malrif",
                            "nom du site_en": "Malrif",
                            "nom du site_fr": "Malrif",
                            "nom du site_key": "malrif",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Malrif lake",
                            "description du site_fr": "lac de Malrif",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "X0215003 (petit laus), X0215023 (grand laus), Mezan ?"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.merlet_superieur",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "merlet_superieur",
                        "values": {
                            "__display_en": "Merlet supérieur",
                            "__display_fr": "Merlet supérieur",
                            "nom du site_en": "Merlet supérieur",
                            "nom du site_fr": "Merlet supérieur",
                            "nom du site_key": "merlet superieur",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Upper Merlet lake",
                            "description du site_fr": "Lac du Merlet supérieur",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W0225003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.mont_coua",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "mont_coua",
                        "values": {
                            "__display_en": "Mont Coua",
                            "__display_fr": "Mont Coua",
                            "nom du site_en": "Mont Coua",
                            "nom du site_fr": "Mont Coua",
                            "nom du site_key": "mont coua",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Coua mont lake",
                            "description du site_fr": "lac du Mont Coua",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W0235063"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.muzelle",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "muzelle",
                        "values": {
                            "__display_en": "Muzelle",
                            "__display_fr": "Muzelle",
                            "nom du site_en": "Muzelle",
                            "nom du site_fr": "Muzelle",
                            "nom du site_key": "muzelle",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Muzelle lake",
                            "description du site_fr": "lac de la Muzelle",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2735043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.noir_du_carro",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "noir_du_carro",
                        "values": {
                            "__display_en": "Noir du Carro",
                            "__display_fr": "Noir du Carro",
                            "nom du site_en": "Noir du Carro",
                            "nom du site_fr": "Noir du Carro",
                            "nom du site_key": "noir du carro",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Carro Noir lake",
                            "description du site_fr": "lac Noir du Caro",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1005023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.oncet",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "oncet",
                        "values": {
                            "__display_en": "Oncet",
                            "__display_fr": "Oncet",
                            "nom du site_en": "Oncet",
                            "nom du site_fr": "Oncet",
                            "nom du site_key": "oncet",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Oncet lake",
                            "description du site_fr": "lac d'Oncet",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Q4305003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.pave",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "pave",
                        "values": {
                            "__display_en": "Pavé",
                            "__display_fr": "Pavé",
                            "nom du site_en": "Pavé",
                            "nom du site_fr": "Pavé",
                            "nom du site_key": "pave",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Pave lake",
                            "description du site_fr": "lac du Pavé",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2705003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.petarel",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "petarel",
                        "values": {
                            "__display_en": "Pétarel",
                            "__display_fr": "Pétarel",
                            "nom du site_en": "Pétarel",
                            "nom du site_fr": "Pétarel",
                            "nom du site_key": "petarel",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Petarel lake",
                            "description du site_fr": "Lac Pétarel",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2115023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.pisses",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "pisses",
                        "values": {
                            "__display_en": "Pisses",
                            "__display_fr": "Pisses",
                            "nom du site_en": "Pisses",
                            "nom du site_fr": "Pisses",
                            "nom du site_key": "pisses",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Pisses lake",
                            "description du site_fr": "Lac des Pisses",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2005023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.plan_vianney",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "plan_vianney",
                        "values": {
                            "__display_en": "Plan Vianney",
                            "__display_fr": "Plan Vianney",
                            "nom du site_en": "Plan Vianney",
                            "nom du site_fr": "Plan Vianney",
                            "nom du site_key": "plan vianney",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Plan Vianney Lake",
                            "description du site_fr": "Lac de Plan Vianney",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2735003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.pormenaz",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "pormenaz",
                        "values": {
                            "__display_en": "Pormenaz",
                            "__display_fr": "Pormenaz",
                            "nom du site_en": "Pormenaz",
                            "nom du site_fr": "Pormenaz",
                            "nom du site_key": "pormenaz",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Pormenaz lake",
                            "description du site_fr": "Lac de Pormenaz",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0015003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.port___bielh",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "port___bielh",
                        "values": {
                            "__display_en": "Port Bielh",
                            "__display_fr": "Port Bielh",
                            "nom du site_en": "Port Bielh",
                            "nom du site_fr": "Port Bielh",
                            "nom du site_key": "port   bielh",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0115002"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.port_bielh",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "port_bielh",
                        "values": {
                            "__display_en": "Port-Bielh",
                            "__display_fr": "Port-Bielh",
                            "nom du site_en": "Port-Bielh",
                            "nom du site_fr": "Port-Bielh",
                            "nom du site_key": "port bielh",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0115003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.rabuons",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "rabuons",
                        "values": {
                            "__display_en": "Rabuons",
                            "__display_fr": "Rabuons",
                            "nom du site_en": "Rabuons",
                            "nom du site_fr": "Rabuons",
                            "nom du site_key": "rabuons",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Rabuons lake",
                            "description du site_fr": "lac du Rabuons",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Y6205283"
                        }
                    }, {
                        "hierarchicalKey": "riviere.bimont",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bimont",
                        "values": {
                            "__display_en": "Bimont",
                            "__display_fr": "Bimont",
                            "nom du site_en": "Bimont",
                            "nom du site_fr": "Bimont",
                            "nom du site_key": "bimont",
                            "nom du type de site": "riviere",
                            "description du site_en": "Bimont river",
                            "description du site_fr": "rivière Bimont",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "riviere.dranse",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "dranse",
                        "values": {
                            "__display_en": "Dranse",
                            "__display_fr": "Dranse",
                            "nom du site_en": "Dranse",
                            "nom du site_fr": "Dranse",
                            "nom du site_key": "dranse",
                            "nom du type de site": "riviere",
                            "description du site_en": "Dranse river",
                            "description du site_fr": "rivière Dranse",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V03-0400 ou V0321430"
                        }
                    }, {
                        "hierarchicalKey": "riviere.mercube",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "mercube",
                        "values": {
                            "__display_en": "Mercube",
                            "__display_fr": "Mercube",
                            "nom du site_en": "Mercube",
                            "nom du site_fr": "Mercube",
                            "nom du site_key": "mercube",
                            "nom du type de site": "riviere",
                            "description du site_en": "Mercube river",
                            "description du site_fr": "rivière Mercube",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0351440"
                        }
                    }]
                }
            }).as('pageDataAuthorizationrefsite')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/project', {
                statusCode: 200,
                body: {
                    "referenceValues": [{
                        "hierarchicalKey": "dce",
                        "hierarchicalReference": "project",
                        "naturalKey": "dce",
                        "values": {
                            "__display_en": "DCE",
                            "__display_fr": "DCE",
                            "nom du projet_en": "DCE",
                            "nom du projet_fr": "DCE",
                            "nom du projet_key": "dce",
                            "description du projet_en": "Sampling realized with DCE protocol",
                            "description du projet_fr": "Prélèvement faits selon le protocole de la DCE"
                        }
                    }, {
                        "hierarchicalKey": "rnt",
                        "hierarchicalReference": "project",
                        "naturalKey": "rnt",
                        "values": {
                            "__display_en": "RNT",
                            "__display_fr": "RNT",
                            "nom du projet_en": "RNT",
                            "nom du projet_fr": "RNT",
                            "nom du projet_key": "RNT",
                            "description du projet_en": "",
                            "description du projet_fr": ""
                        }
                    }, {
                        "hierarchicalKey": "sou",
                        "hierarchicalReference": "project",
                        "naturalKey": "sou",
                        "values": {
                            "__display_en": "SOU",
                            "__display_fr": "SOU",
                            "nom du projet_en": "SOU",
                            "nom du projet_fr": "SOU",
                            "nom du projet_key": "SOU",
                            "description du projet_en": "",
                            "description du projet_fr": ""
                        }
                    }, {
                        "hierarchicalKey": "suivi_des_lacs",
                        "hierarchicalReference": "project",
                        "naturalKey": "suivi_des_lacs",
                        "values": {
                            "__display_en": "Lakes monitoring",
                            "__display_fr": "Suivi des lacs",
                            "nom du projet_en": "Lakes monitoring",
                            "nom du projet_fr": "Suivi des lacs",
                            "nom du projet_key": "suivi des lacs",
                            "description du projet_en": "Long-term monitoring of peri-alpine lakes",
                            "description du projet_fr": "Suivi è long terme des lacs pèri-alpins"
                        }
                    }, {
                        "hierarchicalKey": "suivi_des_lacs_sentinelles",
                        "hierarchicalReference": "project",
                        "naturalKey": "suivi_des_lacs_sentinelles",
                        "values": {
                            "__display_en": "Sentinels lakes monitoring",
                            "__display_fr": "Suivi des lacs sentinelles",
                            "nom du projet_en": "Sentinels lakes monitoring",
                            "nom du projet_fr": "Suivi des lacs sentinelles",
                            "nom du projet_key": "suivi des lacs sentinelles",
                            "description du projet_en": "Long-term monitoring of altitudes lakes",
                            "description du projet_fr": "Suivi è long terme des lacs d'altitude"
                        }
                    }, {
                        "hierarchicalKey": "suivi_des_rivieres",
                        "hierarchicalReference": "project",
                        "naturalKey": "suivi_des_rivieres",
                        "values": {
                            "__display_en": "Rivers monitoring",
                            "__display_fr": "Suivi des rivières",
                            "nom du projet_en": "Rivers monitoring",
                            "nom du projet_fr": "Suivi des rivières",
                            "nom du projet_key": "suivi des rivieres",
                            "description du projet_en": "river's sampling",
                            "description du projet_fr": "Prélèvement en rivière(s)"
                        }
                    }]
                }
            }).as('pageDataAuthorizationProject')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/site_type\n', {
                statusCode: 200,
                body: {
                    "referenceValues": [{
                        "hierarchicalKey": "grand_lac",
                        "hierarchicalReference": "site_type",
                        "naturalKey": "grand_lac",
                        "values": {
                            "nom_en": "large lake",
                            "nom_fr": "grand lac",
                            "nom_key": "grand_lac",
                            "code sandre": "",
                            "__display_en": "large lake",
                            "__display_fr": "grand lac",
                            "description_en": "Alpine great lake from SOERE",
                            "description_fr": "Grand lac péri alpins du SOERE",
                            "code sandre du contexte": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude",
                        "hierarchicalReference": "site_type",
                        "naturalKey": "lac_d_altitude",
                        "values": {
                            "nom_en": "altitude lake",
                            "nom_fr": "lac d altitude",
                            "nom_key": "lac_d_altitude",
                            "code sandre": "",
                            "__display_en": "altitude lake",
                            "__display_fr": "lac d altitude",
                            "description_en": "altitude lake",
                            "description_fr": "lac d altitude",
                            "code sandre du contexte": ""
                        }
                    }, {
                        "hierarchicalKey": "riviere",
                        "hierarchicalReference": "site_type",
                        "naturalKey": "riviere",
                        "values": {
                            "nom_en": "river",
                            "nom_fr": "rivière",
                            "nom_key": "riviere",
                            "code sandre": "",
                            "__display_en": "river",
                            "__display_fr": "rivière",
                            "description_en": "a watershed of a large lake river",
                            "description_fr": "rivière du bassin versant d un grand lac",
                            "code sandre du contexte": ""
                        }
                    }]
                }
            }).as('pageDataAuthorizationsitetype')
        /* get authorizations*/
        cy.visit(Cypress.env('ola_dataTypes_new_authorizations_url'))
        cy.get('.taginput-container').click()
        cy.contains('poussin').click()
        cy.contains('echo').click()
        cy.get('.field > .control > .input').should(($input) => {
            const value = $input.val("name");
            console.log(value); // do something with the value
        })

        cy.get(':nth-child(2) > :nth-child(1) > div.rows > .card-content > :nth-child(2) > :nth-child(1) > .folder > [style="margin-right: 10px;"] > .svg-inline--fa > path').click()
        cy.get(':nth-child(4) > :nth-child(1) > .columns > :nth-child(2) > .field > .b-tooltip > .tooltip-trigger > .icon').click()

        cy.get(':nth-child(5) > :nth-child(1) > div.rows > .card-content > :nth-child(2) > :nth-child(1) > .folder > [style="margin-right: 10px;"] > .svg-inline--fa > path').click()
        cy.get(':nth-child(5) > :nth-child(1) > [current-authorization-scope="[object Object]"] > li[data-v-6bd0a084=""] > ul.rows > .rows > .card-content > :nth-child(3) > :nth-child(1) > .columns > :nth-child(5) > .column > .field-body > .field > .is-warning > .tooltip-trigger > .icon').click()

/* intercept pour get authorization "aa5fee55-bab4-49bb-a1c5-6fdf5fc1b301"*/
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/applications/ola/authorization', {
                statusCode: 201,
                body: {"authorizationId": "aa5fee55-bab4-49bb-a1c5-6fdf5fc1b301"},
            }).as('validateResponseDataAuthorization')

        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageCreateDataAuthorization')
        })
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/authorization', {
                statusCode: 200,
                body: {
                    "authorizationResults": [{
                        "uuid": "596397ad-0359-43d1-b8c7-fe9eae95bf26",
                        "name": "test chlrophylle",
                        "users": [{
                            "id": "a5486b95-21f7-4f02-8942-adbd707fcf1b",
                            "creationDate": 1677508654866,
                            "updateDate": 1677508654866,
                            "login": "echo",
                            "password": "$2a$12$sz6MzU0jQe16yN7xthzYCuUEUThqEHTRzJBXphaqkBergJDpYnQhq",
                            "authorizations": []
                        }],
                        "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                        "authorizations": {
                            "phytoplancton": {
                                "extraction": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "delete": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            },
                            "chlorophylle": {
                                "extraction": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "admin": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "delete": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "publication": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            },
                            "zooplancton": {
                                "extraction": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "admin": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "delete": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }, {
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            }
                        },
                        "publicAuthorizations": {
                            "physico-chimie": {
                                "extraction": [{
                                    "timeScope": {"range": {"empty": false}},
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                                }],
                                "depot": [{
                                    "timeScope": {"range": {"empty": false}},
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                                }]
                            }
                        },
                        "authorizationsForUser": {
                            "authorizationResults": {
                                "physico-chimie": {
                                    "extraction": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "depot": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "publication": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "sonde_multiparametres": {
                                    "extraction": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "depot": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "delete": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "admin": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "publication": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                }
                            }, "applicationName": "ola", "authorizationByPath": {
                                "physico-chimie": {
                                    "extraction": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "depot": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "publication": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    }
                                },
                                "sonde_multiparametres": {
                                    "extraction": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "depot": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "delete": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "admin": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "publication": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    }
                                }
                            }, "isAdministrator": true
                        }
                    }, {
                        "uuid": "479ea4d8-d116-4bbf-8365-c8b7c8552b29",
                        "name": "depot extra phisico",
                        "users": [{
                            "id": "9032ffe5-bfc1-453d-814e-287cd678484a",
                            "creationDate": 1677497830455,
                            "updateDate": 1677497830455,
                            "login": "_public_",
                            "password": "",
                            "authorizations": [".*"]
                        }],
                        "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                        "authorizations": {
                            "physico-chimie": {
                                "extraction": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "grand_lac"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            }
                        },
                        "publicAuthorizations": {
                            "physico-chimie": {
                                "extraction": [{
                                    "timeScope": {"range": {"empty": false}},
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                                }],
                                "depot": [{
                                    "timeScope": {"range": {"empty": false}},
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                                }]
                            }
                        },
                        "authorizationsForUser": {
                            "authorizationResults": {
                                "physico-chimie": {
                                    "extraction": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "depot": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "publication": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "sonde_multiparametres": {
                                    "extraction": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "depot": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "delete": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "admin": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "publication": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                }
                            }, "applicationName": "ola", "authorizationByPath": {
                                "physico-chimie": {
                                    "extraction": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "depot": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "publication": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    }
                                },
                                "sonde_multiparametres": {
                                    "extraction": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "depot": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "delete": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "admin": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "publication": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    }
                                }
                            }, "isAdministrator": true
                        }
                    }, {
                        "uuid": "aa5fee55-bab4-49bb-a1c5-6fdf5fc1b301",
                        "name": "name",
                        "users": [{
                            "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
                            "creationDate": 1677494315655,
                            "updateDate": 1677494315655,
                            "login": "poussin",
                            "password": "$2a$12$4gAH34ZwgvgQNS0pbR5dGem1Nle0AT/.UwrZWfqtqMiJ0hXeYMvUG",
                            "authorizations": [".*"]
                        }, {
                            "id": "a5486b95-21f7-4f02-8942-adbd707fcf1b",
                            "creationDate": 1677508654866,
                            "updateDate": 1677508654866,
                            "login": "echo",
                            "password": "$2a$12$sz6MzU0jQe16yN7xthzYCuUEUThqEHTRzJBXphaqkBergJDpYnQhq",
                            "authorizations": []
                        }],
                        "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                        "authorizations": {
                            "physico-chimie": {
                                "extraction": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "publication": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            },
                            "sonde_multiparametres": {
                                "extraction": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "admin": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "delete": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "publication": [{
                                    "path": "not setting",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            }
                        },
                        "publicAuthorizations": {
                            "physico-chimie": {
                                "extraction": [{
                                    "timeScope": {"range": {"empty": false}},
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                                }],
                                "depot": [{
                                    "timeScope": {"range": {"empty": false}},
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": {"sql": "grand_lac"}}
                                }]
                            }
                        },
                        "authorizationsForUser": {
                            "authorizationResults": {
                                "physico-chimie": {
                                    "extraction": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "depot": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "publication": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "sonde_multiparametres": {
                                    "extraction": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "depot": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "delete": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "admin": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }],
                                    "publication": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                }
                            }, "applicationName": "ola", "authorizationByPath": {
                                "physico-chimie": {
                                    "extraction": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "depot": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "publication": {
                                        "lac_d_altitude": [{
                                            "path": "lac_d_altitude",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "lac_d_altitude"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    }
                                },
                                "sonde_multiparametres": {
                                    "extraction": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "depot": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "delete": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "admin": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    },
                                    "publication": {
                                        "riviere": [{
                                            "path": "riviere",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {"site": "riviere"},
                                            "fromDay": null,
                                            "toDay": null
                                        }]
                                    }
                                }
                            }, "isAdministrator": true
                        }
                    }], "authorizationsForUser": {
                        "authorizationResults": {
                            "physico-chimie": {
                                "extraction": [{
                                    "path": "lac_d_altitude",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "lac_d_altitude",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "publication": [{
                                    "path": "lac_d_altitude",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "lac_d_altitude"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            },
                            "sonde_multiparametres": {
                                "extraction": [{
                                    "path": "riviere",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "depot": [{
                                    "path": "riviere",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "delete": [{
                                    "path": "riviere",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "admin": [{
                                    "path": "riviere",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }],
                                "publication": [{
                                    "path": "riviere",
                                    "dataGroups": [],
                                    "requiredAuthorizations": {"site": "riviere"},
                                    "fromDay": null,
                                    "toDay": null
                                }]
                            }
                        }, "applicationName": "ola", "authorizationByPath": {
                            "physico-chimie": {
                                "extraction": {
                                    "lac_d_altitude": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "depot": {
                                    "lac_d_altitude": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "publication": {
                                    "lac_d_altitude": [{
                                        "path": "lac_d_altitude",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "lac_d_altitude"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                }
                            },
                            "sonde_multiparametres": {
                                "extraction": {
                                    "riviere": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "depot": {
                                    "riviere": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "delete": {
                                    "riviere": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "admin": {
                                    "riviere": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                },
                                "publication": {
                                    "riviere": [{
                                        "path": "riviere",
                                        "dataGroups": [],
                                        "requiredAuthorizations": {"site": "riviere"},
                                        "fromDay": null,
                                        "toDay": null
                                    }]
                                }
                            }
                        }, "isAdministrator": true
                    }
                }
            }).as('pageCreateDataAuthorization')

        cy.visit(Cypress.env('ola_dataTypes_authorizations_url'))
        cy.intercept(
            'DELETE',
            'http://localhost:8081/api/v1/logout', {
                statusCode: 200,
            }).as('logout')
        //cy.visit(Cypress.env('login_url'))
    })

    it('Test creation authorization regularUser', () => {
        cy.login("regularUser", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        const olaDataType = 'applications/ore/ola/ola.json'

        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageDataAuthorization')
        })
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/authorization', {
                statusCode: 200,
                body: {
                    "authorizationResults": [
                        {
                            "uuid": "596397ad-0359-43d1-b8c7-fe9eae95bf26",
                            "name": "test chlrophylle",
                            "users": [
                                {
                                    "id": "a5486b95-21f7-4f02-8942-adbd707fcf1b",
                                    "creationDate": 1677508654866,
                                    "updateDate": 1677508654866,
                                    "login": "echo",
                                    "password": "$2a$12$sz6MzU0jQe16yN7xthzYCuUEUThqEHTRzJBXphaqkBergJDpYnQhq",
                                    "authorizations": []
                                }
                            ],
                            "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                            "authorizations": {
                                "phytoplancton": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "delete": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                },
                                "chlorophylle": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "admin": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "delete": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "publication": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                },
                                "zooplancton": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "admin": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "delete": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "lac_d_altitude"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        },
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "riviere"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                }
                            },
                            "publicAuthorizations": {
                                "physico-chimie": {
                                    "extraction": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ]
                                }
                            },
                            "authorizationsForUser": {
                                "authorizationResults": {},
                                "applicationName": "ola",
                                "authorizationByPath": {},
                                "isAdministrator": true
                            }
                        },
                        {
                            "uuid": "479ea4d8-d116-4bbf-8365-c8b7c8552b29",
                            "name": "depot extra phisico",
                            "users": [
                                {
                                    "id": "9032ffe5-bfc1-453d-814e-287cd678484a",
                                    "creationDate": 1677497830455,
                                    "updateDate": 1677497830455,
                                    "login": "_public_",
                                    "password": "",
                                    "authorizations": [
                                        ".*"
                                    ]
                                }
                            ],
                            "application": "36776c27-acf3-4981-a977-7c3c37be0183",
                            "authorizations": {
                                "physico-chimie": {
                                    "extraction": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "path": "not setting",
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": "grand_lac"
                                            },
                                            "fromDay": null,
                                            "toDay": null
                                        }
                                    ]
                                }
                            },
                            "publicAuthorizations": {
                                "physico-chimie": {
                                    "extraction": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ],
                                    "depot": [
                                        {
                                            "timeScope": {
                                                "range": {
                                                    "empty": false
                                                }
                                            },
                                            "dataGroups": [],
                                            "requiredAuthorizations": {
                                                "site": {
                                                    "sql": "grand_lac"
                                                }
                                            }
                                        }
                                    ]
                                }
                            },
                            "authorizationsForUser": {
                                "authorizationResults": {},
                                "applicationName": "ola",
                                "authorizationByPath": {},
                                "isAdministrator": true
                            }
                        }
                    ],
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "authorizationByPath": {},
                        "isAdministrator": true
                    }
                }
            }).as('pageDataAuthorization')

        cy.visit(Cypress.env('ola_dataTypes_authorizations_url'))
        cy.get('.column > .button').contains("Ajouter une autorisation")

        cy.get(':nth-child(1) > [data-label="Actions"] > .is-warning > .icon').click()
        cy.fixture(olaDataType).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=DATATYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageDataAuthorization')
        })

        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/grantable', {
                statusCode: 200,
                body: {
                    "users": [{
                        "id": "9032ffe5-bfc1-453d-814e-287cd678484a",
                        "label": "_public_"
                    }, {
                        "id": "4a77cb9e-f136-47db-83cf-03abd16c8ae2",
                        "label": "echo"
                    }, {"id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9", "label": "poussin"}],
                    "dataGroups": {
                        "condition_prelevements": [{
                            "id": "qualitatif",
                            "label": "Données qualitatives"
                        }, {"id": "quantitatif", "label": "Données quantitatives"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }],
                        "phytoplancton": [{"id": "condition", "label": "Contexte"}, {
                            "id": "donnee",
                            "label": "Donnée"
                        }, {"id": "referentiel", "label": "Référentiel"}],
                        "chlorophylle": [{"id": "condition", "label": "Contexte"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }],
                        "production_primaire": [{"id": "condition", "label": "Contexte"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }],
                        "haute_frequence": [{"id": "all", "label": "Toutes les données"}],
                        "zooplancton": [{"id": "condition", "label": "Contexte"}, {
                            "id": "donnée",
                            "label": "Data"
                        }, {"id": "referentiel", "label": "Référentiel"}],
                        "physico-chimie": [{"id": "condition", "label": "Contexte"}, {
                            "id": "dataGroup_variable",
                            "label": "Données"
                        }, {"id": "referentiel", "label": "Référentiel"}],
                        "sonde_multiparametres": [{
                            "id": "condition_prelevement",
                            "label": "Condition de prélèvement"
                        }, {"id": "donnee_prelevement", "label": "Données du prélèvement"}, {
                            "id": "referentiel",
                            "label": "Référentiel"
                        }]
                    },
                    "authorizationScopes": {
                        "condition_prelevements": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "phytoplancton": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "chlorophylle": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "production_primaire": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "haute_frequence": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "zooplancton": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "physico-chimie": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }],
                        "sonde_multiparametres": [{
                            "id": "projet",
                            "label": "projet",
                            "options": [{"id": "dce", "label": "dce", "children": []}, {
                                "id": "rnt",
                                "label": "rnt",
                                "children": []
                            }, {"id": "sou", "label": "sou", "children": []}, {
                                "id": "suivi_des_lacs",
                                "label": "suivi_des_lacs",
                                "children": []
                            }, {
                                "id": "suivi_des_lacs_sentinelles",
                                "label": "suivi_des_lacs_sentinelles",
                                "children": []
                            }, {"id": "suivi_des_rivieres", "label": "suivi_des_rivieres", "children": []}]
                        }, {
                            "id": "site",
                            "label": "site",
                            "options": [{
                                "id": "grand_lac",
                                "label": "grand_lac",
                                "children": [{
                                    "id": "grand_lac.aiguebelette",
                                    "label": "grand_lac.aiguebelette",
                                    "children": []
                                }, {
                                    "id": "grand_lac.annecy",
                                    "label": "grand_lac.annecy",
                                    "children": []
                                }, {
                                    "id": "grand_lac.bourget",
                                    "label": "grand_lac.bourget",
                                    "children": []
                                }, {"id": "grand_lac.leman", "label": "grand_lac.leman", "children": []}]
                            }, {
                                "id": "lac_d_altitude",
                                "label": "lac_d_altitude",
                                "children": [{
                                    "id": "lac_d_altitude.anterne",
                                    "label": "lac_d_altitude.anterne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aratilles",
                                    "label": "lac_d_altitude.aratilles",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arbu",
                                    "label": "lac_d_altitude.arbu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.arpont",
                                    "label": "lac_d_altitude.arpont",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.aumar",
                                    "label": "lac_d_altitude.aumar",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.barroude",
                                    "label": "lac_d_altitude.barroude",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_bramant",
                                    "label": "lac_d_altitude.blanc_du_bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.blanc_du_carro",
                                    "label": "lac_d_altitude.blanc_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bramant",
                                    "label": "lac_d_altitude.bramant",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_inferieur",
                                    "label": "lac_d_altitude.bresses_inferieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.bresses_superieur",
                                    "label": "lac_d_altitude.bresses_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.brevent",
                                    "label": "lac_d_altitude.brevent",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.corne",
                                    "label": "lac_d_altitude.corne",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cornu",
                                    "label": "lac_d_altitude.cornu",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.cos",
                                    "label": "lac_d_altitude.cos",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.espingo",
                                    "label": "lac_d_altitude.espingo",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.estany_gros",
                                    "label": "lac_d_altitude.estany_gros",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gentau",
                                    "label": "lac_d_altitude.gentau",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.gourg_gaudet",
                                    "label": "lac_d_altitude.gourg_gaudet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.isaby",
                                    "label": "lac_d_altitude.isaby",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.izourt",
                                    "label": "lac_d_altitude.izourt",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.jovet",
                                    "label": "lac_d_altitude.jovet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauvitel",
                                    "label": "lac_d_altitude.lauvitel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.lauzanier",
                                    "label": "lac_d_altitude.lauzanier",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.malrif",
                                    "label": "lac_d_altitude.malrif",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.merlet_superieur",
                                    "label": "lac_d_altitude.merlet_superieur",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.mont_coua",
                                    "label": "lac_d_altitude.mont_coua",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.muzelle",
                                    "label": "lac_d_altitude.muzelle",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.noir_du_carro",
                                    "label": "lac_d_altitude.noir_du_carro",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.oncet",
                                    "label": "lac_d_altitude.oncet",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pave",
                                    "label": "lac_d_altitude.pave",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.petarel",
                                    "label": "lac_d_altitude.petarel",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pisses",
                                    "label": "lac_d_altitude.pisses",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.plan_vianney",
                                    "label": "lac_d_altitude.plan_vianney",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.pormenaz",
                                    "label": "lac_d_altitude.pormenaz",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port___bielh",
                                    "label": "lac_d_altitude.port___bielh",
                                    "children": []
                                }, {
                                    "id": "lac_d_altitude.port_bielh",
                                    "label": "lac_d_altitude.port_bielh",
                                    "children": []
                                }, {"id": "lac_d_altitude.rabuons", "label": "lac_d_altitude.rabuons", "children": []}]
                            }, {
                                "id": "riviere",
                                "label": "riviere",
                                "children": [{
                                    "id": "riviere.bimont",
                                    "label": "riviere.bimont",
                                    "children": []
                                }, {
                                    "id": "riviere.dranse",
                                    "label": "riviere.dranse",
                                    "children": []
                                }, {"id": "riviere.mercube", "label": "riviere.mercube", "children": []}]
                            }]
                        }]
                    },
                    "columnsDescription": {
                        "condition_prelevements": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "phytoplancton": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "chlorophylle": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "production_primaire": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "haute_frequence": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "zooplancton": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "physico-chimie": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        },
                        "sonde_multiparametres": {
                            "admin": {
                                "display": true,
                                "title": "admin",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Delegation", "fr": "Délégation"}
                            },
                            "delete": {
                                "display": true,
                                "title": "delete",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deletion", "fr": "Suppression"}
                            },
                            "depot": {
                                "display": true,
                                "title": "depot",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Deposit", "fr": "Dépôt"}
                            },
                            "extraction": {
                                "display": true,
                                "title": "extraction",
                                "withPeriods": true,
                                "withDataGroups": true,
                                "forPublic": true,
                                "internationalizationName": {"en": "Extraction", "fr": "Extraction"}
                            },
                            "publication": {
                                "display": true,
                                "title": "publication",
                                "withPeriods": false,
                                "withDataGroups": false,
                                "forPublic": false,
                                "internationalizationName": {"en": "Publication", "fr": "Publication"}
                            }
                        }
                    },
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "authorizationByPath": {},
                        "isAdministrator": true
                    },
                    "publicAuthorizations": {}
                }
            }).as('pageDataAuthorization')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/authorization/c858e98b-a60e-4ee8-ae20-1f6814a7e77f', {
                statusCode: 200,
                body: {
                    "uuid": "c858e98b-a60e-4ee8-ae20-1f6814a7e77f",
                    "name": "test chlorophylle",
                    "users": [{
                        "id": "4a77cb9e-f136-47db-83cf-03abd16c8ae2",
                        "creationDate": 1678276702095,
                        "updateDate": 1678276702095,
                        "login": "echo",
                        "password": "$2a$12$t.02Tdiu9gvrBcGAVFK.jubwkiZf/NNDBC4rESaGRATA6WixbscBa",
                        "authorizations": []
                    }],
                    "application": "a7c447b7-42ff-4400-9785-3e6e36d04ae4",
                    "authorizations": {
                        "phytoplancton": {
                            "publication": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "delete": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "extraction": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }]
                        },
                        "chlorophylle": {
                            "publication": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "depot": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "delete": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "extraction": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "admin": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }]
                        },
                        "zooplancton": {
                            "publication": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "depot": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "delete": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "extraction": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "admin": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }]
                        },
                        "physico-chimie": {
                            "depot": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }],
                            "extraction": [{
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "grand_lac"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "lac_d_altitude"},
                                "fromDay": null,
                                "toDay": null
                            }, {
                                "path": "not setting",
                                "dataGroups": [],
                                "requiredAuthorizations": {"site": "riviere"},
                                "fromDay": null,
                                "toDay": null
                            }]
                        }
                    },
                    "publicAuthorizations": {},
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "authorizationByPath": {},
                        "isAdministrator": true
                    }
                }
            }).as('pageDataAuthorization')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/ref_site', {
                statusCode: 200,
                body: {
                    "referenceValues": [{
                        "hierarchicalKey": "grand_lac.aiguebelette",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "aiguebelette",
                        "values": {
                            "__display_en": "Aiguebelette",
                            "__display_fr": "Aiguebelette",
                            "nom du site_en": "Aiguebelette",
                            "nom du site_fr": "Aiguebelette",
                            "nom du site_key": "aiguebelette",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "",
                            "description du site_fr": "",
                            "code sandre du Plan d'eau": "DL61",
                            "code sandre de la Masse d'eau plan d'eau": "V1535003"
                        }
                    }, {
                        "hierarchicalKey": "grand_lac.annecy",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "annecy",
                        "values": {
                            "__display_en": "Annecy",
                            "__display_fr": "Annecy",
                            "nom du site_en": "Annecy",
                            "nom du site_fr": "Annecy",
                            "nom du site_key": "annecy",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "lake of Annecy",
                            "description du site_fr": "lac d' Annecy",
                            "code sandre du Plan d'eau": "DL66",
                            "code sandre de la Masse d'eau plan d'eau": "V1235003"
                        }
                    }, {
                        "hierarchicalKey": "grand_lac.bourget",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bourget",
                        "values": {
                            "__display_en": "Bourget",
                            "__display_fr": "Bourget",
                            "nom du site_en": "Bourget",
                            "nom du site_fr": "Bourget",
                            "nom du site_key": "bourget",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "lake of  Bourget",
                            "description du site_fr": "lac du Bourget",
                            "code sandre du Plan d'eau": "DL60",
                            "code sandre de la Masse d'eau plan d'eau": "V1335003"
                        }
                    }, {
                        "hierarchicalKey": "grand_lac.leman",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "leman",
                        "values": {
                            "__display_en": "Leman(Geneva Lake)",
                            "__display_fr": "Léman",
                            "nom du site_en": "Leman(Geneva Lake)",
                            "nom du site_fr": "Léman",
                            "nom du site_key": "leman",
                            "nom du type de site": "grand_lac",
                            "description du site_en": "Geneva lake (Leman)",
                            "description du site_fr": "lac Léman",
                            "code sandre du Plan d'eau": "DL65",
                            "code sandre de la Masse d'eau plan d'eau": "V03-4003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.anterne",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "anterne",
                        "values": {
                            "__display_en": "Anterne",
                            "__display_fr": "Anterne",
                            "nom du site_en": "Anterne",
                            "nom du site_fr": "Anterne",
                            "nom du site_key": "anterne",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Anterne lake",
                            "description du site_fr": "Lac d'Anterne",
                            "code sandre du Plan d'eau": "DL62",
                            "code sandre de la Masse d'eau plan d'eau": "V0115023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.aratilles",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "aratilles",
                        "values": {
                            "__display_en": "Aratilles",
                            "__display_fr": "Aratilles",
                            "nom du site_en": "Aratilles",
                            "nom du site_fr": "Aratilles",
                            "nom du site_key": "aratilles",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.arbu",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "arbu",
                        "values": {
                            "__display_en": "Arbu",
                            "__display_fr": "Arbu",
                            "nom du site_en": "Arbu",
                            "nom du site_fr": "Arbu",
                            "nom du site_key": "arbu",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O1135003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.arpont",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "arpont",
                        "values": {
                            "__display_en": "Arpont",
                            "__display_fr": "Arpont",
                            "nom du site_en": "Arpont",
                            "nom du site_fr": "Arpont",
                            "nom du site_key": "arpont",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Arpont Lake",
                            "description du site_fr": "Lac de l'Arpont",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1015003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.aumar",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "aumar",
                        "values": {
                            "__display_en": "Aumar",
                            "__display_fr": "Aumar",
                            "nom du site_en": "Aumar",
                            "nom du site_fr": "Aumar",
                            "nom du site_key": "aumar",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "This lake is located in the French pyrénées at 2192 m in the Néouvielle Reserve. This lake belongs to the EDF hydropower scheme of Pragnères",
                            "description du site_fr": "Ce lac est situé pyrénées française à une altitude de 2192 m au cœur de la réserve naturelle de Néouvielle. Il fait partie de l'aménagement hydroélectrique de Pragnères (EDF) où ses eaux sont turbinées",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0115123"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.barroude",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "barroude",
                        "values": {
                            "__display_en": "Barroude",
                            "__display_fr": "Barroude",
                            "nom du site_en": "Barroude",
                            "nom du site_fr": "Barroude",
                            "nom du site_key": "barroude",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0105023 (grand) et O0105013 (petit)"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.blanc_du_bramant",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "blanc_du_bramant",
                        "values": {
                            "__display_en": "Blanc du Bramant",
                            "__display_fr": "Blanc du Bramant",
                            "nom du site_en": "Blanc du Bramant",
                            "nom du site_fr": "Blanc du Bramant",
                            "nom du site_key": "blanc du bramant",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Blanc du Bramant Lake",
                            "description du site_fr": "lac Blanc du Bramant",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.blanc_du_carro",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "blanc_du_carro",
                        "values": {
                            "__display_en": "Blanc du Carro",
                            "__display_fr": "Blanc du Carro",
                            "nom du site_en": "Blanc du Carro",
                            "nom du site_fr": "Blanc du Carro",
                            "nom du site_key": "blanc du carro",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Carro Blanc lake",
                            "description du site_fr": "Lac Blanc du Caro",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1005043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.bramant",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bramant",
                        "values": {
                            "__display_en": "Bramant",
                            "__display_fr": "Bramant",
                            "nom du site_en": "Bramant",
                            "nom du site_fr": "Bramant",
                            "nom du site_key": "bramant",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Bramant Lake",
                            "description du site_fr": "Lac Bramant",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.bresses_inferieur",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bresses_inferieur",
                        "values": {
                            "__display_en": "Bresses inférieur",
                            "__display_fr": "Bresses inférieur",
                            "nom du site_en": "Bresses inférieur",
                            "nom du site_fr": "Bresses inférieur",
                            "nom du site_key": "bresses inferieur",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lower Bresse lake",
                            "description du site_fr": "lac de Bresses inférieur",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Y6225043 bis"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.bresses_superieur",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bresses_superieur",
                        "values": {
                            "__display_en": "Bresses supérieur",
                            "__display_fr": "Bresses supérieur",
                            "nom du site_en": "Bresses supérieur",
                            "nom du site_fr": "Bresses supérieur",
                            "nom du site_key": "bresses superieur",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Upper Bresse lake",
                            "description du site_fr": "lac de Bresses supérieur",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Y6225043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.brevent",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "brevent",
                        "values": {
                            "__display_en": "Brevent",
                            "__display_fr": "Brévent",
                            "nom du site_en": "Brevent",
                            "nom du site_fr": "Brévent",
                            "nom du site_key": "brevent",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Brevent lake",
                            "description du site_fr": "Lac du Brévent",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0015023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.corne",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "corne",
                        "values": {
                            "__display_en": "Corne",
                            "__display_fr": "Corne",
                            "nom du site_en": "Corne",
                            "nom du site_fr": "Corne",
                            "nom du site_key": "corne",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Corne lake",
                            "description du site_fr": "Lac de Corne",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2755063"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.cornu",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "cornu",
                        "values": {
                            "__display_en": "Cornu",
                            "__display_fr": "Cornu",
                            "nom du site_en": "Cornu",
                            "nom du site_fr": "Cornu",
                            "nom du site_key": "cornu",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Cornu lake",
                            "description du site_fr": "Lac Cornu",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0015043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.cos",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "cos",
                        "values": {
                            "__display_en": "Cos",
                            "__display_fr": "Cos",
                            "nom du site_en": "Cos",
                            "nom du site_fr": "Cos",
                            "nom du site_key": "cos",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Cos lake",
                            "description du site_fr": "lac de Cos",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1205063"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.espingo",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "espingo",
                        "values": {
                            "__display_en": "Espingo",
                            "__display_fr": "Espingo",
                            "nom du site_en": "Espingo",
                            "nom du site_fr": "Espingo",
                            "nom du site_key": "espingo",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.estany_gros",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "estany_gros",
                        "values": {
                            "__display_en": "Estany Gros",
                            "__display_fr": "Estany Gros",
                            "nom du site_en": "Estany Gros",
                            "nom du site_fr": "Estany Gros",
                            "nom du site_key": "estany gros",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.gentau",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "gentau",
                        "values": {
                            "__display_en": "Gentau",
                            "__display_fr": "Gentau",
                            "nom du site_en": "Gentau",
                            "nom du site_fr": "Gentau",
                            "nom du site_key": "gentau",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.gourg_gaudet",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "gourg_gaudet",
                        "values": {
                            "__display_en": "Gourg Gaudet",
                            "__display_fr": "Gourg Gaudet",
                            "nom du site_en": "Gourg Gaudet",
                            "nom du site_fr": "Gourg Gaudet",
                            "nom du site_key": "gourg gaudet",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.isaby",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "isaby",
                        "values": {
                            "__display_en": "Isaby",
                            "__display_fr": "Isaby",
                            "nom du site_en": "Isaby",
                            "nom du site_fr": "Isaby",
                            "nom du site_key": "isaby",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Isaby lake",
                            "description du site_fr": "lac d'Isaby",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Q4425003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.izourt",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "izourt",
                        "values": {
                            "__display_en": "Izourt",
                            "__display_fr": "Izourt",
                            "nom du site_en": "Izourt",
                            "nom du site_fr": "Izourt",
                            "nom du site_key": "izourt",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lake Izourt",
                            "description du site_fr": "lac d'Izourt",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O1125103"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.jovet",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "jovet",
                        "values": {
                            "__display_en": "Jovet",
                            "__display_fr": "Jovet",
                            "nom du site_en": "Jovet",
                            "nom du site_fr": "Jovet",
                            "nom du site_key": "jovet",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Jovet lake",
                            "description du site_fr": "Lac Jovet",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0025023 et V0025003 (lacs)"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.lauvitel",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "lauvitel",
                        "values": {
                            "__display_en": "Lauvitel",
                            "__display_fr": "Lauvitel",
                            "nom du site_en": "Lauvitel",
                            "nom du site_fr": "Lauvitel",
                            "nom du site_key": "lauvitel",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lauvitel lake",
                            "description du site_fr": "Lac de Lauvitel",
                            "code sandre du Plan d'eau": "DL76",
                            "code sandre de la Masse d'eau plan d'eau": "DL76"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.lauzanier",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "lauzanier",
                        "values": {
                            "__display_en": "Lauzanier",
                            "__display_fr": "Lauzanier",
                            "nom du site_en": "Lauzanier",
                            "nom du site_fr": "Lauzanier",
                            "nom du site_key": "lauzanier",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Lauzanier lake",
                            "description du site_fr": "lac du Lauzanier",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "X0415043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.malrif",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "malrif",
                        "values": {
                            "__display_en": "Malrif",
                            "__display_fr": "Malrif",
                            "nom du site_en": "Malrif",
                            "nom du site_fr": "Malrif",
                            "nom du site_key": "malrif",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Malrif lake",
                            "description du site_fr": "lac de Malrif",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "X0215003 (petit laus), X0215023 (grand laus), Mezan ?"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.merlet_superieur",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "merlet_superieur",
                        "values": {
                            "__display_en": "Merlet supérieur",
                            "__display_fr": "Merlet supérieur",
                            "nom du site_en": "Merlet supérieur",
                            "nom du site_fr": "Merlet supérieur",
                            "nom du site_key": "merlet superieur",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Upper Merlet lake",
                            "description du site_fr": "Lac du Merlet supérieur",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W0225003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.mont_coua",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "mont_coua",
                        "values": {
                            "__display_en": "Mont Coua",
                            "__display_fr": "Mont Coua",
                            "nom du site_en": "Mont Coua",
                            "nom du site_fr": "Mont Coua",
                            "nom du site_key": "mont coua",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Coua mont lake",
                            "description du site_fr": "lac du Mont Coua",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W0235063"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.muzelle",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "muzelle",
                        "values": {
                            "__display_en": "Muzelle",
                            "__display_fr": "Muzelle",
                            "nom du site_en": "Muzelle",
                            "nom du site_fr": "Muzelle",
                            "nom du site_key": "muzelle",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Muzelle lake",
                            "description du site_fr": "lac de la Muzelle",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2735043"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.noir_du_carro",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "noir_du_carro",
                        "values": {
                            "__display_en": "Noir du Carro",
                            "__display_fr": "Noir du Carro",
                            "nom du site_en": "Noir du Carro",
                            "nom du site_fr": "Noir du Carro",
                            "nom du site_key": "noir du carro",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Carro Noir lake",
                            "description du site_fr": "lac Noir du Caro",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W1005023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.oncet",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "oncet",
                        "values": {
                            "__display_en": "Oncet",
                            "__display_fr": "Oncet",
                            "nom du site_en": "Oncet",
                            "nom du site_fr": "Oncet",
                            "nom du site_key": "oncet",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Oncet lake",
                            "description du site_fr": "lac d'Oncet",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Q4305003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.pave",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "pave",
                        "values": {
                            "__display_en": "Pavé",
                            "__display_fr": "Pavé",
                            "nom du site_en": "Pavé",
                            "nom du site_fr": "Pavé",
                            "nom du site_key": "pave",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Pave lake",
                            "description du site_fr": "lac du Pavé",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2705003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.petarel",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "petarel",
                        "values": {
                            "__display_en": "Pétarel",
                            "__display_fr": "Pétarel",
                            "nom du site_en": "Pétarel",
                            "nom du site_fr": "Pétarel",
                            "nom du site_key": "petarel",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Petarel lake",
                            "description du site_fr": "Lac Pétarel",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2115023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.pisses",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "pisses",
                        "values": {
                            "__display_en": "Pisses",
                            "__display_fr": "Pisses",
                            "nom du site_en": "Pisses",
                            "nom du site_fr": "Pisses",
                            "nom du site_key": "pisses",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Pisses lake",
                            "description du site_fr": "Lac des Pisses",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2005023"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.plan_vianney",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "plan_vianney",
                        "values": {
                            "__display_en": "Plan Vianney",
                            "__display_fr": "Plan Vianney",
                            "nom du site_en": "Plan Vianney",
                            "nom du site_fr": "Plan Vianney",
                            "nom du site_key": "plan vianney",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Plan Vianney Lake",
                            "description du site_fr": "Lac de Plan Vianney",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "W2735003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.pormenaz",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "pormenaz",
                        "values": {
                            "__display_en": "Pormenaz",
                            "__display_fr": "Pormenaz",
                            "nom du site_en": "Pormenaz",
                            "nom du site_fr": "Pormenaz",
                            "nom du site_key": "pormenaz",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Pormenaz lake",
                            "description du site_fr": "Lac de Pormenaz",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0015003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.port___bielh",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "port___bielh",
                        "values": {
                            "__display_en": "Port Bielh",
                            "__display_fr": "Port Bielh",
                            "nom du site_en": "Port Bielh",
                            "nom du site_fr": "Port Bielh",
                            "nom du site_key": "port   bielh",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0115002"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.port_bielh",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "port_bielh",
                        "values": {
                            "__display_en": "Port-Bielh",
                            "__display_fr": "Port-Bielh",
                            "nom du site_en": "Port-Bielh",
                            "nom du site_fr": "Port-Bielh",
                            "nom du site_key": "port bielh",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "french pyrenes lake",
                            "description du site_fr": "lac des pyrénées française",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "O0115003"
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude.rabuons",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "rabuons",
                        "values": {
                            "__display_en": "Rabuons",
                            "__display_fr": "Rabuons",
                            "nom du site_en": "Rabuons",
                            "nom du site_fr": "Rabuons",
                            "nom du site_key": "rabuons",
                            "nom du type de site": "lac_d_altitude",
                            "description du site_en": "Rabuons lake",
                            "description du site_fr": "lac du Rabuons",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "Y6205283"
                        }
                    }, {
                        "hierarchicalKey": "riviere.bimont",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "bimont",
                        "values": {
                            "__display_en": "Bimont",
                            "__display_fr": "Bimont",
                            "nom du site_en": "Bimont",
                            "nom du site_fr": "Bimont",
                            "nom du site_key": "bimont",
                            "nom du type de site": "riviere",
                            "description du site_en": "Bimont river",
                            "description du site_fr": "rivière Bimont",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": ""
                        }
                    }, {
                        "hierarchicalKey": "riviere.dranse",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "dranse",
                        "values": {
                            "__display_en": "Dranse",
                            "__display_fr": "Dranse",
                            "nom du site_en": "Dranse",
                            "nom du site_fr": "Dranse",
                            "nom du site_key": "dranse",
                            "nom du type de site": "riviere",
                            "description du site_en": "Dranse river",
                            "description du site_fr": "rivière Dranse",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V03-0400 ou V0321430"
                        }
                    }, {
                        "hierarchicalKey": "riviere.mercube",
                        "hierarchicalReference": "site_type.ref_site",
                        "naturalKey": "mercube",
                        "values": {
                            "__display_en": "Mercube",
                            "__display_fr": "Mercube",
                            "nom du site_en": "Mercube",
                            "nom du site_fr": "Mercube",
                            "nom du site_key": "mercube",
                            "nom du type de site": "riviere",
                            "description du site_en": "Mercube river",
                            "description du site_fr": "rivière Mercube",
                            "code sandre du Plan d'eau": "",
                            "code sandre de la Masse d'eau plan d'eau": "V0351440"
                        }
                    }]
                }
            }).as('pageDataAuthorization')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/project', {
                statusCode: 200,
                body: {
                    "referenceValues": [{
                        "hierarchicalKey": "dce",
                        "hierarchicalReference": "project",
                        "naturalKey": "dce",
                        "values": {
                            "__display_en": "DCE",
                            "__display_fr": "DCE",
                            "nom du projet_en": "DCE",
                            "nom du projet_fr": "DCE",
                            "nom du projet_key": "dce",
                            "description du projet_en": "Sampling realized with DCE protocol",
                            "description du projet_fr": "Prélèvement faits selon le protocole de la DCE"
                        }
                    }, {
                        "hierarchicalKey": "rnt",
                        "hierarchicalReference": "project",
                        "naturalKey": "rnt",
                        "values": {
                            "__display_en": "RNT",
                            "__display_fr": "RNT",
                            "nom du projet_en": "RNT",
                            "nom du projet_fr": "RNT",
                            "nom du projet_key": "RNT",
                            "description du projet_en": "",
                            "description du projet_fr": ""
                        }
                    }, {
                        "hierarchicalKey": "sou",
                        "hierarchicalReference": "project",
                        "naturalKey": "sou",
                        "values": {
                            "__display_en": "SOU",
                            "__display_fr": "SOU",
                            "nom du projet_en": "SOU",
                            "nom du projet_fr": "SOU",
                            "nom du projet_key": "SOU",
                            "description du projet_en": "",
                            "description du projet_fr": ""
                        }
                    }, {
                        "hierarchicalKey": "suivi_des_lacs",
                        "hierarchicalReference": "project",
                        "naturalKey": "suivi_des_lacs",
                        "values": {
                            "__display_en": "Lakes monitoring",
                            "__display_fr": "Suivi des lacs",
                            "nom du projet_en": "Lakes monitoring",
                            "nom du projet_fr": "Suivi des lacs",
                            "nom du projet_key": "suivi des lacs",
                            "description du projet_en": "Long-term monitoring of peri-alpine lakes",
                            "description du projet_fr": "Suivi è long terme des lacs pèri-alpins"
                        }
                    }, {
                        "hierarchicalKey": "suivi_des_lacs_sentinelles",
                        "hierarchicalReference": "project",
                        "naturalKey": "suivi_des_lacs_sentinelles",
                        "values": {
                            "__display_en": "Sentinels lakes monitoring",
                            "__display_fr": "Suivi des lacs sentinelles",
                            "nom du projet_en": "Sentinels lakes monitoring",
                            "nom du projet_fr": "Suivi des lacs sentinelles",
                            "nom du projet_key": "suivi des lacs sentinelles",
                            "description du projet_en": "Long-term monitoring of altitudes lakes",
                            "description du projet_fr": "Suivi è long terme des lacs d'altitude"
                        }
                    }, {
                        "hierarchicalKey": "suivi_des_rivieres",
                        "hierarchicalReference": "project",
                        "naturalKey": "suivi_des_rivieres",
                        "values": {
                            "__display_en": "Rivers monitoring",
                            "__display_fr": "Suivi des rivières",
                            "nom du projet_en": "Rivers monitoring",
                            "nom du projet_fr": "Suivi des rivières",
                            "nom du projet_key": "suivi des rivieres",
                            "description du projet_en": "river's sampling",
                            "description du projet_fr": "Prélèvement en rivière(s)"
                        }
                    }]
                }
            }).as('pageDataAuthorization')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/site_type', {
                statusCode: 200,
                body: {
                    "referenceValues": [{
                        "hierarchicalKey": "grand_lac",
                        "hierarchicalReference": "site_type",
                        "naturalKey": "grand_lac",
                        "values": {
                            "nom_en": "large lake",
                            "nom_fr": "grand lac",
                            "nom_key": "grand_lac",
                            "code sandre": "",
                            "__display_en": "large lake",
                            "__display_fr": "grand lac",
                            "description_en": "Alpine great lake from SOERE",
                            "description_fr": "Grand lac péri alpins du SOERE",
                            "code sandre du contexte": ""
                        }
                    }, {
                        "hierarchicalKey": "lac_d_altitude",
                        "hierarchicalReference": "site_type",
                        "naturalKey": "lac_d_altitude",
                        "values": {
                            "nom_en": "altitude lake",
                            "nom_fr": "lac d altitude",
                            "nom_key": "lac_d_altitude",
                            "code sandre": "",
                            "__display_en": "altitude lake",
                            "__display_fr": "lac d altitude",
                            "description_en": "altitude lake",
                            "description_fr": "lac d altitude",
                            "code sandre du contexte": ""
                        }
                    }, {
                        "hierarchicalKey": "riviere",
                        "hierarchicalReference": "site_type",
                        "naturalKey": "riviere",
                        "values": {
                            "nom_en": "river",
                            "nom_fr": "rivière",
                            "nom_key": "riviere",
                            "code sandre": "",
                            "__display_en": "river",
                            "__display_fr": "rivière",
                            "description_en": "a watershed of a large lake river",
                            "description_fr": "rivière du bassin versant d un grand lac",
                            "code sandre du contexte": ""
                        }
                    }]
                }
            }).as('pageDataAuthorization')

        cy.visit(Cypress.env('ola_dataTypes_update_authorizations_url'))
    })
})