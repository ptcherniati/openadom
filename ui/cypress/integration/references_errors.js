const errors = require("../fixtures/applications/errors/ref_ola_errors.json");
describe('test authorization application', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it('Test references ola', () => {

        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        const ola = 'references/ola/ola.json'
        cy.fixture(ola).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageRef')

        })
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references', {
                statusCode: 200,
                body: {
                    "id": "20a6b24b-ac4e-4cee-a21d-02bb75d7ab1d",
                    "name": "ola",
                    "title": "ola",
                    "comment": "",
                    "internationalization": {
                        "application": {
                            "internationalizationName": {
                                "en": "Lake's observatory",
                                "fr": "Observatoire des lacs"
                            }
                        }, "references": {
                            "controle_coherence": {
                                "internationalizationName": {
                                    "en": "Controle de la cohérence",
                                    "fr": "Controle de la cohérence"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "taxon_phytoplancton": {
                                "internationalizationName": {
                                    "en": "taxon's phytoplanctons",
                                    "fr": "taxon des phytoplanctons"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {
                                    "proprietes_taxon": {
                                        "en": "Properties of Taxa",
                                        "fr": "Proprétés de Taxons"
                                    }
                                },
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du niveau de taxon}: {nom du taxon superieur}.{nom du taxon déterminé}",
                                        "fr": "{nom du niveau de taxon}: {nom du taxon superieur}.{nom du taxon déterminé}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "site_type": {
                                "internationalizationName": {"en": "Sites types", "fr": "Types de site"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {}
                            },
                            "stade_développement_zoo": {
                                "internationalizationName": {
                                    "en": "Stage of development",
                                    "fr": "Stade de développement"
                                },
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "project": {
                                "internationalizationName": {"en": "Project", "fr": "Projet"},
                                "internationalizedColumns": {
                                    "nom du projet_key": {
                                        "en": "nom du projet_en",
                                        "fr": "nom du projet_fr"
                                    },
                                    "description du projet_fr": {
                                        "en": "description du projet_en",
                                        "fr": "description du projet_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du projet_key}",
                                        "fr": "{nom du projet_key}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "valeurs_qualitative": {
                                "internationalizationName": {
                                    "en": "Qualitative values",
                                    "fr": "Valeurs qualitatives"
                                },
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "valeur_key": {"en": "valeur_en", "fr": "valeur_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "variable_norm": {
                                "internationalizationName": {
                                    "en": "Variables' norms",
                                    "fr": "Normes de variable"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                "internationalizedValidations": {}
                            },
                            "propriete_taxon": {
                                "internationalizationName": {
                                    "en": "Proporties of taxons",
                                    "fr": "Propiété des taxons"
                                },
                                "internationalizedColumns": {
                                    "définition_fr": {
                                        "en": "définition_en",
                                        "fr": "définition_fr"
                                    },
                                    "nom de la propriété_key": {
                                        "en": "nom de la propriété_en",
                                        "fr": "nom de la propriété_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de la propriété_key}",
                                        "fr": "{nom de la propriété_key}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "tool": {
                                "internationalizationName": {"en": "Measuring tool", "fr": "Outils de mesure"},
                                "internationalizedColumns": {
                                    "description_fr": {
                                        "en": "description_en",
                                        "fr": "description_fr"
                                    },
                                    "nom de l_outil de mesure_fr": {
                                        "en": "nom de l_outil de mesure_en",
                                        "fr": "nom de l_outil de mesure_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "platform": {
                                "internationalizationName": {"en": "Plateforms", "fr": "Plateformes"},
                                "internationalizedColumns": {
                                    "nom de la plateforme_key": {
                                        "en": "nom de la plateforme_en",
                                        "fr": "nom de la plateforme_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de la plateforme_key}",
                                        "fr": "{nom de la plateforme_key}"
                                    }
                                },
                                "internationalizedValidations": {"format_float": {"fr": "latitude,longitude,altitude au format flottant obligatoire"}}
                            },
                            "site": {
                                "internationalizationName": {"en": "Site", "fr": "Site"},
                                "internationalizedColumns": {
                                    "nom du site_key": {
                                        "en": "nom du site_en",
                                        "fr": "nom du site_fr"
                                    },
                                    "description du site_fr": {
                                        "en": "description du site_en",
                                        "fr": "description du site_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du site_key}",
                                        "fr": "{nom du site_key}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "unit": {
                                "internationalizationName": {"en": "Units", "fr": "Unités"},
                                "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {}
                            },
                            "tool_type": {
                                "internationalizationName": {"en": "Tools type", "fr": "Type d'outils"},
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "platform_type": {
                                "internationalizationName": {
                                    "en": "Plateform types",
                                    "fr": "Types de plateforme"
                                },
                                "internationalizedColumns": {
                                    "description_fr": {
                                        "en": "description_en",
                                        "fr": "description_fr"
                                    },
                                    "nom du type de plateforme_key": {
                                        "en": "nom du type de plateforme_en",
                                        "fr": "nom du type de plateforme_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du type de plateforme_key}",
                                        "fr": "{nom du type de plateforme_key}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "file_type": {
                                "internationalizationName": {"en": "Type's Files", "fr": "Type de fichier"},
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                "internationalizedValidations": {}
                            },
                            "variable": {
                                "internationalizationName": {"en": "Variables", "fr": "Variables"},
                                "internationalizedColumns": {
                                    "définition_fr": {
                                        "en": "définition_en",
                                        "fr": "définition_fr"
                                    },
                                    "nom de la variable_fr": {
                                        "en": "nom de la variable_en",
                                        "fr": "nom de la variable_fr"
                                    },
                                    "Affichage de la variable_fr": {
                                        "en": "Affichage de la variable_en",
                                        "fr": "Affichage de la variable_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "data_type": {
                                "internationalizationName": {"en": "Data type", "fr": "Types de données"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "niveau_taxon": {
                                "internationalizationName": {
                                    "en": "Level of taxon",
                                    "fr": "Niveau de taxon"
                                },
                                "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {}
                            },
                            "variable_group": {
                                "internationalizationName": {
                                    "en": "Variable's groups",
                                    "fr": "Groupes de variable"
                                },
                                "internationalizedColumns": {
                                    "nom du groupe": {
                                        "en": "nom du groupe_en",
                                        "fr": "nom du groupe_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du groupe_en}",
                                        "fr": "{nom du groupe_fr}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "taxon_zooplancton": {
                                "internationalizationName": {
                                    "en": "taxon's zooplancton",
                                    "fr": "taxon des zooplancton"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du niveau de taxon}: {nom du taxon superieur}.{nom du taxon déterminé}",
                                        "fr": "{nom du niveau de taxon}: {nom du taxon superieur}.{nom du taxon déterminé}"
                                    }
                                },
                                "internationalizedValidations": {}
                            },
                            "thematic": {
                                "internationalizationName": {"en": "Thematic", "fr": "Thème"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {}
                            }
                        }, "dataTypes": {
                            "condition_prelevements": {
                                "internationalizationName": {
                                    "en": "Collection condition",
                                    "fr": "Condition de prélèvement"
                                },
                                "internationalizedColumns": null,
                                "authorization": {
                                    "dataGroups": {
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            }
                                        },
                                        "qualitatif": {
                                            "internationalizationName": {
                                                "en": "Qualitative data",
                                                "fr": "Données qualitatives"
                                            }
                                        },
                                        "quantitatif": {
                                            "internationalizationName": {
                                                "en": "Quantitative data",
                                                "fr": "Données quantitatives"
                                            }
                                        }
                                    },
                                    "authorizationScopes": {
                                        "localization_site": {"internationalizationName": null},
                                        "localization_projet": {"internationalizationName": null}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "physico-chimie": {
                                "internationalizationName": {
                                    "en": "Chemical Physics",
                                    "fr": "Physico Chimie"
                                },
                                "internationalizedColumns": null,
                                "authorization": {
                                    "dataGroups": {
                                        "condition": {
                                            "internationalizationName": {
                                                "en": "Context",
                                                "fr": "Contexte"
                                            }
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            }
                                        },
                                        "variable": {"internationalizationName": {"en": "Data", "fr": "Données"}}
                                    },
                                    "authorizationScopes": {
                                        "localization_site": {"internationalizationName": null},
                                        "localization_projet": {"internationalizationName": null}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "sonde_multiparametres": {
                                "internationalizationName": {
                                    "en": "Probe data",
                                    "fr": "Données des sondes"
                                },
                                "internationalizedColumns": null,
                                "authorization": {
                                    "dataGroups": {
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            }
                                        },
                                        "condition_prelevement": {
                                            "internationalizationName": {
                                                "en": "Prelevement's condition",
                                                "fr": "Condition de prélèvement"
                                            }
                                        },
                                        "donnee_prelevement": {
                                            "internationalizationName": {
                                                "en": "Data's condition",
                                                "fr": "Données du prélèvement"
                                            }
                                        }
                                    },
                                    "authorizationScopes": {
                                        "localization_site": {"internationalizationName": null},
                                        "localization_projet": {"internationalizationName": null}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            }
                        }
                    },
                    "references": {
                        "stade_développement_zoo": {
                            "id": "stade_développement_zoo",
                            "label": "stade_développement_zoo",
                            "children": [],
                            "columns": {
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null},
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_en": {
                                    "id": "description_en",
                                    "title": "description_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_fr": {
                                    "id": "description_fr",
                                    "title": "description_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "valeurs_qualitative": {
                            "id": "valeurs_qualitative",
                            "label": "valeurs_qualitative",
                            "children": [],
                            "columns": {
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null},
                                "valeur_en": {"id": "valeur_en", "title": "valeur_en", "key": false, "linkedTo": null},
                                "valeur_fr": {"id": "valeur_fr", "title": "valeur_fr", "key": false, "linkedTo": null},
                                "valeur_key": {"id": "valeur_key", "title": "valeur_key", "key": true, "linkedTo": null}
                            },
                            "dynamicColumns": {}
                        },
                        "niveau_taxon": {
                            "id": "niveau_taxon",
                            "label": "niveau_taxon",
                            "children": [],
                            "columns": {
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null},
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "propriete_taxon": {
                            "id": "propriete_taxon",
                            "label": "propriete_taxon",
                            "children": [],
                            "columns": {
                                "isFloatValue": {
                                    "id": "isFloatValue",
                                    "title": "isFloatValue",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "isQualitative": {
                                    "id": "isQualitative",
                                    "title": "isQualitative",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "type associé": {
                                    "id": "type associé",
                                    "title": "type associé",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "définition_en": {
                                    "id": "définition_en",
                                    "title": "définition_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "définition_fr": {
                                    "id": "définition_fr",
                                    "title": "définition_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "ordre d'affichage": {
                                    "id": "ordre d'affichage",
                                    "title": "ordre d'affichage",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la propriété_en": {
                                    "id": "nom de la propriété_en",
                                    "title": "nom de la propriété_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la propriété_fr": {
                                    "id": "nom de la propriété_fr",
                                    "title": "nom de la propriété_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la propriété_key": {
                                    "id": "nom de la propriété_key",
                                    "title": "nom de la propriété_key",
                                    "key": true,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "taxon_phytoplancton": {
                            "id": "taxon_phytoplancton",
                            "label": "taxon_phytoplancton",
                            "children": [],
                            "columns": {
                                "theme": {"id": "theme", "title": "theme", "key": false, "linkedTo": null},
                                "Code Sandre": {
                                    "id": "Code Sandre",
                                    "title": "Code Sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Notes libres": {
                                    "id": "Notes libres",
                                    "title": "Notes libres",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Synonyme ancien": {
                                    "id": "Synonyme ancien",
                                    "title": "Synonyme ancien",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Synonyme récent": {
                                    "id": "Synonyme récent",
                                    "title": "Synonyme récent",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du taxon": {
                                    "id": "code sandre du taxon",
                                    "title": "code sandre du taxon",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du niveau de taxon": {
                                    "id": "nom du niveau de taxon",
                                    "title": "nom du niveau de taxon",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du taxon superieur": {
                                    "id": "nom du taxon superieur",
                                    "title": "nom du taxon superieur",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Année de la description": {
                                    "id": "Année de la description",
                                    "title": "Année de la description",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Auteur de la description": {
                                    "id": "Auteur de la description",
                                    "title": "Auteur de la description",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du taxon déterminé": {
                                    "id": "nom du taxon déterminé",
                                    "title": "nom du taxon déterminé",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "Classe algale sensu Bourrelly": {
                                    "id": "Classe algale sensu Bourrelly",
                                    "title": "Classe algale sensu Bourrelly",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Référence de la description": {
                                    "id": "Référence de la description",
                                    "title": "Référence de la description",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du taxon supérieur": {
                                    "id": "code sandre du taxon supérieur",
                                    "title": "code sandre du taxon supérieur",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Références relatives à ce taxon": {
                                    "id": "Références relatives à ce taxon",
                                    "title": "Références relatives à ce taxon",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "niveau incertitude de détermination": {
                                    "id": "niveau incertitude de détermination",
                                    "title": "niveau incertitude de détermination",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {
                                "proprietes_taxon": {
                                    "id": "proprietes_taxon",
                                    "title": "proprietes_taxon",
                                    "headerPrefix": "pt_",
                                    "reference": "propriete_taxon",
                                    "referenceColumnToLookForHeader": "nom de la propriété_key",
                                    "presenceConstraint": true
                                }
                            }
                        },
                        "data_type": {
                            "id": "data_type",
                            "label": "data_type",
                            "children": [],
                            "columns": {
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null},
                                "description_en": {
                                    "id": "description_en",
                                    "title": "description_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_fr": {
                                    "id": "description_fr",
                                    "title": "description_fr",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "controle_coherence": {
                            "id": "controle_coherence",
                            "label": "controle_coherence",
                            "children": [],
                            "columns": {
                                "valeur max": {
                                    "id": "valeur max",
                                    "title": "valeur max",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "valeur min": {
                                    "id": "valeur min",
                                    "title": "valeur min",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du site": {
                                    "id": "nom du site",
                                    "title": "nom du site",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom de la variable": {
                                    "id": "nom de la variable",
                                    "title": "nom de la variable",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du type de données": {
                                    "id": "nom du type de données",
                                    "title": "nom du type de données",
                                    "key": true,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "taxon_zooplancton": {
                            "id": "taxon_zooplancton",
                            "label": "taxon_zooplancton",
                            "children": [],
                            "columns": {
                                "theme": {"id": "theme", "title": "theme", "key": false, "linkedTo": null},
                                "preselected": {
                                    "id": "preselected",
                                    "title": "preselected",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du taxon": {
                                    "id": "code sandre du taxon",
                                    "title": "code sandre du taxon",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du niveau de taxon": {
                                    "id": "nom du niveau de taxon",
                                    "title": "nom du niveau de taxon",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du taxon superieur": {
                                    "id": "nom du taxon superieur",
                                    "title": "nom du taxon superieur",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du taxon déterminé": {
                                    "id": "nom du taxon déterminé",
                                    "title": "nom du taxon déterminé",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "code sandre du taxon supérieur": {
                                    "id": "code sandre du taxon supérieur",
                                    "title": "code sandre du taxon supérieur",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "file_type": {
                            "id": "file_type",
                            "label": "file_type",
                            "children": [],
                            "columns": {
                                "nom": {"id": "nom", "title": "nom", "key": true, "linkedTo": null},
                                "description": {
                                    "id": "description",
                                    "title": "description",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "variable_norm": {
                            "id": "variable_norm",
                            "label": "variable_norm",
                            "children": [],
                            "columns": {
                                "nom": {"id": "nom", "title": "nom", "key": true, "linkedTo": null},
                                "définition": {
                                    "id": "définition",
                                    "title": "définition",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "variable_group": {
                            "id": "variable_group",
                            "label": "variable_group",
                            "children": [],
                            "columns": {
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du groupe": {
                                    "id": "nom du groupe",
                                    "title": "nom du groupe",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du groupe_en": {
                                    "id": "nom du groupe_en",
                                    "title": "nom du groupe_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du groupe_fr": {
                                    "id": "nom du groupe_fr",
                                    "title": "nom du groupe_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du groupe parent": {
                                    "id": "nom du groupe parent",
                                    "title": "nom du groupe parent",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "variable": {
                            "id": "variable", "label": "variable", "children": [], "columns": {
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du groupe": {
                                    "id": "nom du groupe",
                                    "title": "nom du groupe",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "définition_en": {
                                    "id": "définition_en",
                                    "title": "définition_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "définition_fr": {
                                    "id": "définition_fr",
                                    "title": "définition_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "ordre daffichage": {
                                    "id": "ordre daffichage",
                                    "title": "ordre daffichage",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "valeur  qualitative": {
                                    "id": "valeur  qualitative",
                                    "title": "valeur  qualitative",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la variable_en": {
                                    "id": "nom de la variable_en",
                                    "title": "nom de la variable_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la variable_fr": {
                                    "id": "nom de la variable_fr",
                                    "title": "nom de la variable_fr",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Affichage de la variable_en": {
                                    "id": "Affichage de la variable_en",
                                    "title": "Affichage de la variable_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "Affichage de la variable_fr": {
                                    "id": "Affichage de la variable_fr",
                                    "title": "Affichage de la variable_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la norme de variable": {
                                    "id": "nom de la norme de variable",
                                    "title": "nom de la norme de variable",
                                    "key": false,
                                    "linkedTo": null
                                }
                            }, "dynamicColumns": {}
                        },
                        "thematic": {
                            "id": "thematic",
                            "label": "thematic",
                            "children": [],
                            "columns": {
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null},
                                "description_en": {
                                    "id": "description_en",
                                    "title": "description_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_fr": {
                                    "id": "description_fr",
                                    "title": "description_fr",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "platform_type": {
                            "id": "platform_type",
                            "label": "platform_type",
                            "children": [],
                            "columns": {
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_en": {
                                    "id": "description_en",
                                    "title": "description_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_fr": {
                                    "id": "description_fr",
                                    "title": "description_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du type de plateforme_en": {
                                    "id": "nom du type de plateforme_en",
                                    "title": "nom du type de plateforme_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du type de plateforme_fr": {
                                    "id": "nom du type de plateforme_fr",
                                    "title": "nom du type de plateforme_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du type de plateforme_key": {
                                    "id": "nom du type de plateforme_key",
                                    "title": "nom du type de plateforme_key",
                                    "key": true,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "site_type": {
                            "id": "site_type",
                            "label": "site_type",
                            "children": ["site"],
                            "columns": {
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null},
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_en": {
                                    "id": "description_en",
                                    "title": "description_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_fr": {
                                    "id": "description_fr",
                                    "title": "description_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "site": {
                            "id": "site",
                            "label": "site",
                            "children": ["platform"],
                            "columns": {
                                "nom du site_en": {
                                    "id": "nom du site_en",
                                    "title": "nom du site_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du site_fr": {
                                    "id": "nom du site_fr",
                                    "title": "nom du site_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du site_key": {
                                    "id": "nom du site_key",
                                    "title": "nom du site_key",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du type de site": {
                                    "id": "nom du type de site",
                                    "title": "nom du type de site",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description du site_en": {
                                    "id": "description du site_en",
                                    "title": "description du site_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description du site_fr": {
                                    "id": "description du site_fr",
                                    "title": "description du site_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du Plan d'eau": {
                                    "id": "code sandre du Plan d'eau",
                                    "title": "code sandre du Plan d'eau",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre de la Masse d'eau plan d'eau": {
                                    "id": "code sandre de la Masse d'eau plan d'eau",
                                    "title": "code sandre de la Masse d'eau plan d'eau",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "platform": {
                            "id": "platform", "label": "platform", "children": [], "columns": {
                                "altitude": {"id": "altitude", "title": "altitude", "key": false, "linkedTo": null},
                                "latitude": {"id": "latitude", "title": "latitude", "key": false, "linkedTo": null},
                                "longitude": {"id": "longitude", "title": "longitude", "key": false, "linkedTo": null},
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du site": {
                                    "id": "nom du site",
                                    "title": "nom du site",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la plateforme_en": {
                                    "id": "nom de la plateforme_en",
                                    "title": "nom de la plateforme_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la plateforme_fr": {
                                    "id": "nom de la plateforme_fr",
                                    "title": "nom de la plateforme_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de la plateforme_key": {
                                    "id": "nom de la plateforme_key",
                                    "title": "nom de la plateforme_key",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du type de plateforme_key": {
                                    "id": "nom du type de plateforme_key",
                                    "title": "nom du type de plateforme_key",
                                    "key": true,
                                    "linkedTo": null
                                }
                            }, "dynamicColumns": {}
                        },
                        "project": {
                            "id": "project",
                            "label": "project",
                            "children": [],
                            "columns": {
                                "nom du projet_en": {
                                    "id": "nom du projet_en",
                                    "title": "nom du projet_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du projet_fr": {
                                    "id": "nom du projet_fr",
                                    "title": "nom du projet_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du projet_key": {
                                    "id": "nom du projet_key",
                                    "title": "nom du projet_key",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "description du projet_en": {
                                    "id": "description du projet_en",
                                    "title": "description du projet_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description du projet_fr": {
                                    "id": "description du projet_fr",
                                    "title": "description du projet_fr",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "unit": {
                            "id": "unit",
                            "label": "unit",
                            "children": [],
                            "columns": {
                                "code": {"id": "code", "title": "code", "key": true, "linkedTo": null},
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": false, "linkedTo": null}
                            },
                            "dynamicColumns": {}
                        },
                        "tool_type": {
                            "id": "tool_type",
                            "label": "tool_type",
                            "children": ["tool"],
                            "columns": {
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "type d_outils": {
                                    "id": "type d_outils",
                                    "title": "type d_outils",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du type d_outils": {
                                    "id": "nom du type d_outils",
                                    "title": "nom du type d_outils",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {}
                        },
                        "tool": {
                            "id": "tool", "label": "tool", "children": [], "columns": {
                                "modèle": {"id": "modèle", "title": "modèle", "key": false, "linkedTo": null},
                                "fabricant": {"id": "fabricant", "title": "fabricant", "key": false, "linkedTo": null},
                                "code sandre": {
                                    "id": "code sandre",
                                    "title": "code sandre",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description": {
                                    "id": "description",
                                    "title": "description",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "étalonnage": {
                                    "id": "étalonnage",
                                    "title": "étalonnage",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_en": {
                                    "id": "description_en",
                                    "title": "description_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "description_fr": {
                                    "id": "description_fr",
                                    "title": "description_fr",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "numéro de série": {
                                    "id": "numéro de série",
                                    "title": "numéro de série",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "code sandre du contexte": {
                                    "id": "code sandre du contexte",
                                    "title": "code sandre du contexte",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de l_outil de mesure_en": {
                                    "id": "nom de l_outil de mesure_en",
                                    "title": "nom de l_outil de mesure_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de l_outil de mesure_fr": {
                                    "id": "nom de l_outil de mesure_fr",
                                    "title": "nom de l_outil de mesure_fr",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du type d_outils de mesure": {
                                    "id": "nom du type d_outils de mesure",
                                    "title": "nom du type d_outils de mesure",
                                    "key": true,
                                    "linkedTo": null
                                }
                            }, "dynamicColumns": {}
                        }
                    },
                    "dataTypes": {
                        "physico-chimie": {
                            "id": "physico-chimie", "label": "physico-chimie", "variables": {
                                "date": {
                                    "id": "date",
                                    "label": "date",
                                    "components": {
                                        "day": {"id": "day", "label": "day"},
                                        "time": {"id": "time", "label": "time"}
                                    },
                                    "chartDescription": null
                                },
                                "site": {
                                    "id": "site",
                                    "label": "site",
                                    "components": {"nom du site": {"id": "nom du site", "label": "nom du site"}},
                                    "chartDescription": null
                                },
                                "outil": {
                                    "id": "outil",
                                    "label": "outil",
                                    "components": {"prélèvement": {"id": "prélèvement", "label": "prélèvement"}},
                                    "chartDescription": null
                                },
                                "projet": {
                                    "id": "projet",
                                    "label": "projet",
                                    "components": {"nom du projet": {"id": "nom du projet", "label": "nom du projet"}},
                                    "chartDescription": null
                                },
                                "variable": {
                                    "id": "variable",
                                    "label": "variable",
                                    "components": {
                                        "nom": {"id": "nom", "label": "nom"},
                                        "values": {"id": "values", "label": "values"}
                                    },
                                    "chartDescription": null
                                },
                                "plateforme": {
                                    "id": "plateforme",
                                    "label": "plateforme",
                                    "components": {
                                        "nom de la plateforme": {
                                            "id": "nom de la plateforme",
                                            "label": "nom de la plateforme"
                                        }
                                    },
                                    "chartDescription": null
                                },
                                "profondeur": {
                                    "id": "profondeur",
                                    "label": "profondeur",
                                    "components": {
                                        "reelle": {"id": "reelle", "label": "reelle"},
                                        "maximum": {"id": "maximum", "label": "maximum"},
                                        "minimum": {"id": "minimum", "label": "minimum"}
                                    },
                                    "chartDescription": null
                                }
                            }, "repository": {"toto": "test"}, "hasAuthorizations": true
                        }, "sonde_multiparametres": {
                            "id": "sonde_multiparametres", "label": "sonde_multiparametres", "variables": {
                                "ph": {
                                    "id": "ph",
                                    "label": "ph",
                                    "components": {
                                        "tc": {"id": "tc", "label": "tc"},
                                        "brut": {"id": "brut", "label": "brut"},
                                        "corrige_labo": {"id": "corrige_labo", "label": "corrige_labo"}
                                    },
                                    "chartDescription": null
                                },
                                "chl": {
                                    "id": "chl",
                                    "label": "chl",
                                    "components": {
                                        "a": {"id": "a", "label": "a"},
                                        "a_corrige_labo": {"id": "a_corrige_labo", "label": "a_corrige_labo"}
                                    },
                                    "chartDescription": null
                                },
                                "date": {
                                    "id": "date",
                                    "label": "date",
                                    "components": {
                                        "day": {"id": "day", "label": "day"},
                                        "time": {"id": "time", "label": "time"}
                                    },
                                    "chartDescription": null
                                },
                                "site": {
                                    "id": "site",
                                    "label": "site",
                                    "components": {"nom du site": {"id": "nom du site", "label": "nom du site"}},
                                    "chartDescription": null
                                },
                                "outil": {
                                    "id": "outil",
                                    "label": "outil",
                                    "components": {"mesure": {"id": "mesure", "label": "mesure"}},
                                    "chartDescription": null
                                },
                                "trans": {
                                    "id": "trans",
                                    "label": "trans",
                                    "components": {
                                        "par_a": {"id": "par_a", "label": "par_a"},
                                        "par_w": {"id": "par_w", "label": "par_w"},
                                        "value": {"id": "value", "label": "value"}
                                    },
                                    "chartDescription": null
                                },
                                "cond25": {
                                    "id": "cond25",
                                    "label": "cond25",
                                    "components": {
                                        "degres": {"id": "degres", "label": "degres"},
                                        "C_corrige_labo": {"id": "C_corrige_labo", "label": "C_corrige_labo"}
                                    },
                                    "chartDescription": null
                                },
                                "projet": {
                                    "id": "projet",
                                    "label": "projet",
                                    "components": {"nom du projet": {"id": "nom du projet", "label": "nom du projet"}},
                                    "chartDescription": null
                                },
                                "oxygene": {
                                    "id": "oxygene",
                                    "label": "oxygene",
                                    "components": {
                                        "mg": {"id": "mg", "label": "mg"},
                                        "mg_corrige": {"id": "mg_corrige", "label": "mg_corrige"},
                                        "saturation": {"id": "saturation", "label": "saturation"},
                                        "saturation_corrige": {
                                            "id": "saturation_corrige",
                                            "label": "saturation_corrige"
                                        }
                                    },
                                    "chartDescription": null
                                },
                                "turbidite": {
                                    "id": "turbidite",
                                    "label": "turbidite",
                                    "components": {"value": {"id": "value", "label": "value"}},
                                    "chartDescription": null
                                },
                                "plateforme": {
                                    "id": "plateforme",
                                    "label": "plateforme",
                                    "components": {
                                        "nom de la plateforme": {
                                            "id": "nom de la plateforme",
                                            "label": "nom de la plateforme"
                                        }
                                    },
                                    "chartDescription": null
                                },
                                "condition_prelevement": {
                                    "id": "condition_prelevement",
                                    "label": "condition_prelevement",
                                    "components": {
                                        "profondeur": {"id": "profondeur", "label": "profondeur"},
                                        "commentaire": {"id": "commentaire", "label": "commentaire"},
                                        "temperature": {"id": "temperature", "label": "temperature"}
                                    },
                                    "chartDescription": null
                                }
                            }, "repository": {"toto": "test"}, "hasAuthorizations": true
                        }, "condition_prelevements": {
                            "id": "condition_prelevements", "label": "condition_prelevements", "variables": {
                                "date": {
                                    "id": "date",
                                    "label": "date",
                                    "components": {
                                        "day": {"id": "day", "label": "day"},
                                        "time": {"id": "time", "label": "time"}
                                    },
                                    "chartDescription": null
                                },
                                "site": {
                                    "id": "site",
                                    "label": "site",
                                    "components": {
                                        "nom du site": {"id": "nom du site", "label": "nom du site"},
                                        "nom de la plateforme": {
                                            "id": "nom de la plateforme",
                                            "label": "nom de la plateforme"
                                        }
                                    },
                                    "chartDescription": null
                                },
                                "projet": {
                                    "id": "projet",
                                    "label": "projet",
                                    "components": {"value": {"id": "value", "label": "value"}},
                                    "chartDescription": null
                                },
                                "commentaire": {
                                    "id": "commentaire",
                                    "label": "commentaire",
                                    "components": {"value": {"id": "value", "label": "value"}},
                                    "chartDescription": null
                                },
                                "valeurs qualitatives": {
                                    "id": "valeurs qualitatives",
                                    "label": "valeurs qualitatives",
                                    "components": {
                                        "temps": {"id": "temps", "label": "temps"},
                                        "nebulosite": {"id": "nebulosite", "label": "nebulosite"},
                                        "ensoleillement": {"id": "ensoleillement", "label": "ensoleillement"},
                                        "aspect de l'eau": {"id": "aspect de l'eau", "label": "aspect de l'eau"},
                                        "etat de surface": {"id": "etat de surface", "label": "etat de surface"},
                                        "vitesse du vent": {"id": "vitesse du vent", "label": "vitesse du vent"},
                                        "couleur de l'eau": {"id": "couleur de l'eau", "label": "couleur de l'eau"},
                                        "direction du vent": {"id": "direction du vent", "label": "direction du vent"}
                                    },
                                    "chartDescription": null
                                },
                                "valeurs quantitatives": {
                                    "id": "valeurs quantitatives",
                                    "label": "valeurs quantitatives",
                                    "components": {
                                        "temperature de l'air": {
                                            "id": "temperature de l'air",
                                            "label": "temperature de l'air"
                                        },
                                        "pression atmospherique": {
                                            "id": "pression atmospherique",
                                            "label": "pression atmospherique"
                                        },
                                        "transparence par secchi": {
                                            "id": "transparence par secchi",
                                            "label": "transparence par secchi"
                                        },
                                        "transparence par disque inra": {
                                            "id": "transparence par disque inra",
                                            "label": "transparence par disque inra"
                                        }
                                    },
                                    "chartDescription": null
                                }
                            }, "repository": {"toto": "test"}, "hasAuthorizations": true
                        }
                    },
                    "referenceSynthesis": [{
                        "referenceType": "taxon_zooplancton",
                        "lineCount": 377
                    }, {"referenceType": "site", "lineCount": 44}, {
                        "referenceType": "data_type",
                        "lineCount": 14
                    }, {"referenceType": "variable", "lineCount": 124}, {
                        "referenceType": "propriete_taxon",
                        "lineCount": 39
                    }, {"referenceType": "platform_type", "lineCount": 4}, {
                        "referenceType": "platform",
                        "lineCount": 57
                    }, {"referenceType": "site_type", "lineCount": 3}, {
                        "referenceType": "variable_group",
                        "lineCount": 16
                    }, {
                        "referenceType": "controle_coherence",
                        "lineCount": 149
                    }, {"referenceType": "stade_développement_zoo", "lineCount": 23}, {
                        "referenceType": "variable_norm",
                        "lineCount": 2
                    }, {"referenceType": "taxon_phytoplancton", "lineCount": 1517}, {
                        "referenceType": "unit",
                        "lineCount": 24
                    }, {"referenceType": "niveau_taxon", "lineCount": 22}, {
                        "referenceType": "tool_type",
                        "lineCount": 9
                    }, {"referenceType": "file_type", "lineCount": 4}, {
                        "referenceType": "project",
                        "lineCount": 4
                    }, {"referenceType": "thematic", "lineCount": 7}, {
                        "referenceType": "tool",
                        "lineCount": 52
                    }, {"referenceType": "valeurs_qualitative", "lineCount": 142}]
                }
            }).as('pageRef')


        cy.visit(Cypress.env('ola_references_url'))

        let returnErrors = {};
        for (const methodName in errors) {
            cy.intercept(
                'POST',
                'http://localhost:8081/api/v1/applications/ola/references/thematic', {
                    statusCode: 400,
                    body: errors[methodName]
                })
            cy.get(".liste[id=13] input[type=file]")
                .attachFile({
                    fileContent: "toto",
                    fileName: 'variable.csv',
                    mimeType: 'text/csv'
                })
            cy.get('.delete').click({force: true})
        }
    })
})