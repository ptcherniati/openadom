describe('test authorization references', () => {
    beforeEach(() => {
        cy.setLocale('fr');
    });

    it.skip('Test creation authorization admin ola', () => {

        cy.login("admin", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        const ola = 'references/ola/ola.json'
        cy.fixture(ola).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=REFERENCETYPE', {
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

        cy.fixture(ola).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=REFERENCETYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageRefAuthorization')
        })
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/authorization?offset=0&limit=10', {
                statusCode: 200,
                body: {
                    "authorizationResults": [],
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "isAdministrator": true
                    },
                    "users": [{
                        "id": "4a77cb9e-f136-47db-83cf-03abd16c8ae2",
                        "label": "echo"
                    }, {
                        "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
                        "label": "poussin"
                    }, {"id": "0f6ed2eb-785e-46e0-84c3-f917ac135a62", "label": "lucky"}]
                }
            }).as('pageRefAuthorization')

        cy.visit(Cypress.env('ola_authorization_references_url'))
        cy.get('.column > .button').contains("Ajouter une autorisation")

        cy.fixture(ola).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=REFERENCETYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageRefAuthorization')
        })
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/authorization?userId=null&limit=0', {
                statusCode: 200,
                body: {
                    "authorizationResults": [],
                    "authorizationsForUser": {
                        "authorizationResults": {},
                        "applicationName": "ola",
                        "isAdministrator": true
                    },
                    "users": [{
                        "id": "4a77cb9e-f136-47db-83cf-03abd16c8ae2",
                        "label": "echo"
                    }, {
                        "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
                        "label": "poussin"
                    }, {"id": "0f6ed2eb-785e-46e0-84c3-f917ac135a62", "label": "lucky"}]
                }
            }).as('pageRefAuthorization')

        cy.visit(Cypress.env('ola_new_authorization_references_url'))
        cy.get('.taginput-container').click()
        cy.contains('poussin').click()
        cy.contains('echo').click()
        cy.get('.field > .control > .input').should(($input) => {
            const value = $input.val("name");
            console.log(value); // do something with the value
        })
        cy.get(':nth-child(1) > [data-label="Administration"] > .control > .b-checkbox > .icon').click()
        cy.get(':nth-child(2) > [data-label="Gestion"] > .control > .b-checkbox > .icon').click()
        cy.get(':nth-child(3) > [data-label="Gestion"] > .control > .b-checkbox > .icon').click()
        cy.get('.buttons > .button').contains("Créer l'autorisation")

        cy.intercept(
            'OPTIONS',
            'http://localhost:8081/api/v1/applications/ola/references/authorization', {
                statusCode: 200,
                body: {}
            }).as('pageRefAuthorizations')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola?filter=CONFIGURATION&filter=REFERENCETYPE', {
                statusCode: 200,
                body: {
                    "id": "a7c447b7-42ff-4400-9785-3e6e36d04ae4",
                    "name": "ola",
                    "title": "ola",
                    "comment": "",
                    "internationalization": {
                        "application": {"internationalizationName": {"en": "ORE OLA", "fr": "ORE OLA"}}, "references": {
                            "controle_coherence": {
                                "internationalizationName": {
                                    "en": "Controle de la cohérence",
                                    "fr": "Controle de la cohérence"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "site_type": {
                                "internationalizationName": {"en": "Sites types", "fr": "Types de site"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "data_type_site_theme_project": {
                                "internationalizationName": {
                                    "en": "Data type for theme's site and project",
                                    "fr": "Type de données par thème de sites et projet"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "ref_site": {
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
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{valeur_key}",
                                        "fr": "{valeur_key}"
                                    }
                                },
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "variable_norm": {
                                "internationalizationName": {
                                    "en": "Variables' norms",
                                    "fr": "Normes de variable"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "ref_variable": {
                                "internationalizationName": {"en": "Variables", "fr": "Variables"},
                                "internationalizedColumns": {
                                    "définition_fr": {
                                        "en": "définition_en",
                                        "fr": "définition_fr"
                                    },
                                    "nom de la variable_fr": {
                                        "en": "Affichage de la variable_en",
                                        "fr": "Affichage de la variable_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de la variable_fr}",
                                        "fr": "{nom de la variable_fr}"
                                    }
                                },
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "propriete_taxon": {
                                "internationalizationName": {
                                    "en": "Proporties of taxons",
                                    "fr": "Propiétés des taxons"
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
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "project_site": {
                                "internationalizationName": {
                                    "en": "Project on site",
                                    "fr": "Projet par site"
                                },
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "tool": {
                                "internationalizationName": {"en": "Measuring tool", "fr": "Outils de mesure"},
                                "internationalizedColumns": {
                                    "description_fr": {
                                        "en": "description_en",
                                        "fr": "description_fr"
                                    },
                                    "nom de l'outil de mesure_fr": {
                                        "en": "nom de l'outil de mesure_en",
                                        "fr": "nom de l'outil de mesure_fr"
                                    }
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de l'outil de mesure_fr}",
                                        "fr": "{nom de l'outil de mesure_fr}"
                                    }
                                },
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizedValidations": {"format_float": {"fr": "latitude, longitude, altitude au format flottant obligatoire"}},
                                "internationalizedTags": null
                            },
                            "unit": {
                                "internationalizationName": {"en": "Units", "fr": "Unités"},
                                "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom_key} ({code})",
                                        "fr": "{nom_key} ({code})"
                                    }
                                },
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "tool_type": {
                                "internationalizationName": {"en": "Tools type", "fr": "Type d'outils"},
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du type d'outils}",
                                        "fr": "{nom du type d'outils}"
                                    }
                                },
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "file_type": {
                                "internationalizationName": {"en": "Type's Files", "fr": "Type de fichier"},
                                "internationalizedColumns": null,
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "data_type": {
                                "internationalizationName": {"en": "Data type", "fr": "Types de données"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "niveau_taxon": {
                                "internationalizationName": {
                                    "en": "Level of taxon",
                                    "fr": "Niveau de taxon"
                                },
                                "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                "internationalizedValidations": {},
                                "internationalizedTags": null
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
                                        "en": "{nom du taxon superieur}.{nom du taxon déterminé}({nom du niveau de taxon})",
                                        "fr": "{nom du taxon superieur}.{nom du taxon déterminé}({nom du niveau de taxon})"
                                    }
                                },
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            },
                            "thematic": {
                                "internationalizationName": {"en": "Thematic", "fr": "Thème"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizedDynamicColumns": {},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "internationalizedValidations": {},
                                "internationalizedTags": null
                            }
                        }, "dataTypes": {
                            "phytoplancton": {
                                "internationalizationName": {
                                    "en": "Phytoplancton",
                                    "fr": "Phytoplancton"
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
                                        "donnee": {"internationalizationName": {"en": "Data", "fr": "Donnée"}}
                                    },
                                    "authorizationScopes": {
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
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
                                        "site": {
                                            "internationalizationName": {
                                                "en": "Site",
                                                "fr": "Site"
                                            }
                                        }, "projet": {"internationalizationName": {"en": "Project", "fr": "Projet"}}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "chlorophylle": {
                                "internationalizationName": {"en": null, "fr": "Chlorophylle"},
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
                                        }
                                    },
                                    "authorizationScopes": {
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "haute_frequence": {
                                "internationalizationName": {"en": null, "fr": "Haute Fréquence"},
                                "internationalizedColumns": null,
                                "authorization": {
                                    "dataGroups": {"all": {"internationalizationName": null}},
                                    "authorizationScopes": {
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "production_primaire": {
                                "internationalizationName": {
                                    "en": null,
                                    "fr": "Production primaire"
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
                                        }
                                    },
                                    "authorizationScopes": {
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "zooplancton": {
                                "internationalizationName": {"en": "Zooplancton", "fr": "Zooplancton"},
                                "internationalizedColumns": null,
                                "authorization": {
                                    "dataGroups": {
                                        "condition": {
                                            "internationalizationName": {
                                                "en": "Context",
                                                "fr": "Contexte"
                                            }
                                        },
                                        "donnée": {"internationalizationName": {"en": "Donnée", "fr": "Donnée"}},
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            }
                                        }
                                    },
                                    "authorizationScopes": {
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
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
                                        "dataGroup_variable": {
                                            "internationalizationName": {
                                                "en": "Data",
                                                "fr": "Données"
                                            }
                                        }
                                    },
                                    "authorizationScopes": {
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            },
                            "sonde_multiparametres": {
                                "internationalizationName": {"en": "Probe data", "fr": "Sonde multi-paramètres"},
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
                                        "site": {"internationalizationName": null},
                                        "projet": {"internationalizationName": null}
                                    },
                                    "columnsDescription": {
                                        "depot": {
                                            "internationalizationName": {
                                                "en": "Deposit",
                                                "fr": "Dépôt"
                                            }
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            }
                                        },
                                        "admin": {"internationalizationName": {"en": "Delegation", "fr": "Délégation"}},
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            }
                                        },
                                        "delete": {"internationalizationName": {"en": "Deletion", "fr": "Suppression"}}
                                    }
                                },
                                "internationalizationDisplay": null,
                                "internationalizedValidations": {}
                            }
                        }, "internationalizedTags": {"taxon": {"en": "Taxon", "fr": "Taxon"}}, "rightsRequest": null
                    },
                    "references": {
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
                                "type d'outils": {
                                    "id": "type d'outils",
                                    "title": "type d'outils",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du type d'outils": {
                                    "id": "nom du type d'outils",
                                    "title": "nom du type d'outils",
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                                "nom de l'outil de mesure_en": {
                                    "id": "nom de l'outil de mesure_en",
                                    "title": "nom de l'outil de mesure_en",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom de l'outil de mesure_fr": {
                                    "id": "nom de l'outil de mesure_fr",
                                    "title": "nom de l'outil de mesure_fr",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du type d'outils de mesure": {
                                    "id": "nom du type d'outils de mesure",
                                    "title": "nom du type d'outils de mesure",
                                    "key": true,
                                    "linkedTo": null
                                }
                            }, "dynamicColumns": {}, "tags": ["no-tag"]
                        },
                        "unit": {
                            "id": "unit",
                            "label": "unit",
                            "children": [],
                            "columns": {
                                "code": {"id": "code", "title": "code", "key": false, "linkedTo": null},
                                "nom_en": {"id": "nom_en", "title": "nom_en", "key": false, "linkedTo": null},
                                "nom_fr": {"id": "nom_fr", "title": "nom_fr", "key": false, "linkedTo": null},
                                "nom_key": {"id": "nom_key", "title": "nom_key", "key": true, "linkedTo": null}
                            },
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        },
                        "site_type": {
                            "id": "site_type",
                            "label": "site_type",
                            "children": ["ref_site"],
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        },
                        "ref_site": {
                            "id": "ref_site",
                            "label": "ref_site",
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            }, "dynamicColumns": {}, "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        },
                        "project_site": {
                            "id": "project_site",
                            "label": "project_site",
                            "children": [],
                            "columns": {
                                "date de fin": {
                                    "id": "date de fin",
                                    "title": "date de fin",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "nom du site": {
                                    "id": "nom du site",
                                    "title": "nom du site",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du projet": {
                                    "id": "nom du projet",
                                    "title": "nom du projet",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "date de début": {
                                    "id": "date de début",
                                    "title": "date de début",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "commentaire de projet": {
                                    "id": "commentaire de projet",
                                    "title": "commentaire de projet",
                                    "key": false,
                                    "linkedTo": null
                                },
                                "commanditaire du projet": {
                                    "id": "commanditaire du projet",
                                    "title": "commanditaire du projet",
                                    "key": false,
                                    "linkedTo": null
                                }
                            },
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        },
                        "ref_variable": {
                            "id": "ref_variable", "label": "ref_variable", "children": [], "columns": {
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
                                "ordre d'affichage": {
                                    "id": "ordre d'affichage",
                                    "title": "ordre d'affichage",
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
                            }, "dynamicColumns": {}, "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            },
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        },
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        },
                        "data_type_site_theme_project": {
                            "id": "data_type_site_theme_project",
                            "label": "data_type_site_theme_project",
                            "children": [],
                            "columns": {
                                "nom du site": {
                                    "id": "nom du site",
                                    "title": "nom du site",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du projet": {
                                    "id": "nom du projet",
                                    "title": "nom du projet",
                                    "key": true,
                                    "linkedTo": null
                                },
                                "nom du thème": {
                                    "id": "nom du thème",
                                    "title": "nom du thème",
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
                            "dynamicColumns": {},
                            "tags": ["no-tag"]
                        }
                    },
                    "authorizationReferencesRights": {
                        "authorizations": {
                            "controle_coherence": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "taxon_phytoplancton": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "site_type": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "data_type_site_theme_project": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "stade_développement_zoo": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "ref_site": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "project": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "valeurs_qualitative": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "variable_norm": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "ref_variable": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "propriete_taxon": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "project_site": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "tool": {
                                "DELETE": true,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": true,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "platform": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "unit": {
                                "DELETE": true,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": true,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "tool_type": {
                                "DELETE": true,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": true,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "platform_type": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "file_type": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "data_type": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "niveau_taxon": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "taxon_zooplancton": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "variable_group": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            },
                            "thematic": {
                                "DELETE": false,
                                "PUBLICATION": false,
                                "READ": true,
                                "ADMIN": true,
                                "UPLOAD": false,
                                "DOWNLOAD": true,
                                "ANY": true
                            }
                        },
                        "applicationName": "ola",
                        "isAdministrator": true,
                        "userId": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9"
                    },
                    "referenceSynthesis": [{"referenceType": "unit", "lineCount": 26}, {
                        "referenceType": "file_type",
                        "lineCount": 4
                    }, {"referenceType": "platform_type", "lineCount": 4}, {
                        "referenceType": "tool_type",
                        "lineCount": 10
                    }, {"referenceType": "project", "lineCount": 6}, {
                        "referenceType": "tool",
                        "lineCount": 62
                    }, {"referenceType": "platform", "lineCount": 59}, {
                        "referenceType": "site_type",
                        "lineCount": 3
                    }, {"referenceType": "ref_site", "lineCount": 45}],
                    "dataTypes": {},
                    "authorizationsDatatypesRights": {},
                    "rightsRequest": null,
                    "configuration": {
                        "requiredAuthorizationsAttributes": ["projet", "site"],
                        "version": 1,
                        "internationalization": {
                            "application": {"internationalizationName": {"en": "ORE OLA", "fr": "ORE OLA"}},
                            "references": {
                                "controle_coherence": {
                                    "internationalizationName": {
                                        "en": "Controle de la cohérence",
                                        "fr": "Controle de la cohérence"
                                    },
                                    "internationalizedColumns": null,
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "site_type": {
                                    "internationalizationName": {"en": "Sites types", "fr": "Types de site"},
                                    "internationalizedColumns": {
                                        "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                        "description_fr": {"en": "description_en", "fr": "description_fr"}
                                    },
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "data_type_site_theme_project": {
                                    "internationalizationName": {
                                        "en": "Data type for theme's site and project",
                                        "fr": "Type de données par thème de sites et projet"
                                    },
                                    "internationalizedColumns": null,
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "ref_site": {
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
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizationDisplay": {
                                        "pattern": {
                                            "en": "{valeur_key}",
                                            "fr": "{valeur_key}"
                                        }
                                    },
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "variable_norm": {
                                    "internationalizationName": {
                                        "en": "Variables' norms",
                                        "fr": "Normes de variable"
                                    },
                                    "internationalizedColumns": null,
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "ref_variable": {
                                    "internationalizationName": {"en": "Variables", "fr": "Variables"},
                                    "internationalizedColumns": {
                                        "définition_fr": {
                                            "en": "définition_en",
                                            "fr": "définition_fr"
                                        },
                                        "nom de la variable_fr": {
                                            "en": "Affichage de la variable_en",
                                            "fr": "Affichage de la variable_fr"
                                        }
                                    },
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {
                                        "pattern": {
                                            "en": "{nom de la variable_fr}",
                                            "fr": "{nom de la variable_fr}"
                                        }
                                    },
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "propriete_taxon": {
                                    "internationalizationName": {
                                        "en": "Proporties of taxons",
                                        "fr": "Propiétés des taxons"
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
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "project_site": {
                                    "internationalizationName": {
                                        "en": "Project on site",
                                        "fr": "Projet par site"
                                    },
                                    "internationalizedColumns": null,
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "tool": {
                                    "internationalizationName": {"en": "Measuring tool", "fr": "Outils de mesure"},
                                    "internationalizedColumns": {
                                        "description_fr": {
                                            "en": "description_en",
                                            "fr": "description_fr"
                                        },
                                        "nom de l'outil de mesure_fr": {
                                            "en": "nom de l'outil de mesure_en",
                                            "fr": "nom de l'outil de mesure_fr"
                                        }
                                    },
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {
                                        "pattern": {
                                            "en": "{nom de l'outil de mesure_fr}",
                                            "fr": "{nom de l'outil de mesure_fr}"
                                        }
                                    },
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizedValidations": {"format_float": {"fr": "latitude, longitude, altitude au format flottant obligatoire"}},
                                    "internationalizedTags": null
                                },
                                "unit": {
                                    "internationalizationName": {"en": "Units", "fr": "Unités"},
                                    "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {
                                        "pattern": {
                                            "en": "{nom_key} ({code})",
                                            "fr": "{nom_key} ({code})"
                                        }
                                    },
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "tool_type": {
                                    "internationalizationName": {"en": "Tools type", "fr": "Type d'outils"},
                                    "internationalizedColumns": null,
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {
                                        "pattern": {
                                            "en": "{nom du type d'outils}",
                                            "fr": "{nom du type d'outils}"
                                        }
                                    },
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "file_type": {
                                    "internationalizationName": {
                                        "en": "Type's Files",
                                        "fr": "Type de fichier"
                                    },
                                    "internationalizedColumns": null,
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "data_type": {
                                    "internationalizationName": {"en": "Data type", "fr": "Types de données"},
                                    "internationalizedColumns": {
                                        "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                        "description_fr": {"en": "description_en", "fr": "description_fr"}
                                    },
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "niveau_taxon": {
                                    "internationalizationName": {
                                        "en": "Level of taxon",
                                        "fr": "Niveau de taxon"
                                    },
                                    "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
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
                                            "en": "{nom du taxon superieur}.{nom du taxon déterminé}({nom du niveau de taxon})",
                                            "fr": "{nom du taxon superieur}.{nom du taxon déterminé}({nom du niveau de taxon})"
                                        }
                                    },
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                },
                                "thematic": {
                                    "internationalizationName": {"en": "Thematic", "fr": "Thème"},
                                    "internationalizedColumns": {
                                        "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                        "description_fr": {"en": "description_en", "fr": "description_fr"}
                                    },
                                    "internationalizedDynamicColumns": {},
                                    "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                    "internationalizedValidations": {},
                                    "internationalizedTags": null
                                }
                            },
                            "dataTypes": {
                                "phytoplancton": {
                                    "internationalizationName": {
                                        "en": "Phytoplancton",
                                        "fr": "Phytoplancton"
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
                                            "donnee": {"internationalizationName": {"en": "Data", "fr": "Donnée"}}
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                },
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
                                            "site": {
                                                "internationalizationName": {
                                                    "en": "Site",
                                                    "fr": "Site"
                                                }
                                            }, "projet": {"internationalizationName": {"en": "Project", "fr": "Projet"}}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                },
                                "chlorophylle": {
                                    "internationalizationName": {"en": null, "fr": "Chlorophylle"},
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
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                },
                                "haute_frequence": {
                                    "internationalizationName": {"en": null, "fr": "Haute Fréquence"},
                                    "internationalizedColumns": null,
                                    "authorization": {
                                        "dataGroups": {"all": {"internationalizationName": null}},
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                },
                                "production_primaire": {
                                    "internationalizationName": {
                                        "en": null,
                                        "fr": "Production primaire"
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
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                },
                                "zooplancton": {
                                    "internationalizationName": {"en": "Zooplancton", "fr": "Zooplancton"},
                                    "internationalizedColumns": null,
                                    "authorization": {
                                        "dataGroups": {
                                            "condition": {
                                                "internationalizationName": {
                                                    "en": "Context",
                                                    "fr": "Contexte"
                                                }
                                            },
                                            "donnée": {"internationalizationName": {"en": "Donnée", "fr": "Donnée"}},
                                            "referentiel": {
                                                "internationalizationName": {
                                                    "en": "Referential",
                                                    "fr": "Référentiel"
                                                }
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
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
                                            "dataGroup_variable": {
                                                "internationalizationName": {
                                                    "en": "Data",
                                                    "fr": "Données"
                                                }
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                },
                                "sonde_multiparametres": {
                                    "internationalizationName": {"en": "Probe data", "fr": "Sonde multi-paramètres"},
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
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    },
                                    "internationalizationDisplay": null,
                                    "internationalizedValidations": {}
                                }
                            },
                            "internationalizedTags": {"taxon": {"en": "Taxon", "fr": "Taxon"}},
                            "rightsRequest": null
                        },
                        "comment": null,
                        "application": {
                            "internationalizationName": {"en": "ORE OLA", "fr": "ORE OLA"},
                            "internationalizedColumns": null,
                            "name": "ola",
                            "version": 1,
                            "defaultLanguage": "fr",
                            "internationalization": {"internationalizationName": {"en": "ORE OLA", "fr": "ORE OLA"}}
                        },
                        "tags": {"taxon": {"en": "Taxon", "fr": "Taxon"}},
                        "rightsRequest": null,
                        "references": {
                            "tool_type": {
                                "internationalizationName": {"en": "Tools type", "fr": "Type d'outils"},
                                "internationalizedColumns": null,
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du type d'outils}",
                                        "fr": "{nom du type d'outils}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du type d'outils"],
                                "columns": {
                                    "code sandre": null,
                                    "type d'outils": null,
                                    "nom du type d'outils": null,
                                    "code sandre du contexte": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "tool": {
                                "internationalizationName": {"en": "Measuring tool", "fr": "Outils de mesure"},
                                "internationalizedColumns": {
                                    "description_fr": {
                                        "en": "description_en",
                                        "fr": "description_fr"
                                    },
                                    "nom de l'outil de mesure_fr": {
                                        "en": "nom de l'outil de mesure_en",
                                        "fr": "nom de l'outil de mesure_fr"
                                    }
                                },
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de l'outil de mesure_fr}",
                                        "fr": "{nom de l'outil de mesure_fr}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du type d'outils de mesure", "nom de l'outil de mesure_fr"],
                                "columns": {
                                    "modèle": null,
                                    "fabricant": null,
                                    "code sandre": null,
                                    "étalonnage": null,
                                    "description_en": null,
                                    "description_fr": null,
                                    "numéro de série": null,
                                    "code sandre du contexte": null,
                                    "nom de l'outil de mesure_en": null,
                                    "nom de l'outil de mesure_fr": null,
                                    "nom du type d'outils de mesure": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "tool_type",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    }
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "unit": {
                                "internationalizationName": {"en": "Units", "fr": "Unités"},
                                "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom_key} ({code})",
                                        "fr": "{nom_key} ({code})"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom_key"],
                                "columns": {"code": null, "nom_en": null, "nom_fr": null, "nom_key": null},
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "site_type": {
                                "internationalizationName": {"en": "Sites types", "fr": "Types de site"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "separator": ";",
                                "keyColumns": ["nom_key"],
                                "columns": {
                                    "nom_en": null,
                                    "nom_fr": null,
                                    "nom_key": null,
                                    "code sandre": null,
                                    "description_en": null,
                                    "description_fr": null,
                                    "code sandre du contexte": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "ref_site": {
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du site_key}",
                                        "fr": "{nom du site_key}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du site_key"],
                                "columns": {
                                    "nom du site_en": null,
                                    "nom du site_fr": null,
                                    "nom du site_key": null,
                                    "nom du type de site": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "site_type",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "description du site_en": null,
                                    "description du site_fr": null,
                                    "code sandre du Plan d'eau": null,
                                    "code sandre de la Masse d'eau plan d'eau": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du type de plateforme_key}",
                                        "fr": "{nom du type de plateforme_key}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du type de plateforme_key"],
                                "columns": {
                                    "code sandre": null,
                                    "description_en": null,
                                    "description_fr": null,
                                    "code sandre du contexte": null,
                                    "nom du type de plateforme_en": null,
                                    "nom du type de plateforme_fr": null,
                                    "nom du type de plateforme_key": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "platform": {
                                "internationalizationName": {"en": "Plateforms", "fr": "Plateformes"},
                                "internationalizedColumns": {
                                    "nom de la plateforme_key": {
                                        "en": "nom de la plateforme_en",
                                        "fr": "nom de la plateforme_fr"
                                    }
                                },
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de la plateforme_key}",
                                        "fr": "{nom de la plateforme_key}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du site", "nom du type de plateforme_key", "nom de la plateforme_key"],
                                "columns": {
                                    "altitude": null,
                                    "latitude": null,
                                    "longitude": null,
                                    "code sandre": null,
                                    "nom du site": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "ref_site",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {
                                                    "codify": true,
                                                    "groovy": {
                                                        "expression": "return references.ref_site.find({it.naturalKey.equals(datum[\"nom du site\"])}).naturalKey;\n",
                                                        "references": ["ref_site"],
                                                        "datatypes": []
                                                    }
                                                },
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "code sandre du contexte": null,
                                    "nom de la plateforme_en": null,
                                    "nom de la plateforme_fr": null,
                                    "nom de la plateforme_key": null,
                                    "nom du type de plateforme_key": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "platform_type",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    }
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {
                                    "format_float": {
                                        "internationalizationName": {"fr": "latitude, longitude, altitude au format flottant obligatoire"},
                                        "internationalizedColumns": null,
                                        "checker": {
                                            "name": "Float",
                                            "params": {
                                                "pattern": null,
                                                "refType": null,
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": false, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "columns": ["altitude", "latitude", "longitude"]
                                    }
                                },
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "file_type": {
                                "internationalizationName": {"en": "Type's Files", "fr": "Type de fichier"},
                                "internationalizedColumns": null,
                                "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                "separator": ";",
                                "keyColumns": ["nom"],
                                "columns": {"nom": null, "description": null},
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du projet_key}",
                                        "fr": "{nom du projet_key}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du projet_key"],
                                "columns": {
                                    "nom du projet_en": null,
                                    "nom du projet_fr": null,
                                    "nom du projet_key": null,
                                    "description du projet_en": null,
                                    "description du projet_fr": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "project_site": {
                                "internationalizationName": {
                                    "en": "Project on site",
                                    "fr": "Projet par site"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplay": null,
                                "separator": ";",
                                "keyColumns": ["nom du projet", "nom du site"],
                                "columns": {
                                    "date de fin": null,
                                    "nom du site": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "ref_site",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du projet": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "project",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "date de début": null,
                                    "commentaire de projet": null,
                                    "commanditaire du projet": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "niveau_taxon": {
                                "internationalizationName": {
                                    "en": "Level of taxon",
                                    "fr": "Niveau de taxon"
                                },
                                "internationalizedColumns": {"nom_key": {"en": "nom_en", "fr": "nom_fr"}},
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "separator": ";",
                                "keyColumns": ["nom_key"],
                                "columns": {
                                    "nom_en": null,
                                    "nom_fr": null,
                                    "nom_key": null,
                                    "code sandre": null,
                                    "code sandre du contexte": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "taxon_zooplancton": {
                                "internationalizationName": {
                                    "en": "taxon's zooplancton",
                                    "fr": "taxon des zooplancton"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du taxon superieur}.{nom du taxon déterminé}({nom du niveau de taxon})",
                                        "fr": "{nom du taxon superieur}.{nom du taxon déterminé}({nom du niveau de taxon})"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du taxon déterminé"],
                                "columns": {
                                    "theme": null,
                                    "preselected": null,
                                    "code sandre du taxon": null,
                                    "nom du niveau de taxon": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "niveau_taxon",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du taxon superieur": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "taxon_zooplancton",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du taxon déterminé": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "RegularExpression",
                                            "params": {
                                                "pattern": ".*",
                                                "refType": null,
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "code sandre du taxon supérieur": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "data_type": {
                                "internationalizationName": {"en": "Data type", "fr": "Types de données"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "separator": ";",
                                "keyColumns": ["nom_key"],
                                "columns": {
                                    "nom_en": null,
                                    "nom_fr": null,
                                    "nom_key": null,
                                    "description_en": null,
                                    "description_fr": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "variable_norm": {
                                "internationalizationName": {
                                    "en": "Variables' norms",
                                    "fr": "Normes de variable"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplay": {"pattern": {"en": "{nom}", "fr": "{nom}"}},
                                "separator": ";",
                                "keyColumns": ["nom"],
                                "columns": {"nom": null, "définition": null},
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du groupe_en}",
                                        "fr": "{nom du groupe_fr}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du groupe"],
                                "columns": {
                                    "code sandre": null,
                                    "nom du groupe": null,
                                    "nom du groupe_en": null,
                                    "nom du groupe_fr": null,
                                    "nom du groupe parent": null,
                                    "code sandre du contexte": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "ref_variable": {
                                "internationalizationName": {"en": "Variables", "fr": "Variables"},
                                "internationalizedColumns": {
                                    "définition_fr": {
                                        "en": "définition_en",
                                        "fr": "définition_fr"
                                    },
                                    "nom de la variable_fr": {
                                        "en": "Affichage de la variable_en",
                                        "fr": "Affichage de la variable_fr"
                                    }
                                },
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de la variable_fr}",
                                        "fr": "{nom de la variable_fr}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom de la variable_fr"],
                                "columns": {
                                    "code sandre": null,
                                    "nom du groupe": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "variable_group",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "définition_en": null,
                                    "définition_fr": null,
                                    "ordre d'affichage": null,
                                    "valeur  qualitative": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "RegularExpression",
                                            "params": {
                                                "pattern": "faux|vrai",
                                                "refType": null,
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom de la variable_en": null,
                                    "nom de la variable_fr": null,
                                    "code sandre du contexte": null,
                                    "Affichage de la variable_en": null,
                                    "Affichage de la variable_fr": null,
                                    "nom de la norme de variable": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "variable_norm",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    }
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "controle_coherence": {
                                "internationalizationName": {
                                    "en": "Controle de la cohérence",
                                    "fr": "Controle de la cohérence"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplay": null,
                                "separator": ";",
                                "keyColumns": ["nom de la variable", "nom du type de données", "nom du site"],
                                "columns": {
                                    "valeur max": null,
                                    "valeur min": null,
                                    "nom du site": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "ref_site",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom de la variable": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "ref_variable",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du type de données": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "data_type",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    }
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "propriete_taxon": {
                                "internationalizationName": {
                                    "en": "Proporties of taxons",
                                    "fr": "Propiétés des taxons"
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom de la propriété_key}",
                                        "fr": "{nom de la propriété_key}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom de la propriété_key"],
                                "columns": {
                                    "isFloatValue": null,
                                    "isQualitative": null,
                                    "type associé": null,
                                    "définition_en": null,
                                    "définition_fr": null,
                                    "ordre d'affichage": null,
                                    "nom de la propriété_en": null,
                                    "nom de la propriété_fr": null,
                                    "nom de la propriété_key": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "taxon_phytoplancton": {
                                "internationalizationName": {
                                    "en": "taxon's phytoplanctons",
                                    "fr": "taxon des phytoplanctons"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{nom du niveau de taxon}: {nom du taxon superieur}.{nom du taxon déterminé}",
                                        "fr": "{nom du niveau de taxon}: {nom du taxon superieur}.{nom du taxon déterminé}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom du taxon déterminé"],
                                "columns": {
                                    "theme": null,
                                    "Code Sandre": null,
                                    "Notes libres": null,
                                    "Synonyme ancien": null,
                                    "Synonyme récent": null,
                                    "code sandre du taxon": null,
                                    "nom du niveau de taxon": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "niveau_taxon",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du taxon superieur": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "taxon_phytoplancton",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "Année de la description": null,
                                    "Auteur de la description": null,
                                    "nom du taxon déterminé": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "RegularExpression",
                                            "params": {
                                                "pattern": ".*",
                                                "refType": null,
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": true,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "Classe algale sensu Bourrelly": null,
                                    "Référence de la description": null,
                                    "code sandre du taxon supérieur": null,
                                    "Références relatives à ce taxon": null,
                                    "niveau incertitude de détermination": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {
                                    "proprietes_taxon": {
                                        "presenceConstraint": "MANDATORY",
                                        "internationalizationName": {
                                            "en": "Properties of Taxa",
                                            "fr": "Proprétés de Taxons"
                                        },
                                        "headerPrefix": "pt_",
                                        "reference": "propriete_taxon",
                                        "referenceColumnToLookForHeader": "nom de la propriété_key"
                                    }
                                },
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
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
                                "internationalizationDisplay": {
                                    "pattern": {
                                        "en": "{valeur_key}",
                                        "fr": "{valeur_key}"
                                    }
                                },
                                "separator": ";",
                                "keyColumns": ["nom_key", "valeur_key"],
                                "columns": {
                                    "nom_en": null,
                                    "nom_fr": null,
                                    "nom_key": null,
                                    "valeur_en": null,
                                    "valeur_fr": null,
                                    "valeur_key": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
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
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "separator": ";",
                                "keyColumns": ["nom_key"],
                                "columns": {
                                    "nom_en": null,
                                    "nom_fr": null,
                                    "nom_key": null,
                                    "code sandre": null,
                                    "description_en": null,
                                    "description_fr": null,
                                    "code sandre du contexte": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "thematic": {
                                "internationalizationName": {"en": "Thematic", "fr": "Thème"},
                                "internationalizedColumns": {
                                    "nom_key": {"en": "nom_en", "fr": "nom_fr"},
                                    "description_fr": {"en": "description_en", "fr": "description_fr"}
                                },
                                "internationalizationDisplay": {"pattern": {"en": "{nom_key}", "fr": "{nom_key}"}},
                                "separator": ";",
                                "keyColumns": ["nom_key"],
                                "columns": {
                                    "nom_en": null,
                                    "nom_fr": null,
                                    "nom_key": null,
                                    "description_en": null,
                                    "description_fr": null
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            },
                            "data_type_site_theme_project": {
                                "internationalizationName": {
                                    "en": "Data type for theme's site and project",
                                    "fr": "Type de données par thème de sites et projet"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplay": null,
                                "separator": ";",
                                "keyColumns": ["nom du projet", "nom du site", "nom du thème", "nom du type de données"],
                                "columns": {
                                    "nom du site": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "ref_site",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du projet": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "project",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du thème": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "thematic",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    },
                                    "nom du type de données": {
                                        "presenceConstraint": "MANDATORY",
                                        "checker": {
                                            "name": "Reference",
                                            "params": {
                                                "pattern": null,
                                                "refType": "data_type",
                                                "groovy": null,
                                                "duration": null,
                                                "transformation": {"codify": true, "groovy": null},
                                                "required": false,
                                                "multiplicity": "ONE"
                                            }
                                        },
                                        "defaultValue": null
                                    }
                                },
                                "computedColumns": {},
                                "dynamicColumns": {},
                                "validations": {},
                                "allowUnexpectedColumns": false,
                                "tags": []
                            }
                        },
                        "compositeReferences": {
                            "arbre_outil": {
                                "internationalizationName": null,
                                "internationalizedColumns": null,
                                "components": [{
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "tool_type",
                                    "parentKeyColumn": null,
                                    "parentRecursiveKey": null
                                }, {
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "tool",
                                    "parentKeyColumn": "nom du type d'outils de mesure",
                                    "parentRecursiveKey": null
                                }]
                            },
                            "arbre_project": {
                                "internationalizationName": null,
                                "internationalizedColumns": null,
                                "components": [{
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "project",
                                    "parentKeyColumn": null,
                                    "parentRecursiveKey": null
                                }]
                            },
                            "arbre_zooplanctons": {
                                "internationalizationName": null,
                                "internationalizedColumns": null,
                                "components": [{
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "taxon_zooplancton",
                                    "parentKeyColumn": null,
                                    "parentRecursiveKey": "nom du taxon superieur"
                                }]
                            },
                            "arbre_localisations": {
                                "internationalizationName": null,
                                "internationalizedColumns": null,
                                "components": [{
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "site_type",
                                    "parentKeyColumn": null,
                                    "parentRecursiveKey": null
                                }, {
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "ref_site",
                                    "parentKeyColumn": "nom du type de site",
                                    "parentRecursiveKey": null
                                }, {
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "platform",
                                    "parentKeyColumn": "nom du site",
                                    "parentRecursiveKey": null
                                }]
                            },
                            "arbre_phytoplanctons": {
                                "internationalizationName": null,
                                "internationalizedColumns": null,
                                "components": [{
                                    "internationalizationName": null,
                                    "internationalizedColumns": null,
                                    "reference": "taxon_phytoplancton",
                                    "parentKeyColumn": null,
                                    "parentRecursiveKey": "nom du taxon superieur"
                                }]
                            }
                        },
                        "dataTypes": {
                            "zooplancton": {
                                "internationalizationName": {"en": "Zooplancton", "fr": "Zooplancton"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de mesure",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "mesure",
                                            "id": "outil_mesure",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de prélèvement",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "prelevement",
                                            "id": "outil_prelevement",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur minimum",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "minimum",
                                            "id": "profondeur_minimum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur maximum",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "maximum",
                                            "id": "profondeur_maximum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du determinateur",
                                        "boundTo": {
                                            "variable": "taxon",
                                            "component": "nom du determinateur",
                                            "id": "taxon_nom du determinateur",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "biovolume sédimenté",
                                        "boundTo": {
                                            "variable": "biovolume sédimenté",
                                            "component": "value",
                                            "id": "biovolume sédimenté_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du taxon déterminé",
                                        "boundTo": {
                                            "variable": "taxon",
                                            "component": "nom du taxon déterminé",
                                            "id": "taxon_nom du taxon déterminé",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "stade de développement",
                                        "boundTo": {
                                            "variable": "taxon",
                                            "component": "stade de développement",
                                            "id": "taxon_stade de développement",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nombre individus",
                                        "boundTo": {
                                            "variable": "nombre_individus",
                                            "component": "value",
                                            "id": "nombre_individus_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "outil": {
                                        "chartDescription": null,
                                        "components": {
                                            "mesure": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.tool.find({ it.refValues[\"nom de l'outil de mesure_fr\"].replaceAll(' ', '_').equalsIgnoreCase(datum.outil['mesure'].replaceAll(' ', '_')) }).naturalKey;\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "prelevement": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.tool.find({it.refValues[\"nom de l'outil de mesure_fr\"].replaceAll(' ', '_').equalsIgnoreCase(datum.outil['prelevement'].replaceAll(' ', '_')) }).naturalKey;\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "taxon": {
                                        "chartDescription": null, "components": {
                                            "nom du determinateur": null,
                                            "stade de développement": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "stade_développement_zoo",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.stade_développement_zoo.find({ it.refValues[\"nom_key\"].equalsIgnoreCase(datum.taxon['stade de développement']) }).naturalKey;\n",
                                                                "references": ["stade_développement_zoo"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "nom du taxon déterminé": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "taxon_zooplancton",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "if(datum.taxon['nom du taxon déterminé'].substring(datum.taxon['nom du taxon déterminé'].length()-1).equals(' ')){\n  return references.taxon_zooplancton.find({ it.refValues[\"nom du taxon déterminé\"].equalsIgnoreCase(datum.taxon['nom du taxon déterminé'].substring(0, datum.taxon['nom du taxon déterminé'].length()-1)) }).naturalKey;\n} else {\n  return references.taxon_zooplancton.find({ it.refValues[\"nom du taxon déterminé\"].equalsIgnoreCase(datum.taxon['nom du taxon déterminé']) }).naturalKey;\n}\n",
                                                                "references": ["taxon_zooplancton"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].replaceAll(' ', '_').equalsIgnoreCase(datum.plateforme['nom de la plateforme'].replaceAll(' ', '_'))}).naturalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"metre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "maximum": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "String valeur = datum.profondeur['maximum'].replaceAll(',','.'); String variable = 'profondeur_maximum'; if(Float.parseFloat(valeur) >= 51) {\n    throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieur à la valeur 50\" , variable, valeur));\n} else {\n  return valeur;\n}\n",
                                                                "references": [],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "minimum": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "String valeur = datum.profondeur['minimum'].replaceAll(',','.'); String variable = 'profondeur_minimum'; if(Float.parseFloat(valeur) <= -1) {\n    throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur 0\" , variable, valeur));\n} else {\n  return valeur;\n}\n",
                                                                "references": [],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "nombre_individus": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"individus par mètre carré\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "biovolume sédimenté": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"millilitre par mètre carré\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "projet",
                                    "component": "nom du projet",
                                    "id": "projet_nom du projet",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "plateforme",
                                    "component": "nom de la plateforme",
                                    "id": "plateforme_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "date",
                                    "component": "day",
                                    "id": "date_day",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "taxon",
                                    "component": "nom du taxon déterminé",
                                    "id": "taxon_nom du taxon déterminé",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "taxon",
                                    "component": "stade de développement",
                                    "id": "taxon_stade de développement",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "outil",
                                    "component": "mesure",
                                    "id": "outil_mesure",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "outil",
                                    "component": "prelevement",
                                    "id": "outil_prelevement",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "donnée": {
                                            "internationalizationName": {
                                                "en": "Donnée",
                                                "fr": "Donnée"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Data",
                                            "data": ["biovolume sédimenté", "nombre_individus"]
                                        },
                                        "condition": {
                                            "internationalizationName": {"en": "Context", "fr": "Contexte"},
                                            "internationalizedColumns": null,
                                            "label": "Contexte",
                                            "data": ["outil", "profondeur", "taxon"]
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["date", "site", "projet", "plateforme"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
                                        "dataGroups": {
                                            "condition": {
                                                "internationalizationName": {
                                                    "en": "Context",
                                                    "fr": "Contexte"
                                                }
                                            },
                                            "donnée": {"internationalizationName": {"en": "Donnée", "fr": "Donnée"}},
                                            "referentiel": {
                                                "internationalizationName": {
                                                    "en": "Referential",
                                                    "fr": "Référentiel"
                                                }
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_zooplancton_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "chlorophylle": {
                                "internationalizationName": {"en": null, "fr": "Chlorophylle"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "Nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prelevement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur min",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "minimum",
                                            "id": "profondeur_minimum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur max",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "maximum",
                                            "id": "profondeur_maximum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "chlorophylle a strickland-parsons",
                                        "boundTo": {
                                            "variable": "chlorophylle",
                                            "component": "a strickland-parsons",
                                            "id": "chlorophylle_a strickland-parsons",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "chlorophylle c",
                                        "boundTo": {
                                            "variable": "chlorophylle",
                                            "component": "c",
                                            "id": "chlorophylle_c",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "carotenoïde",
                                        "boundTo": {
                                            "variable": "chlorophylle",
                                            "component": "carotenoïde",
                                            "id": "chlorophylle_carotenoïde",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "chlorophylle a scor-unesco",
                                        "boundTo": {
                                            "variable": "chlorophylle",
                                            "component": "a scor-unesco",
                                            "id": "chlorophylle_a scor-unesco",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "pheopigments",
                                        "boundTo": {
                                            "variable": "chlorophylle",
                                            "component": "pheopigments",
                                            "id": "chlorophylle_pheopigments",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.plateforme['nom de la plateforme'])}).naturalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null,
                                        "components": {
                                            "unit": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"mètre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "maximum": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "minimum": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "chlorophylle": {
                                        "chartDescription": null, "components": {
                                            "c": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "unit": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"microgramme par litre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "carotenoïde": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "pheopigments": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "a scor-unesco": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "a strickland-parsons": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "date",
                                    "component": "day",
                                    "id": "date_day",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "projet",
                                    "component": "nom du projet",
                                    "id": "projet_nom du projet",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "plateforme",
                                    "component": "nom de la plateforme",
                                    "id": "plateforme_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "minimum",
                                    "id": "profondeur_minimum",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "maximum",
                                    "id": "profondeur_maximum",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "condition": {
                                            "internationalizationName": {
                                                "en": "Context",
                                                "fr": "Contexte"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Contexte",
                                            "data": ["profondeur", "chlorophylle"]
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["date", "site", "projet", "plateforme"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
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
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_chlorophylle_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "phytoplancton": {
                                "internationalizationName": {"en": "Phytoplancton", "fr": "Phytoplancton"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de mesure",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "mesure",
                                            "id": "outil_mesure",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de prélèvement",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "prelevement",
                                            "id": "outil_prelevement",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur minimum",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "minimum",
                                            "id": "profondeur_minimum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur maximum",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "maximum",
                                            "id": "profondeur_maximum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du déterminateur",
                                        "boundTo": {
                                            "variable": "taxon",
                                            "component": "nom du determinateur",
                                            "id": "taxon_nom du determinateur",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "volume sédimenté",
                                        "boundTo": {
                                            "variable": "volume sedimente",
                                            "component": "value",
                                            "id": "volume sedimente_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du taxon déterminé",
                                        "boundTo": {
                                            "variable": "taxon",
                                            "component": "nom du taxon déterminé",
                                            "id": "taxon_nom du taxon déterminé",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "surface de comptage",
                                        "boundTo": {
                                            "variable": "surface de comptage",
                                            "component": "value",
                                            "id": "surface de comptage_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "Nombre de champs comptés",
                                        "boundTo": {
                                            "variable": "nombre de champs comptés",
                                            "component": "value",
                                            "id": "nombre de champs comptés_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nombre d'objets comptés",
                                        "boundTo": {
                                            "variable": "nombre d'objets comptés",
                                            "component": "value",
                                            "id": "nombre d'objets comptés_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nombre d'objets par ml",
                                        "boundTo": {
                                            "variable": "nombre d'objets par ml",
                                            "component": "value",
                                            "id": "nombre d'objets par ml_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nombre de cellules par ml",
                                        "boundTo": {
                                            "variable": "nombre de cellules par ml",
                                            "component": "value",
                                            "id": "nombre de cellules par ml_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "biovolume de l'espèce dans l'échantillon",
                                        "boundTo": {
                                            "variable": "biovolume de l'espèce dans l'échantillon",
                                            "component": "value",
                                            "id": "biovolume de l'espèce dans l'échantillon_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "outil": {
                                        "chartDescription": null,
                                        "components": {
                                            "mesure": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "if(datum.outil['mesure'] != \"\") {\n  return references.tool.find({it.refValues[\"nom de l'outil de mesure_fr\"].equalsIgnoreCase(datum.outil['mesure'])}).naturalKey;\n} else {\n  return datum.outil['mesure'];\n}\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "prelevement": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "if(datum.outil['prelevement'] != \"\") {\n  return references.tool.find({it.refValues[\"nom de l'outil de mesure_fr\"].equalsIgnoreCase(datum.outil['prelevement'])}).naturalKey;\n} else {\n  return datum.outil['prelevement'];\n}\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "taxon": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du determinateur": null,
                                            "nom du taxon déterminé": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "taxon_phytoplancton",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "if(datum.taxon['nom du taxon déterminé'].substring(datum.taxon['nom du taxon déterminé'].length()-1).equals(' ')){\n  return references.taxon_phytoplancton.find({ it.refValues[\"nom du taxon déterminé\"].equalsIgnoreCase(datum.taxon['nom du taxon déterminé'].substring(0, datum.taxon['nom du taxon déterminé'].length()-1)) }).naturalKey;\n} else {\n  return references.taxon_phytoplancton.find({ it.refValues[\"nom du taxon déterminé\"].equalsIgnoreCase(datum.taxon['nom du taxon déterminé']) }).naturalKey;\n}\n",
                                                                "references": ["taxon_phytoplancton"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.plateforme['nom de la plateforme'])}).naturalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"metre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "maximum": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "minimum": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "volume sedimente": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"millilitre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "surface de comptage": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"millimetre_carre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "nombre d'objets par ml": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"individus_par_metre_carre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "nombre d'objets comptés": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"individus_par_metre_carre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "nombre de cellules par ml": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"individus_par_metre_carre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "nombre de champs comptés": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"no_unit\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "biovolume de l'espèce dans l'échantillon": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"micrometre_cube_par_millilitre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "projet",
                                    "component": "nom du projet",
                                    "id": "projet_nom du projet",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "plateforme",
                                    "component": "nom de la plateforme",
                                    "id": "plateforme_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "date",
                                    "component": "day",
                                    "id": "date_day",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "minimum",
                                    "id": "profondeur_minimum",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "maximum",
                                    "id": "profondeur_maximum",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "taxon",
                                    "component": "nom du taxon déterminé",
                                    "id": "taxon_nom du taxon déterminé",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "donnee": {
                                            "internationalizationName": {
                                                "en": "Data",
                                                "fr": "Donnée"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Donnée",
                                            "data": ["volume sedimente", "nombre de cellules par ml", "surface de comptage", "nombre de champs comptés", "nombre d'objets comptés", "taxon", "biovolume de l'espèce dans l'échantillon", "nombre d'objets par ml"]
                                        },
                                        "condition": {
                                            "internationalizationName": {"en": "Context", "fr": "Contexte"},
                                            "internationalizedColumns": null,
                                            "label": "Contexte",
                                            "data": ["outil", "profondeur"]
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["date", "site", "projet", "plateforme"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
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
                                            "donnee": {"internationalizationName": {"en": "Data", "fr": "Donnée"}}
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_phytoplancton_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "physico-chimie": {
                                "internationalizationName": {"en": "Chemical Physics", "fr": "Physico Chimie"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prelevement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de debut de campagne",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de fin de campagne",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de reception",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de prelevement",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "prélèvement",
                                            "id": "outil_prélèvement",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur minimum",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "minimum",
                                            "id": "profondeur_minimum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur maximum",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "maximum",
                                            "id": "profondeur_maximum",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur réelle",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "réelle",
                                            "id": "profondeur_réelle",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la variable",
                                        "boundTo": {
                                            "variable": "data_variable",
                                            "component": "nom",
                                            "id": "data_variable_nom",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "valeur de la variable",
                                        "boundTo": {
                                            "variable": "data_variable",
                                            "component": "values",
                                            "id": "data_variable_values",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "outil": {
                                        "chartDescription": null,
                                        "components": {
                                            "prélèvement": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.tool.find({it.refValues[\"nom de l'outil de mesure_fr\"].equalsIgnoreCase(datum.outil['prélèvement'])}).hierarchicalKey;\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.plateforme['nom de la plateforme'])}).naturalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null, "components": {
                                            "unité": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "return \"mètre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            },
                                            "maximum": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "String valeur = datum.profondeur['maximum'].replaceAll(',','.'); String dataTypes = 'physico_chimie'; String site = datum.site['nom du site']; String variable = 'profondeur_mesuree'; Object valeurTrouve = '' ? '' : references.controle_coherence\n        .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n        .findAll({ it.refValues['nom de la variable'].equals(variable) })\n        .find({ it.refValues['nom du site'].equalsIgnoreCase(site.split(\"\\\\.\")[1]) });\nif(valeurTrouve != null) {\n  if(valeurTrouve.find({valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "minimum": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "String valeur = datum.profondeur['minimum'].replaceAll(',','.'); String dataTypes = 'physico_chimie'; String site = datum.site['nom du site']; String variable = 'profondeur_mesuree'; Object valeurTrouve = '' ? '' : references.controle_coherence\n        .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n        .findAll({ it.refValues['nom de la variable'].equals(variable) })\n        .find({ it.refValues['nom du site'].equalsIgnoreCase(site.split(\"\\\\.\")[1]) });\nif(valeurTrouve != null) {\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else {\n    return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "réelle": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "data_variable": {
                                        "chartDescription": {
                                            "value": "values",
                                            "aggregation": {
                                                "variable": "data_variable",
                                                "component": "nom",
                                                "id": "data_variable_nom",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            },
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "nom": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_variable",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "if( references.ref_variable.find({ it.refValues['nom de la variable_fr'].equalsIgnoreCase(datum.data_variable['nom']) }) ) {\n    return references.ref_variable.find({ it.refValues['nom de la variable_fr'].equalsIgnoreCase(datum.data_variable['nom']) }).naturalKey;\n} else if (datum.data_variable['nom'].substring(datum.data_variable['nom'].length()-1).equals(' ')) {\n    return references.ref_variable.find({ it.refValues['nom de la variable_fr'].equalsIgnoreCase(datum.data_variable['nom'].substring(0, datum.data_variable['nom'].length()-1)) }).naturalKey;\n}  else if (references.ref_variable.find({ it.naturalKey.equalsIgnoreCase( datum.data_variable['nom']) }) ) {\n  return references.ref_variable.find({ it.naturalKey.equalsIgnoreCase(datum.data_variable['nom']) }).naturalKey;\n}\n",
                                                                "references": ["ref_variable"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }, "unit": {
                                                "checker": {
                                                    "name": "Reference", "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "Map variable_unit = [\n  \"ph\": \"no_unit\",\n  \"fer\": \"microgramme_par_litre\",\n  \"zinc\": \"microgramme_par_litre\",\n  \"etain\": \"microgramme_par_litre\",\n  \"plomb\": \"microgramme_par_litre\",\n  \"argent\": \"microgramme_par_litre\",\n  \"chrome\": \"microgramme_par_litre\",\n  \"cuivre\": \"microgramme_par_litre\",\n  \"nickel\": \"microgramme_par_litre\",\n  \"sodium\": \"milligramme_par_litre\",\n  \"arsenic\": \"microgramme_par_litre\",\n  \"cadmium\": \"microgramme_par_litre\",\n  \"calcium\": \"milligramme_par_litre\",\n  \"mercure\": \"microgramme_par_litre\",\n  \"selenium\": \"microgramme_par_litre\",\n  \"sulfates\": \"milligramme_par_litre\",\n  \"aluminium\": \"microgramme_par_litre\",\n  \"chlorures\": \"milligramme_par_litre\",\n  \"magnesium\": \"milligramme_par_litre\",\n  \"manganese\": \"microgramme_par_litre\",\n  \"molybdene\": \"milligramme_par_litre\",\n  \"potassium\": \"milligramme_par_litre\",\n  \"strontium\": \"milligramme_par_litre\",\n  \"azote_total\": \"milligramme_par_litre\",\n  \"bicarbonate\": \"milliequivalent_par_litre\",\n  \"temperature\": \"degres_celsius\",\n  \"conductivite\": \"microsiemens_par_centimetre\",\n  \"azote_ammonium\": \"milligramme_par_litre\",\n  \"azote_nitrates\": \"milligramme_par_litre\",\n  \"azote_nitrites\": \"milligramme_par_litre\",\n  \"balance_ionique\": \"pourcentage\",\n  \"microcystine_lr\": \"microgramme_par_litre\",\n  \"microcystine_rr\": \"microgramme_par_litre\",\n  \"oxygene_dissous\": \"milligramme_par_litre\",\n  \"phosphore_total\": \"milligramme_par_litre\",\n  \"silice_reactive\": \"milligramme_par_litre\",\n  \"azote_total_filtre\": \"milligramme_par_litre\",\n  \"indice_aromaticite\": \"unite_dabsorbance_par_milligramme_de_carbone_par_litre_et_par_centimetre\",\n  \"profondeur_maximum\": \"metre\",\n  \"profondeur_mesuree\": \"metre\",\n  \"profondeur_minimum\": \"metre\",\n  \"transmission_des_uv\": \"pourcentage\",\n  \"matieres_decantables\": \"milligramme_par_litre\",\n  \"titre_alcalimetrique\": \"milliequivalent_par_litre\",\n  \"matieres_en_suspension\": \"milligramme_par_litre\",\n  \"phosphore_particulaire\": \"milligramme_par_litre\",\n  \"phosphore_total_filtre\": \"milligramme_par_litre\",\n  \"carbone_organique_total\": \"milligramme_par_litre\",\n  \"debit_moyen_hebdomadaire\": \"metre_cube_par_seconde\",\n  \"carbone_organique_dissous\": \"milligramme_par_litre\",\n  \"phosphore_orthophosphates\": \"milligramme_par_litre\",\n  \"demande_chimique_en_oxygene\": \"milligramme_par_litre\",\n  \"residu_sec_a_105DEGREESIGNc\": \"milligramme_par_litre\",\n  \"azote_organique_particulaire\": \"milligramme_par_litre\",\n  \"titre_alcalimetrique_complet\": \"milliequivalent_par_litre\",\n  \"carbone_organique_particulaire\": \"milligramme_par_litre\",\n  \"matieres_en_suspension_organiques\": \"milligramme_par_litre\",\n  \"perte_entre_550_et_1000DEGREESIGNc\": \"partie_par_million\",\n  \"residu_sec_a_110_et_550DEGREESIGNc\": \"milligramme_par_litre\",\n  \"demande_biologique_en_oxygene_en_5_jours\": \"milligramme_par_litre\"\n]; references.ref_variable.refValues['nom de la variable_fr']; datum.data_variable['nom']; String variable = references.ref_variable.find({ it.naturalKey.equals(datum.data_variable['nom']) }).naturalKey; return variable_unit[variable];\n",
                                                                "references": ["ref_variable"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }, "values": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.data_variable['values'].replaceAll(',','.'); String dataTypes = 'physico_chimie'; String site = datum.site['nom du site']; String variable = datum.data_variable['nom']; if(references.controle_coherence\n    .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n    .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\") {\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n      .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  \n  if(valeurTrouve != null) {\n    if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n    } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n    } else {\n      return valeur;\n    }\n  } else {\n    return valeur;\n  }\n} else {\n    return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "projet",
                                    "component": "nom du projet",
                                    "id": "projet_nom du projet",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "plateforme",
                                    "component": "nom de la plateforme",
                                    "id": "plateforme_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "date",
                                    "component": "day",
                                    "id": "date_day",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "outil",
                                    "component": "prélèvement",
                                    "id": "outil_prélèvement",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "minimum",
                                    "id": "profondeur_minimum",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "data_variable",
                                    "component": "nom",
                                    "id": "data_variable_nom",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "condition": {
                                            "internationalizationName": {
                                                "en": "Context",
                                                "fr": "Contexte"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Contexte",
                                            "data": ["date", "outil", "profondeur"]
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["site", "projet", "plateforme"]
                                        },
                                        "dataGroup_variable": {
                                            "internationalizationName": {
                                                "en": "Data",
                                                "fr": "Données"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Données",
                                            "data": ["data_variable"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
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
                                            "dataGroup_variable": {
                                                "internationalizationName": {
                                                    "en": "Data",
                                                    "fr": "Données"
                                                }
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_physico_chimie_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "haute_frequence": {
                                "internationalizationName": {"en": null, "fr": "Haute Fréquence"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de mesure",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "mesure",
                                            "id": "outil_mesure",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "commentaire",
                                        "boundTo": {
                                            "variable": "commentaire",
                                            "component": "value",
                                            "id": "commentaire_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "heure",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "time",
                                            "id": "date_time",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "value",
                                            "id": "profondeur_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "temperature",
                                        "boundTo": {
                                            "variable": "temperature",
                                            "component": "value",
                                            "id": "temperature_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "qualite temperature",
                                        "boundTo": {
                                            "variable": "temperature",
                                            "component": "quality",
                                            "id": "temperature_quality",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "pression",
                                        "boundTo": {
                                            "variable": "pression",
                                            "component": "value",
                                            "id": "pression_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "time": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "HH:mm:ss",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "datetime": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy HH:mm:ss",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "return datum.date.day +\" \" +datum.date.time",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "outil": {
                                        "chartDescription": null,
                                        "components": {
                                            "mesure": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.tool.find({it.refValues[\"nom de l'outil de mesure_fr\"].equalsIgnoreCase(datum.outil['mesure'])}).naturalKey;\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "pression": {
                                        "chartDescription": null,
                                        "components": {
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.plateforme['nom de la plateforme'])}).hierarchicalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null,
                                        "components": {
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "commentaire": {
                                        "chartDescription": null,
                                        "components": {"value": null},
                                        "computedComponents": {}
                                    },
                                    "temperature": {
                                        "chartDescription": null,
                                        "components": {
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "quality": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "date",
                                    "component": "datetime",
                                    "id": "date_datetime",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "projet",
                                    "component": "nom du projet",
                                    "id": "projet_nom du projet",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "plateforme",
                                    "component": "nom de la plateforme",
                                    "id": "plateforme_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "value",
                                    "id": "profondeur_value",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "outil",
                                    "component": "mesure",
                                    "id": "outil_mesure",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "datetime",
                                        "id": "date_datetime",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "all": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "label": "Toutes les données",
                                            "data": ["date", "site", "outil", "projet", "profondeur", "temperature", "pression", "plateforme", "commentaire"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
                                        "dataGroups": {"all": {"internationalizationName": null}},
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_haute_frequence_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "production_primaire": {
                                "internationalizationName": {"en": null, "fr": "Production primaire"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "value",
                                            "id": "profondeur_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "heure debut d'incubation",
                                        "boundTo": {
                                            "variable": "incubation",
                                            "component": "heure debut",
                                            "id": "incubation_heure debut",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "heure fin d'incubation",
                                        "boundTo": {
                                            "variable": "incubation",
                                            "component": "heure fin",
                                            "id": "incubation_heure fin",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "duree d'incubation",
                                        "boundTo": {
                                            "variable": "incubation",
                                            "component": "duree",
                                            "id": "incubation_duree",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "production primaire par duree d'incubation reelle",
                                        "boundTo": {
                                            "variable": "production primaire",
                                            "component": "duree d_incubation reelle",
                                            "id": "production primaire_duree d_incubation reelle",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "production primaire par heure",
                                        "boundTo": {
                                            "variable": "production primaire",
                                            "component": "heure",
                                            "id": "production primaire_heure",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "production primaire par tiers median",
                                        "boundTo": {
                                            "variable": "production primaire",
                                            "component": "tiers median",
                                            "id": "production primaire_tiers median",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "incubation": {
                                        "chartDescription": null,
                                        "components": {
                                            "duree": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "HH:MM",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "heure fin": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "HH:MM",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "heure debut": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "HH:MM",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.plateforme['nom de la plateforme'])}).naturalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null,
                                        "components": {
                                            "value": {
                                                "checker": {
                                                    "name": "Float",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "production primaire": {
                                        "chartDescription": null,
                                        "components": {
                                            "heure": null,
                                            "tiers median": null,
                                            "duree d_incubation reelle": null
                                        },
                                        "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "date",
                                    "component": "day",
                                    "id": "date_day",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "projet",
                                    "component": "nom du projet",
                                    "id": "projet_nom du projet",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "plateforme",
                                    "component": "nom de la plateforme",
                                    "id": "plateforme_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "profondeur",
                                    "component": "value",
                                    "id": "profondeur_value",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "incubation",
                                    "component": "heure debut",
                                    "id": "incubation_heure debut",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "incubation",
                                    "component": "heure fin",
                                    "id": "incubation_heure fin",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "incubation",
                                    "component": "duree",
                                    "id": "incubation_duree",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "condition": {
                                            "internationalizationName": {
                                                "en": "Context",
                                                "fr": "Contexte"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Contexte",
                                            "data": ["profondeur", "incubation", "production primaire"]
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["date", "site", "projet", "plateforme"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
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
                                            }
                                        },
                                        "authorizationScopes": {
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_production_primaire_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "sonde_multiparametres": {
                                "internationalizationName": {"en": "Probe data", "fr": "Sonde multi-paramètres"},
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "id": "projet_nom du projet",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "plateforme",
                                            "component": "nom de la plateforme",
                                            "id": "plateforme_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "outil de mesure",
                                        "boundTo": {
                                            "variable": "outil",
                                            "component": "prélèvement",
                                            "id": "outil_prélèvement",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "commentaire sonde",
                                        "boundTo": {
                                            "variable": "commentaire",
                                            "component": "value",
                                            "id": "commentaire_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "heure",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "time",
                                            "id": "date_time",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "profondeur",
                                        "boundTo": {
                                            "variable": "profondeur",
                                            "component": "value",
                                            "id": "profondeur_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "température",
                                        "boundTo": {
                                            "variable": "temperature",
                                            "component": "value",
                                            "id": "temperature_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "chl a",
                                        "boundTo": {
                                            "variable": "chl",
                                            "component": "a",
                                            "id": "chl_a",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "pH brut",
                                        "boundTo": {
                                            "variable": "ph",
                                            "component": "brut",
                                            "id": "ph_brut",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "pH tc",
                                        "boundTo": {
                                            "variable": "ph",
                                            "component": "tc",
                                            "id": "ph_tc",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "cond25degres",
                                        "boundTo": {
                                            "variable": "cond25",
                                            "component": "degres",
                                            "id": "cond25_degres",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "oxygene mg",
                                        "boundTo": {
                                            "variable": "oxygene_mg",
                                            "component": "value",
                                            "id": "oxygene_mg_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "oxygene saturation",
                                        "boundTo": {
                                            "variable": "oxygene_saturation",
                                            "component": "value",
                                            "id": "oxygene_saturation_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "turbidite",
                                        "boundTo": {
                                            "variable": "turbidite",
                                            "component": "value",
                                            "id": "turbidite_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "trans",
                                        "boundTo": {
                                            "variable": "trans",
                                            "component": "value",
                                            "id": "trans_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "par w",
                                        "boundTo": {
                                            "variable": "par_w",
                                            "component": "value",
                                            "id": "par_w_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "par a",
                                        "boundTo": {
                                            "variable": "par_a",
                                            "component": "value",
                                            "id": "par_a_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": true
                                },
                                "data": {
                                    "ph": {
                                        "chartDescription": {
                                            "value": "tc",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "tc": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.ph['tc'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'ph tc'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "brut": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.ph['brut'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'ph_brut'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\" && valeur!=\" \"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equalsIgnoreCase(site) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"no_unit\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "chl": {
                                        "chartDescription": {
                                            "value": "a",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "a": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.chl['a'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'chl_a'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\" && valeur!=\" \"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equalsIgnoreCase(site) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"milligramme_par_metre_cube\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "time": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "HH:mm:ss",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "outil": {
                                        "chartDescription": null,
                                        "components": {
                                            "prélèvement": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "tool",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.tool.find({it.refValues[\"nom de l'outil de mesure_fr\"].equalsIgnoreCase(datum.outil['prélèvement'])}).hierarchicalKey;\n",
                                                                "references": ["tool"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "par_a": {
                                        "chartDescription": {
                                            "value": "value",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": null,
                                                "defaultValue": {
                                                    "expression": "\"micro_ensteins\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.par_a['value'].replaceAll(',','.');  String dataTypes = 'sonde_multiparametres';  String site = datum.site['nom du site'];  String variable = 'par_a'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0  && valeur!=\"\" && valeur!=\" \") {\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "par_w": {
                                        "chartDescription": {
                                            "value": "value",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"micro_ensteins\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.par_w['value'].replaceAll(',','.');  String dataTypes = 'sonde_multiparametres';  String site = datum.site['nom du site'];  String variable = 'par_w'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0  && valeur!=\"\" && valeur!=\" \") {\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "trans": {
                                        "chartDescription": {
                                            "value": "value",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"pourcentage\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.trans['value'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'trans'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0  && valeur!=\"\" && valeur!=\" \") {\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "cond25": {
                                        "chartDescription": {
                                            "value": "degres",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"millisiemens_par_centimetre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "degres": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.cond25['degres'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'cond25degres'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\" && valeur!=\" \"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du projet": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['nom du projet'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "turbidite": {
                                        "chartDescription": {
                                            "value": "value",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"formazine_turbidite_unit\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.turbidite['value'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'turbidite'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0  && valeur!=\"\" && valeur!=\" \") {\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "oxygene_mg": {
                                        "chartDescription": {
                                            "value": "value",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"milligramme_par_litre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.oxygene_mg['value'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'oxygene_mg'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\" && valeur!=\" \"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "plateforme": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.plateforme['nom de la plateforme'])}).hierarchicalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "profondeur": {
                                        "chartDescription": null, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"metre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.profondeur['value'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'profondeur'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "commentaire": {
                                        "chartDescription": null,
                                        "components": {"value": null},
                                        "computedComponents": {}
                                    },
                                    "temperature": {
                                        "chartDescription": null, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"degres_celsius\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.temperature['value'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'temperature'; if(references.controle_coherence\n        .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n        .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\"){\n    Object valeurTrouve = '' ? '' : references.controle_coherence\n            .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n            .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n            .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n    if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n        throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n    } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n        throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n    } else {\n        return valeur;\n    }\n} else {\n    return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "oxygene_saturation": {
                                        "chartDescription": {
                                            "value": "value",
                                            "aggregation": null,
                                            "unit": "unit",
                                            "gap": "1 DAY",
                                            "standardDeviation": null
                                        }, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"pourcentage\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.oxygene_saturation['value'].replaceAll(',','.'); String dataTypes = 'sonde_multiparametres'; String site = datum.site['nom du site']; String variable = 'oxygene_saturation'; if(references.controle_coherence\n      .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n      .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0 && valeur!=\"\" && valeur!=\" \"){\n  Object valeurTrouve = '' ? '' : references.controle_coherence\n          .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n          .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n          .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n  if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n  } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n      throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n  } else {\n      return valeur;\n  }\n} else {\n  return valeur;\n}\n",
                                                                "references": ["controle_coherence"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": null,
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "nom du projet",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "nom du projet",
                                                "id": "projet_nom du projet",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["date", "site", "outil", "projet", "plateforme"]
                                        },
                                        "donnee_prelevement": {
                                            "internationalizationName": {
                                                "en": "Data's condition",
                                                "fr": "Données du prélèvement"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Données du prélèvement",
                                            "data": ["par_w", "oxygene_saturation", "chl", "oxygene_mg", "ph", "cond25", "par_a", "turbidite", "trans"]
                                        },
                                        "condition_prelevement": {
                                            "internationalizationName": {
                                                "en": "Prelevement's condition",
                                                "fr": "Condition de prélèvement"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Condition de prélèvement",
                                            "data": ["profondeur", "temperature", "commentaire"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
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
                                            "site": {"internationalizationName": null},
                                            "projet": {"internationalizationName": null}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_sonde_multiparametres_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }, "condition_prelevements": {
                                "internationalizationName": {
                                    "en": "Collection condition",
                                    "fr": "Condition de prélèvement"
                                },
                                "internationalizedColumns": null,
                                "internationalizationDisplays": null,
                                "format": {
                                    "headerLine": 1,
                                    "firstRowLine": 2,
                                    "separator": ";",
                                    "columns": [{
                                        "header": "nom du projet",
                                        "boundTo": {
                                            "variable": "projet",
                                            "component": "value",
                                            "id": "projet_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom du site",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom du site",
                                            "id": "site_nom du site",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nom de la plateforme",
                                        "boundTo": {
                                            "variable": "site",
                                            "component": "nom de la plateforme",
                                            "id": "site_nom de la plateforme",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "date de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "day",
                                            "id": "date_day",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "heure de prélèvement",
                                        "boundTo": {
                                            "variable": "date",
                                            "component": "time",
                                            "id": "date_time",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "commentaire",
                                        "boundTo": {
                                            "variable": "commentaire",
                                            "component": "value",
                                            "id": "commentaire_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "température de l'air",
                                        "boundTo": {
                                            "variable": "temperature",
                                            "component": "temperature de l'air",
                                            "id": "temperature_temperature de l'air",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "nébulosité",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "nebulosite",
                                            "id": "qualitatives_nebulosite",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "ensoleillement",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "ensoleillement",
                                            "id": "qualitatives_ensoleillement",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "temps",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "temps",
                                            "id": "qualitatives_temps",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "direction du vent",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "direction du vent",
                                            "id": "qualitatives_direction du vent",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "vitesse du vent",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "vitesse du vent",
                                            "id": "qualitatives_vitesse du vent",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "pression atmosphérique",
                                        "boundTo": {
                                            "variable": "pression_atmospherique",
                                            "component": "value",
                                            "id": "pression_atmospherique_value",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "aspect de l'eau",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "aspect de l'eau",
                                            "id": "qualitatives_aspect de l'eau",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "état de surface",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "etat de surface",
                                            "id": "qualitatives_etat de surface",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "transparence par disque inra",
                                        "boundTo": {
                                            "variable": "transparence",
                                            "component": "transparence par disque inra",
                                            "id": "transparence_transparence par disque inra",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "transparence par secchi 20 cm",
                                        "boundTo": {
                                            "variable": "transparence",
                                            "component": "transparence par secchi",
                                            "id": "transparence_transparence par secchi",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }, {
                                        "header": "couleur de l'eau",
                                        "boundTo": {
                                            "variable": "qualitatives",
                                            "component": "couleur de l_eau",
                                            "id": "qualitatives_couleur de l_eau",
                                            "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                        },
                                        "presenceConstraint": "MANDATORY"
                                    }],
                                    "repeatedColumns": [],
                                    "constants": [],
                                    "allowUnexpectedColumns": false
                                },
                                "data": {
                                    "date": {
                                        "chartDescription": null,
                                        "components": {
                                            "day": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "dd/MM/yyyy",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "time": {
                                                "checker": {
                                                    "name": "Date",
                                                    "params": {
                                                        "pattern": "HH:mm:ss",
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": false, "groovy": null},
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "site": {
                                        "chartDescription": null,
                                        "components": {
                                            "nom du site": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "ref_site",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.ref_site.find({it.naturalKey.equals(datum.site['nom du site'])}).hierarchicalKey;\n",
                                                                "references": ["ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "nom de la plateforme": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "platform",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false,
                                                            "groovy": {
                                                                "expression": "return references.platform.find({it.refValues['nom de la plateforme_key'].equalsIgnoreCase(datum.site['nom de la plateforme'])}).hierarchicalKey;\n",
                                                                "references": ["platform"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "projet": {
                                        "chartDescription": null,
                                        "components": {
                                            "value": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "project",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "return references.project.find({it.naturalKey.equals(datum.projet['value'])}).hierarchicalKey;\n",
                                                                "references": ["project"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        },
                                        "computedComponents": {}
                                    },
                                    "commentaire": {
                                        "chartDescription": null,
                                        "components": {"value": null},
                                        "computedComponents": {}
                                    },
                                    "temperature": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"degres_celsius\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "temperature de l'air": null
                                        },
                                        "computedComponents": {}
                                    },
                                    "qualitatives": {
                                        "chartDescription": null, "components": {
                                            "temps": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['temps'];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('temps') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "nebulosite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['nebulosite'];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('nebulosite') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "ensoleillement": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['ensoleillement'];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('ensoleillement') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "aspect de l'eau": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives[\"aspect de l'eau\"];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('aspect de l_eau') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "etat de surface": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['etat de surface']; return nom_qualitative == '' ? '' : references.valeurs_qualitative\n        .findAll({ it.refValues['nom_key'].equals('etat de surface') })\n        .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "vitesse du vent": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['vitesse du vent'];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('vitesse du vent') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "couleur de l_eau": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['couleur de l_eau'];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('couleur de l_eau') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            },
                                            "direction du vent": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "valeurs_qualitative",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": true,
                                                            "groovy": {
                                                                "expression": "String nom_qualitative = datum.qualitatives['direction du vent'];\n  return nom_qualitative == '' ? '' : references.valeurs_qualitative\n          .findAll({ it.refValues['nom_key'].equals('direction du vent') })\n          .find({ it.naturalKey.split('__')[1].equalsIgnoreCase(nom_qualitative) }).naturalKey;\n",
                                                                "references": ["valeurs_qualitative"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "transparence": {
                                        "chartDescription": null, "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"metre\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "transparence par secchi": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.transparence['transparence par secchi'].replaceAll(',','.'); String dataTypes = 'conditions_prelevements'; String site = datum.site['nom du site']; String variable = 'transparence_par_secchi_20_cm'; if(references.controle_coherence\n        .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n        .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0  && valeur!=\"\") {\n    Object valeurTrouve = '' ? '' : references.controle_coherence\n            .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n            .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n            .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n    if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n        throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n    } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n        throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n    } else {\n        return valeur;\n    }\n} else {\n    return valeur;\n}\n",
                                                                "references": ["controle_coherence", "ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }, "transparence par disque inra": {
                                                "checker": {
                                                    "name": "Float", "params": {
                                                        "pattern": null,
                                                        "refType": null,
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {
                                                            "codify": false, "groovy": {
                                                                "expression": "String valeur = datum.transparence['transparence par disque inra'].replaceAll(',','.'); String dataTypes = 'conditions_prelevements'; String site = datum.site['nom du site']; String variable = 'transparence_par_disque_inra'; if(references.controle_coherence\n        .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n        .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) }).size()!=0  && valeur!=\"\") {\n    Object valeurTrouve = '' ? '' : references.controle_coherence\n            .findAll({ it.refValues['nom du type de données'].equals(dataTypes) })\n            .findAll({ it.refValues['nom de la variable'].equalsIgnoreCase(variable) })\n            .find({ it.refValues['nom du site'].equals(site.split(\"\\\\.\")[1]) });\n    if(valeurTrouve.find({Float.parseFloat(it.refValues['valeur min']) <= Float.parseFloat(valeur) }) == null) {\n        throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être supérieure à la valeur %s\" , variable, valeur, valeurTrouve.refValues['valeur min']));\n    } else if(valeurTrouve.find({ valeurTrouve.find({ Float.parseFloat(it.refValues['valeur max']) >=  Float.parseFloat(valeur) }) }) == null) {\n        throw new IllegalArgumentException(String.format(\"la valeur de %s (%s) doit être inférieure à la valeur %s\" ,variable, valeur, valeurTrouve.refValues['valeur max']));\n    } else {\n        return valeur;\n    }\n} else {\n    return valeur;\n}\n",
                                                                "references": ["controle_coherence", "ref_site"],
                                                                "datatypes": []
                                                            }
                                                        },
                                                        "required": false,
                                                        "multiplicity": "ONE"
                                                    }
                                                }, "defaultValue": null
                                            }
                                        }, "computedComponents": {}
                                    },
                                    "pression_atmospherique": {
                                        "chartDescription": null,
                                        "components": {
                                            "unite": {
                                                "checker": {
                                                    "name": "Reference",
                                                    "params": {
                                                        "pattern": null,
                                                        "refType": "unit",
                                                        "groovy": null,
                                                        "duration": null,
                                                        "transformation": {"codify": true, "groovy": null},
                                                        "required": true,
                                                        "multiplicity": "ONE"
                                                    }
                                                },
                                                "defaultValue": {
                                                    "expression": "\"millibar\"",
                                                    "references": [],
                                                    "datatypes": []
                                                }
                                            }, "value": null
                                        },
                                        "computedComponents": {}
                                    }
                                },
                                "validations": {},
                                "uniqueness": [{
                                    "variable": "projet",
                                    "component": "value",
                                    "id": "projet_value",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom du site",
                                    "id": "site_nom du site",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "site",
                                    "component": "nom de la plateforme",
                                    "id": "site_nom de la plateforme",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }, {
                                    "variable": "date",
                                    "component": "day",
                                    "id": "date_day",
                                    "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                }],
                                "migrations": {},
                                "authorization": {
                                    "timeScope": {
                                        "variable": "date",
                                        "component": "day",
                                        "id": "date_day",
                                        "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                    },
                                    "authorizationScopes": {
                                        "site": {
                                            "internationalizationName": {
                                                "en": "Site",
                                                "fr": "Site"
                                            },
                                            "internationalizedColumns": null,
                                            "variable": "site",
                                            "component": "nom du site",
                                            "variableComponentKey": {
                                                "variable": "site",
                                                "component": "nom du site",
                                                "id": "site_nom du site",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        },
                                        "projet": {
                                            "internationalizationName": {"en": "Project", "fr": "Projet"},
                                            "internationalizedColumns": null,
                                            "variable": "projet",
                                            "component": "value",
                                            "variableComponentKey": {
                                                "variable": "projet",
                                                "component": "value",
                                                "id": "projet_value",
                                                "type": "PARAM_VARIABLE_COMPONENT_KEY"
                                            }
                                        }
                                    },
                                    "dataGroups": {
                                        "qualitatif": {
                                            "internationalizationName": {
                                                "en": "Qualitative data",
                                                "fr": "Données qualitatives"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Données qualitatives",
                                            "data": ["qualitatives"]
                                        },
                                        "quantitatif": {
                                            "internationalizationName": {
                                                "en": "Quantitative data",
                                                "fr": "Données quantitatives"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Données quantitatives",
                                            "data": ["pression_atmospherique", "temperature", "transparence"]
                                        },
                                        "referentiel": {
                                            "internationalizationName": {
                                                "en": "Referential",
                                                "fr": "Référentiel"
                                            },
                                            "internationalizedColumns": null,
                                            "label": "Référentiel",
                                            "data": ["date", "site", "projet", "commentaire"]
                                        }
                                    },
                                    "columnsDescription": {
                                        "admin": {
                                            "internationalizationName": {"en": "Delegation", "fr": "Délégation"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "admin",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "depot": {
                                            "internationalizationName": {"en": "Deposit", "fr": "Dépôt"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "depot",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "delete": {
                                            "internationalizationName": {"en": "Deletion", "fr": "Suppression"},
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "delete",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        },
                                        "extraction": {
                                            "internationalizationName": {
                                                "en": "Extraction",
                                                "fr": "Extraction"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "extraction",
                                            "withPeriods": true,
                                            "withDataGroups": true,
                                            "forPublic": true
                                        },
                                        "publication": {
                                            "internationalizationName": {
                                                "en": "Publication",
                                                "fr": "Publication"
                                            },
                                            "internationalizedColumns": null,
                                            "display": true,
                                            "title": "publication",
                                            "withPeriods": false,
                                            "withDataGroups": false,
                                            "forPublic": false
                                        }
                                    },
                                    "internationalization": {
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
                                            "site": {
                                                "internationalizationName": {
                                                    "en": "Site",
                                                    "fr": "Site"
                                                }
                                            }, "projet": {"internationalizationName": {"en": "Project", "fr": "Projet"}}
                                        },
                                        "columnsDescription": {
                                            "depot": {
                                                "internationalizationName": {
                                                    "en": "Deposit",
                                                    "fr": "Dépôt"
                                                }
                                            },
                                            "publication": {
                                                "internationalizationName": {
                                                    "en": "Publication",
                                                    "fr": "Publication"
                                                }
                                            },
                                            "admin": {
                                                "internationalizationName": {
                                                    "en": "Delegation",
                                                    "fr": "Délégation"
                                                }
                                            },
                                            "extraction": {
                                                "internationalizationName": {
                                                    "en": "Extraction",
                                                    "fr": "Extraction"
                                                }
                                            },
                                            "delete": {
                                                "internationalizationName": {
                                                    "en": "Deletion",
                                                    "fr": "Suppression"
                                                }
                                            }
                                        }
                                    }
                                },
                                "repository": {
                                    "filePattern": "(.*)!(.*)_conditions_prelevements_(.*)_(.*).csv",
                                    "authorizationScope": {"site": 2, "projet": 1},
                                    "startDate": {"token": 3},
                                    "endDate": {"token": 4}
                                },
                                "tags": []
                            }
                        }
                    }
                }
            }).as('pageRefAuthorizations')
        cy.intercept(
            'POST',
            'http://localhost:8081/api/v1/applications/ola/references/authorization', {
                statusCode: 201,
                body: {"authorizationId": "6cd6d0a8-8166-48cd-87e9-c262dab1700a"}
            }).as('pageRefAuthorizations')
        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/ola/references/authorization?offset=0&limit=10', {
                statusCode: 200,
                body: {
                    "authorizationResults": [{
                        "uuid": "6cd6d0a8-8166-48cd-87e9-c262dab1700a",
                        "name": "name",
                        "users": [{
                            "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
                            "creationDate": 1678273047127,
                            "updateDate": 1678273047127,
                            "login": "poussin",
                            "password": "$2a$12$4gAH34ZwgvgQNS0pbR5dGem1Nle0AT/.UwrZWfqtqMiJ0hXeYMvUG",
                            "authorizations": [".*"]
                        }, {
                            "id": "4a77cb9e-f136-47db-83cf-03abd16c8ae2",
                            "creationDate": 1678276702095,
                            "updateDate": 1678276702095,
                            "login": "echo",
                            "password": "$2a$12$t.02Tdiu9gvrBcGAVFK.jubwkiZf/NNDBC4rESaGRATA6WixbscBa",
                            "authorizations": []
                        }],
                        "application": "a7c447b7-42ff-4400-9785-3e6e36d04ae4",
                        "authorizations": {"admin": ["tool_type"], "manage": ["tool", "unit"]}
                    }],
                    "authorizationsForUser": {
                        "authorizationResults": {
                            "admin": ["tool_type"],
                            "manage": ["tool", "unit"]
                        }, "applicationName": "ola", "isAdministrator": true
                    },
                    "users": [{
                        "id": "4a77cb9e-f136-47db-83cf-03abd16c8ae2",
                        "label": "echo"
                    }, {
                        "id": "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9",
                        "label": "poussin"
                    }, {"id": "0f6ed2eb-785e-46e0-84c3-f917ac135a62", "label": "lucky"}]
                }
            }).as('pageRefAuthorizations')
        cy.visit(Cypress.env('ola_authorization_references_url'))


    })

    it('Test creation authorization regularUser monsore', () => {
        cy.login("regularUser", ['applications/ore/ore_application_description.json'])
        cy.wait(['@postUserResponse', '@getApplicationResponse'])
        const monsore = 'references/monsore/monsore.json'
        cy.fixture(monsore).then(olaContent => {
            cy.intercept(
                'GET',
                'http://localhost:8081/api/v1/applications/monsore?filter=CONFIGURATION&filter=REFERENCETYPE', {
                    statusCode: 200,
                    body: olaContent
                }).as('pageRef')
        })

        cy.intercept(
            'GET',
            'http://localhost:8081/api/v1/applications/monsore/references', {
                statusCode: 200,
                body: {"id":"13dcde48-7f85-40c4-aeff-26014f5fda5a","name":"monsore","title":"MONSORE","comment":"","internationalization":{"application":{"internationalizationName":{"en":"SOERE my SOERE with repository","fr":"SOERE mon SOERE avec dépôt"}},"references":{"themes":{"internationalizationName":{"en":"Thematic","fr":"Thème"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"description_fr":{"en":"description_en","fr":"description_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"especes":{"internationalizationName":{"en":"Species","fr":"Espèces"},"internationalizedColumns":{"esp_definition_fr":{"en":"esp_definition_en","fr":"esp_definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{esp_nom}","fr":"{esp_nom}"}},"internationalizedValidations":{},"internationalizedTags":null},"variables":{"internationalizationName":{"en":"Variables","fr":"Variables"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"definition_fr":{"en":"definition_en","fr":"definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"type_de_sites":{"internationalizationName":{"en":"Sites types","fr":"Types de sites"},"internationalizedColumns":{"tze_nom_key":{"en":"tze_nom_en","fr":"tze_nom_fr"},"tze_definition_fr":{"en":"tze_definition_en","fr":"tze_definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{tze_nom_key}","fr":"{tze_nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"type de fichiers":{"internationalizationName":{"en":"Files types","fr":"Types de fichiers"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"description_fr":{"en":"description_en","fr":"description_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"unites":{"internationalizationName":{"en":"Units","fr":"Unités"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"code_key":{"en":"code_en","fr":"code_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key} ({code_key})","fr":"{nom_key} ({code_key})"}},"internationalizedValidations":{},"internationalizedTags":null},"projet":{"internationalizationName":{"en":"Project","fr":"Projet"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"definition_fr":{"en":"definition_en","fr":"definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"valeurs_qualitatives":{"internationalizationName":{"en":"Qualitative values","fr":"Valeurs qualitatives"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"valeur_key":{"en":"valeur_en","fr":"valeur_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{valeur_key}","fr":"{valeur_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"variables_et_unites_par_types_de_donnees":{"internationalizationName":{"en":"Variables and units by data type","fr":"Variables et unités par type de données"},"internationalizedColumns":null,"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"datatype name : {nom du type de données}, variable name : {nom de la variable}, : unit name {nom de l'unité}","fr":"nom du type de données : {nom du type de données}, nom de la variable : {nom de la variable}, : nom de l'unité {nom de l'unité}"}},"internationalizedValidations":{"uniteRef":{"fr":"référence à l'unité'"},"variableRef":{"fr":"référence à la variable"},"checkDatatype":{"fr":"test"}},"internationalizedTags":null},"sites":{"internationalizationName":{"en":"Site","fr":"Site"},"internationalizedColumns":{"zet_nom_key":{"en":"zet_nom_en","fr":"zet_nom_fr"},"zet_description_fr":{"en":"zet_description_en","fr":"zet_description_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{zet_nom_key}","fr":"{zet_nom_key}"}},"internationalizedValidations":{"typeSitesRef":{"fr":"référence au type de site"},"siteParentRef":{"fr":"référence à la colonne parent"}},"internationalizedTags":null},"types_de_donnees_par_themes_de_sites_et_projet":{"internationalizationName":{"en":"Data types by site and project","fr":"Types de données par site et projet"},"internationalizedColumns":null,"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"projet name: {nom du projet}, site name : {nom du site}, theme name : {nom du thème}, data type name : {nom du type de données}","fr":"nom du projet: {nom du projet}, nom du site : {nom du site}, nom du thème : {nom du thème}, nom du type de données : {nom du type de données}"}},"internationalizedValidations":{"sitesRef":{"fr":"référence au site"},"projetRef":{"fr":"référence au projet"},"themesRef":{"fr":"référence au theme"},"checkDatatype":{"fr":"test"}},"internationalizedTags":null}},"dataTypes":{"pem":{"internationalizationName":{"en":"Trap in ascent","fr":"Piégeage en Montée"},"internationalizedColumns":null,"authorization":{"dataGroups":{"referentiel":{"internationalizationName":{"en":"Repositories","fr":"Référentiels"}},"qualitatif":{"internationalizationName":{"en":"Qualitative","fr":"Qualitatif"}},"quantitatif":{"internationalizationName":{"en":"Quantitative","fr":"Quantitatif"}}},"authorizationScopes":{"localization":{"internationalizationName":{"en":"Localization","fr":"Localisation"}},"projet":{"internationalizationName":{"en":"Project","fr":"Projet"}}},"columnsDescription":{"depot":{"internationalizationName":{"en":"Deposit","fr":"Dépôt"}},"publication":{"internationalizationName":{"en":"Publication","fr":"Publication"}},"admin":{"internationalizationName":{"en":"Delegation","fr":"Délégation"}},"extraction":{"internationalizationName":{"en":"Extraction","fr":"Extraction"}},"delete":{"internationalizationName":{"en":"Deletion","fr":"Suppression"}}}},"internationalizationDisplay":{"especes":{"pattern":{"en":"espèce :{esp_nom}","fr":"espèce :{esp_nom}"}}},"internationalizedValidations":{"unitOfColor":{"fr":"vérifie l'unité de la couleur des individus"},"unitOfIndividus":{"fr":"vérifie l'unité du nombre d'individus"}}}},"internationalizedTags":{"data":{"en":"data","fr":"données"},"test":{"en":"test","fr":"test"},"context":{"en":"context","fr":"contexte"}},"rightsRequest":{"internationalizationName":null,"description":{"en":"You can request rights to the monsore application by filling out this form","fr":"Vous pouvez demander des droits à l'application monsore en remplissant ce formulaire"},"internationalizationDisplay":null,"internationalizedColumns":null,"format":{"endDate":{"en":"Project end date","fr":"Date de fin du projet"},"project":{"en":"Description of the research project","fr":"Description du projet de recherche"},"startDate":{"en":"Project start date","fr":"Date de début du projet"},"organization":{"en":"Name of research organization","fr":"Nom de l'organisme de recherche"},"projectManagers":{"en":"Project managers","fr":"Responsables du projet"}}}},"references":{"type_de_sites":{"id":"type_de_sites","label":"type_de_sites","children":[],"columns":{"tze_nom_en":{"id":"tze_nom_en","title":"tze_nom_en","key":false,"linkedTo":null},"tze_nom_fr":{"id":"tze_nom_fr","title":"tze_nom_fr","key":false,"linkedTo":null},"tze_nom_key":{"id":"tze_nom_key","title":"tze_nom_key","key":true,"linkedTo":null},"tze_definition_en":{"id":"tze_definition_en","title":"tze_definition_en","key":false,"linkedTo":null},"tze_definition_fr":{"id":"tze_definition_fr","title":"tze_definition_fr","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["context"]},"sites":{"id":"sites","label":"sites","children":[],"columns":{"zet_nom_en":{"id":"zet_nom_en","title":"zet_nom_en","key":false,"linkedTo":null},"zet_nom_fr":{"id":"zet_nom_fr","title":"zet_nom_fr","key":false,"linkedTo":null},"zet_nom_key":{"id":"zet_nom_key","title":"zet_nom_key","key":true,"linkedTo":null},"tze_type_nom":{"id":"tze_type_nom","title":"tze_type_nom","key":false,"linkedTo":null},"zet_chemin_parent":{"id":"zet_chemin_parent","title":"zet_chemin_parent","key":true,"linkedTo":null},"zet_description_en":{"id":"zet_description_en","title":"zet_description_en","key":false,"linkedTo":null},"zet_description_fr":{"id":"zet_description_fr","title":"zet_description_fr","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["context"]},"especes":{"id":"especes","label":"especes","children":[],"columns":{"esp_nom":{"id":"esp_nom","title":"esp_nom","key":true,"linkedTo":null},"esp_definition_en":{"id":"esp_definition_en","title":"esp_definition_en","key":false,"linkedTo":null},"esp_definition_fr":{"id":"esp_definition_fr","title":"esp_definition_fr","key":false,"linkedTo":null},"colonne_homonyme_entre_referentiels":{"id":"colonne_homonyme_entre_referentiels","title":"colonne_homonyme_entre_referentiels","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["data"]},"type de fichiers":{"id":"type de fichiers","label":"type de fichiers","children":[],"columns":{"nom_en":{"id":"nom_en","title":"nom_en","key":false,"linkedTo":null},"nom_fr":{"id":"nom_fr","title":"nom_fr","key":false,"linkedTo":null},"nom_key":{"id":"nom_key","title":"nom_key","key":true,"linkedTo":null},"description_en":{"id":"description_en","title":"description_en","key":false,"linkedTo":null},"description_fr":{"id":"description_fr","title":"description_fr","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["no-tag"]},"valeurs_qualitatives":{"id":"valeurs_qualitatives","label":"valeurs_qualitatives","children":[],"columns":{"nom_en":{"id":"nom_en","title":"nom_en","key":false,"linkedTo":null},"nom_fr":{"id":"nom_fr","title":"nom_fr","key":false,"linkedTo":null},"nom_key":{"id":"nom_key","title":"nom_key","key":true,"linkedTo":null},"valeur_en":{"id":"valeur_en","title":"valeur_en","key":false,"linkedTo":null},"valeur_fr":{"id":"valeur_fr","title":"valeur_fr","key":false,"linkedTo":null},"valeur_key":{"id":"valeur_key","title":"valeur_key","key":true,"linkedTo":null}},"dynamicColumns":{},"tags":["data"]},"variables":{"id":"variables","label":"variables","children":[],"columns":{"nom_en":{"id":"nom_en","title":"nom_en","key":false,"linkedTo":null},"nom_fr":{"id":"nom_fr","title":"nom_fr","key":false,"linkedTo":null},"nom_key":{"id":"nom_key","title":"nom_key","key":true,"linkedTo":null},"definition_en":{"id":"definition_en","title":"definition_en","key":false,"linkedTo":null},"definition_fr":{"id":"definition_fr","title":"definition_fr","key":false,"linkedTo":null},"isQualitative":{"id":"isQualitative","title":"isQualitative","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["data"]},"unites":{"id":"unites","label":"unites","children":[],"columns":{"nom_en":{"id":"nom_en","title":"nom_en","key":false,"linkedTo":null},"nom_fr":{"id":"nom_fr","title":"nom_fr","key":false,"linkedTo":null},"code_en":{"id":"code_en","title":"code_en","key":false,"linkedTo":null},"code_fr":{"id":"code_fr","title":"code_fr","key":false,"linkedTo":null},"nom_key":{"id":"nom_key","title":"nom_key","key":true,"linkedTo":null},"code_key":{"id":"code_key","title":"code_key","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["data"]},"variables_et_unites_par_types_de_donnees":{"id":"variables_et_unites_par_types_de_donnees","label":"variables_et_unites_par_types_de_donnees","children":[],"columns":{"nom de l'unité":{"id":"nom de l'unité","title":"nom de l'unité","key":false,"linkedTo":null},"nom de la variable":{"id":"nom de la variable","title":"nom de la variable","key":true,"linkedTo":null},"nom du type de données":{"id":"nom du type de données","title":"nom du type de données","key":true,"linkedTo":null}},"dynamicColumns":{},"tags":["data"]},"themes":{"id":"themes","label":"themes","children":[],"columns":{"nom_en":{"id":"nom_en","title":"nom_en","key":false,"linkedTo":null},"nom_fr":{"id":"nom_fr","title":"nom_fr","key":false,"linkedTo":null},"nom_key":{"id":"nom_key","title":"nom_key","key":true,"linkedTo":null},"description_en":{"id":"description_en","title":"description_en","key":false,"linkedTo":null},"description_fr":{"id":"description_fr","title":"description_fr","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["context"]},"projet":{"id":"projet","label":"projet","children":[],"columns":{"nom_en":{"id":"nom_en","title":"nom_en","key":false,"linkedTo":null},"nom_fr":{"id":"nom_fr","title":"nom_fr","key":false,"linkedTo":null},"nom_key":{"id":"nom_key","title":"nom_key","key":true,"linkedTo":null},"definition_en":{"id":"definition_en","title":"definition_en","key":false,"linkedTo":null},"definition_fr":{"id":"definition_fr","title":"definition_fr","key":false,"linkedTo":null},"colonne_homonyme_entre_referentiels":{"id":"colonne_homonyme_entre_referentiels","title":"colonne_homonyme_entre_referentiels","key":false,"linkedTo":null}},"dynamicColumns":{},"tags":["data","test","context"]},"types_de_donnees_par_themes_de_sites_et_projet":{"id":"types_de_donnees_par_themes_de_sites_et_projet","label":"types_de_donnees_par_themes_de_sites_et_projet","children":[],"columns":{"nom du site":{"id":"nom du site","title":"nom du site","key":true,"linkedTo":null},"nom du projet":{"id":"nom du projet","title":"nom du projet","key":true,"linkedTo":null},"nom du thème":{"id":"nom du thème","title":"nom du thème","key":true,"linkedTo":null},"nom du type de données":{"id":"nom du type de données","title":"nom du type de données","key":true,"linkedTo":null}},"dynamicColumns":{},"tags":["context"]}},"authorizationReferencesRights":{"authorizations":{"themes":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"especes":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"variables":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"type_de_sites":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"type de fichiers":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"unites":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"projet":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"valeurs_qualitatives":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"variables_et_unites_par_types_de_donnees":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"sites":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true},"types_de_donnees_par_themes_de_sites_et_projet":{"ADMIN":false,"DOWNLOAD":true,"DELETE":false,"PUBLICATION":false,"READ":true,"UPLOAD":false,"ANY":true}},"applicationName":"monsore","isAdministrator":false,"userId":"0ce09b24-6d86-4aac-9cd0-9fe7fce8ef58"},"referenceSynthesis":[{"referenceType":"variables","lineCount":2},{"referenceType":"especes","lineCount":7},{"referenceType":"type_de_sites","lineCount":2},{"referenceType":"types_de_donnees_par_themes_de_sites_et_projet","lineCount":9},{"referenceType":"themes","lineCount":1},{"referenceType":"projet","lineCount":2},{"referenceType":"valeurs_qualitatives","lineCount":3},{"referenceType":"type de fichiers","lineCount":3},{"referenceType":"sites","lineCount":9},{"referenceType":"unites","lineCount":1},{"referenceType":"variables_et_unites_par_types_de_donnees","lineCount":2}],"dataTypes":{},"authorizationsDatatypesRights":{},"rightsRequest":null,"configuration":{"requiredAuthorizationsAttributes":null,"version":0,"internationalization":{"application":{"internationalizationName":{"en":"SOERE my SOERE with repository","fr":"SOERE mon SOERE avec dépôt"}},"references":{"themes":{"internationalizationName":{"en":"Thematic","fr":"Thème"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"description_fr":{"en":"description_en","fr":"description_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"especes":{"internationalizationName":{"en":"Species","fr":"Espèces"},"internationalizedColumns":{"esp_definition_fr":{"en":"esp_definition_en","fr":"esp_definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{esp_nom}","fr":"{esp_nom}"}},"internationalizedValidations":{},"internationalizedTags":null},"variables":{"internationalizationName":{"en":"Variables","fr":"Variables"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"definition_fr":{"en":"definition_en","fr":"definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"type_de_sites":{"internationalizationName":{"en":"Sites types","fr":"Types de sites"},"internationalizedColumns":{"tze_nom_key":{"en":"tze_nom_en","fr":"tze_nom_fr"},"tze_definition_fr":{"en":"tze_definition_en","fr":"tze_definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{tze_nom_key}","fr":"{tze_nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"type de fichiers":{"internationalizationName":{"en":"Files types","fr":"Types de fichiers"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"description_fr":{"en":"description_en","fr":"description_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"unites":{"internationalizationName":{"en":"Units","fr":"Unités"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"code_key":{"en":"code_en","fr":"code_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key} ({code_key})","fr":"{nom_key} ({code_key})"}},"internationalizedValidations":{},"internationalizedTags":null},"projet":{"internationalizationName":{"en":"Project","fr":"Projet"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"definition_fr":{"en":"definition_en","fr":"definition_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"valeurs_qualitatives":{"internationalizationName":{"en":"Qualitative values","fr":"Valeurs qualitatives"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"valeur_key":{"en":"valeur_en","fr":"valeur_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{valeur_key}","fr":"{valeur_key}"}},"internationalizedValidations":{},"internationalizedTags":null},"variables_et_unites_par_types_de_donnees":{"internationalizationName":{"en":"Variables and units by data type","fr":"Variables et unités par type de données"},"internationalizedColumns":null,"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"datatype name : {nom du type de données}, variable name : {nom de la variable}, : unit name {nom de l'unité}","fr":"nom du type de données : {nom du type de données}, nom de la variable : {nom de la variable}, : nom de l'unité {nom de l'unité}"}},"internationalizedValidations":{"uniteRef":{"fr":"référence à l'unité'"},"variableRef":{"fr":"référence à la variable"},"checkDatatype":{"fr":"test"}},"internationalizedTags":null},"sites":{"internationalizationName":{"en":"Site","fr":"Site"},"internationalizedColumns":{"zet_nom_key":{"en":"zet_nom_en","fr":"zet_nom_fr"},"zet_description_fr":{"en":"zet_description_en","fr":"zet_description_fr"}},"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"{zet_nom_key}","fr":"{zet_nom_key}"}},"internationalizedValidations":{"typeSitesRef":{"fr":"référence au type de site"},"siteParentRef":{"fr":"référence à la colonne parent"}},"internationalizedTags":null},"types_de_donnees_par_themes_de_sites_et_projet":{"internationalizationName":{"en":"Data types by site and project","fr":"Types de données par site et projet"},"internationalizedColumns":null,"internationalizedDynamicColumns":{},"internationalizationDisplay":{"pattern":{"en":"projet name: {nom du projet}, site name : {nom du site}, theme name : {nom du thème}, data type name : {nom du type de données}","fr":"nom du projet: {nom du projet}, nom du site : {nom du site}, nom du thème : {nom du thème}, nom du type de données : {nom du type de données}"}},"internationalizedValidations":{"sitesRef":{"fr":"référence au site"},"projetRef":{"fr":"référence au projet"},"themesRef":{"fr":"référence au theme"},"checkDatatype":{"fr":"test"}},"internationalizedTags":null}},"dataTypes":{"pem":{"internationalizationName":{"en":"Trap in ascent","fr":"Piégeage en Montée"},"internationalizedColumns":null,"authorization":{"dataGroups":{"referentiel":{"internationalizationName":{"en":"Repositories","fr":"Référentiels"}},"qualitatif":{"internationalizationName":{"en":"Qualitative","fr":"Qualitatif"}},"quantitatif":{"internationalizationName":{"en":"Quantitative","fr":"Quantitatif"}}},"authorizationScopes":{"localization":{"internationalizationName":{"en":"Localization","fr":"Localisation"}},"projet":{"internationalizationName":{"en":"Project","fr":"Projet"}}},"columnsDescription":{"depot":{"internationalizationName":{"en":"Deposit","fr":"Dépôt"}},"publication":{"internationalizationName":{"en":"Publication","fr":"Publication"}},"admin":{"internationalizationName":{"en":"Delegation","fr":"Délégation"}},"extraction":{"internationalizationName":{"en":"Extraction","fr":"Extraction"}},"delete":{"internationalizationName":{"en":"Deletion","fr":"Suppression"}}}},"internationalizationDisplay":{"especes":{"pattern":{"en":"espèce :{esp_nom}","fr":"espèce :{esp_nom}"}}},"internationalizedValidations":{"unitOfColor":{"fr":"vérifie l'unité de la couleur des individus"},"unitOfIndividus":{"fr":"vérifie l'unité du nombre d'individus"}}}},"internationalizedTags":{"data":{"en":"data","fr":"données"},"test":{"en":"test","fr":"test"},"context":{"en":"context","fr":"contexte"}},"rightsRequest":{"internationalizationName":null,"description":{"en":"You can request rights to the monsore application by filling out this form","fr":"Vous pouvez demander des droits à l'application monsore en remplissant ce formulaire"},"internationalizationDisplay":null,"internationalizedColumns":null,"format":{"endDate":{"en":"Project end date","fr":"Date de fin du projet"},"project":{"en":"Description of the research project","fr":"Description du projet de recherche"},"startDate":{"en":"Project start date","fr":"Date de début du projet"},"organization":{"en":"Name of research organization","fr":"Nom de l'organisme de recherche"},"projectManagers":{"en":"Project managers","fr":"Responsables du projet"}}}},"comment":null,"application":{"internationalizationName":{"en":"SOERE my SOERE with repository","fr":"SOERE mon SOERE avec dépôt"},"internationalizedColumns":null,"name":"MONSORE","version":1,"defaultLanguage":"fr","internationalization":{"internationalizationName":{"en":"SOERE my SOERE with repository","fr":"SOERE mon SOERE avec dépôt"}}},"tags":{"data":{"en":"data","fr":"données"},"test":{"en":"test","fr":"test"},"context":{"en":"context","fr":"contexte"}},"rightsRequest":{"description":{"en":"You can request rights to the monsore application by filling out this form","fr":"Vous pouvez demander des droits à l'application monsore en remplissant ce formulaire"},"format":{"endDate":{"internationalizationName":{"en":"Project end date","fr":"Date de fin du projet"},"internationalizedColumns":null,"checker":{"name":"Date","params":{"pattern":"dd/MM/yyyy","refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}}},"project":{"internationalizationName":{"en":"Description of the research project","fr":"Description du projet de recherche"},"internationalizedColumns":null,"checker":{"name":"RegularExpression","params":{"pattern":".*","refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"ONE"}}},"startDate":{"internationalizationName":{"en":"Project start date","fr":"Date de début du projet"},"internationalizedColumns":null,"checker":{"name":"Date","params":{"pattern":"dd/MM/yyyy","refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}}},"organization":{"internationalizationName":{"en":"Name of research organization","fr":"Nom de l'organisme de recherche"},"internationalizedColumns":null,"checker":{"name":"RegularExpression","params":{"pattern":".*","refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}}},"projectManagers":{"internationalizationName":{"en":"Project managers","fr":"Responsables du projet"},"internationalizedColumns":null,"checker":{"name":"RegularExpression","params":{"pattern":".*","refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"MANY"}}}}},"references":{"type_de_sites":{"internationalizationName":{"en":"Sites types","fr":"Types de sites"},"internationalizedColumns":{"tze_nom_key":{"en":"tze_nom_en","fr":"tze_nom_fr"},"tze_definition_fr":{"en":"tze_definition_en","fr":"tze_definition_fr"}},"internationalizationDisplay":{"pattern":{"en":"{tze_nom_key}","fr":"{tze_nom_key}"}},"separator":";","keyColumns":["tze_nom_key"],"columns":{"tze_nom_en":null,"tze_nom_fr":null,"tze_nom_key":null,"tze_definition_en":null,"tze_definition_fr":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["context"]},"sites":{"internationalizationName":{"en":"Site","fr":"Site"},"internationalizedColumns":{"zet_nom_key":{"en":"zet_nom_en","fr":"zet_nom_fr"},"zet_description_fr":{"en":"zet_description_en","fr":"zet_description_fr"}},"internationalizationDisplay":{"pattern":{"en":"{zet_nom_key}","fr":"{zet_nom_key}"}},"separator":";","keyColumns":["zet_chemin_parent","zet_nom_key"],"columns":{"zet_nom_en":null,"zet_nom_fr":null,"zet_nom_key":null,"tze_type_nom":null,"zet_chemin_parent":null,"zet_description_en":null,"zet_description_fr":null},"computedColumns":{},"dynamicColumns":{},"validations":{"typeSitesRef":{"internationalizationName":{"fr":"référence au type de site"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"type_de_sites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["tze_type_nom"]},"siteParentRef":{"internationalizationName":{"fr":"référence à la colonne parent"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"sites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"ONE"}},"columns":["zet_chemin_parent"]}},"allowUnexpectedColumns":false,"tags":["context"]},"especes":{"internationalizationName":{"en":"Species","fr":"Espèces"},"internationalizedColumns":{"esp_definition_fr":{"en":"esp_definition_en","fr":"esp_definition_fr"}},"internationalizationDisplay":{"pattern":{"en":"{esp_nom}","fr":"{esp_nom}"}},"separator":";","keyColumns":["esp_nom"],"columns":{"esp_nom":null,"esp_definition_en":null,"esp_definition_fr":null,"colonne_homonyme_entre_referentiels":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["data"]},"type de fichiers":{"internationalizationName":{"en":"Files types","fr":"Types de fichiers"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"description_fr":{"en":"description_en","fr":"description_fr"}},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"separator":";","keyColumns":["nom_key"],"columns":{"nom_en":null,"nom_fr":null,"nom_key":null,"description_en":null,"description_fr":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":[]},"valeurs_qualitatives":{"internationalizationName":{"en":"Qualitative values","fr":"Valeurs qualitatives"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"valeur_key":{"en":"valeur_en","fr":"valeur_fr"}},"internationalizationDisplay":{"pattern":{"en":"{valeur_key}","fr":"{valeur_key}"}},"separator":";","keyColumns":["nom_key","valeur_key"],"columns":{"nom_en":null,"nom_fr":null,"nom_key":null,"valeur_en":null,"valeur_fr":null,"valeur_key":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["data"]},"unites":{"internationalizationName":{"en":"Units","fr":"Unités"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"code_key":{"en":"code_en","fr":"code_fr"}},"internationalizationDisplay":{"pattern":{"en":"{nom_key} ({code_key})","fr":"{nom_key} ({code_key})"}},"separator":";","keyColumns":["nom_key"],"columns":{"nom_en":null,"nom_fr":null,"code_en":null,"code_fr":null,"nom_key":null,"code_key":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["data"]},"variables":{"internationalizationName":{"en":"Variables","fr":"Variables"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"definition_fr":{"en":"definition_en","fr":"definition_fr"}},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"separator":";","keyColumns":["nom_key"],"columns":{"nom_en":null,"nom_fr":null,"nom_key":null,"definition_en":null,"definition_fr":null,"isQualitative":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["data"]},"variables_et_unites_par_types_de_donnees":{"internationalizationName":{"en":"Variables and units by data type","fr":"Variables et unités par type de données"},"internationalizedColumns":null,"internationalizationDisplay":{"pattern":{"en":"datatype name : {nom du type de données}, variable name : {nom de la variable}, : unit name {nom de l'unité}","fr":"nom du type de données : {nom du type de données}, nom de la variable : {nom de la variable}, : nom de l'unité {nom de l'unité}"}},"separator":";","keyColumns":["nom du type de données","nom de la variable"],"columns":{"nom de l'unité":null,"nom de la variable":null,"nom du type de données":null},"computedColumns":{},"dynamicColumns":{},"validations":{"uniteRef":{"internationalizationName":{"fr":"référence à l'unité'"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"unites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom de l'unité"]},"variableRef":{"internationalizationName":{"fr":"référence à la variable"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"variables","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom de la variable"]},"checkDatatype":{"internationalizationName":{"fr":"test"},"internationalizedColumns":null,"checker":{"name":"GroovyExpression","params":{"pattern":null,"refType":null,"groovy":{"expression":"String datatype = Arrays.stream(datum.get(\"nom du type de données\").split(\"_\")).collect{it.substring(0, 1)}.join(); return application.getDataType().contains(datatype);\n","references":[],"datatypes":[]},"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom du type de données"]}},"allowUnexpectedColumns":false,"tags":["data"]},"themes":{"internationalizationName":{"en":"Thematic","fr":"Thème"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"description_fr":{"en":"description_en","fr":"description_fr"}},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"separator":";","keyColumns":["nom_key"],"columns":{"nom_en":null,"nom_fr":null,"nom_key":null,"description_en":null,"description_fr":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["context"]},"projet":{"internationalizationName":{"en":"Project","fr":"Projet"},"internationalizedColumns":{"nom_key":{"en":"nom_en","fr":"nom_fr"},"definition_fr":{"en":"definition_en","fr":"definition_fr"}},"internationalizationDisplay":{"pattern":{"en":"{nom_key}","fr":"{nom_key}"}},"separator":";","keyColumns":["nom_key"],"columns":{"nom_en":null,"nom_fr":null,"nom_key":null,"definition_en":null,"definition_fr":null,"colonne_homonyme_entre_referentiels":null},"computedColumns":{},"dynamicColumns":{},"validations":{},"allowUnexpectedColumns":false,"tags":["context","data","test"]},"types_de_donnees_par_themes_de_sites_et_projet":{"internationalizationName":{"en":"Data types by site and project","fr":"Types de données par site et projet"},"internationalizedColumns":null,"internationalizationDisplay":{"pattern":{"en":"projet name: {nom du projet}, site name : {nom du site}, theme name : {nom du thème}, data type name : {nom du type de données}","fr":"nom du projet: {nom du projet}, nom du site : {nom du site}, nom du thème : {nom du thème}, nom du type de données : {nom du type de données}"}},"separator":";","keyColumns":["nom du projet","nom du site","nom du thème","nom du type de données"],"columns":{"nom du site":null,"nom du projet":null,"nom du thème":null,"nom du type de données":null},"computedColumns":{},"dynamicColumns":{},"validations":{"sitesRef":{"internationalizationName":{"fr":"référence au site"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"sites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom du site"]},"projetRef":{"internationalizationName":{"fr":"référence au projet"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"projet","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom du projet"]},"themesRef":{"internationalizationName":{"fr":"référence au theme"},"internationalizedColumns":null,"checker":{"name":"Reference","params":{"pattern":null,"refType":"themes","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom du thème"]},"checkDatatype":{"internationalizationName":{"fr":"test"},"internationalizedColumns":null,"checker":{"name":"GroovyExpression","params":{"pattern":null,"refType":null,"groovy":{"expression":"String datatype = Arrays.stream(datum.get(\"nom du type de données\").split(\"_\")).collect{it.substring(0, 1)}.join(); return application.getDataType().contains(datatype);\n","references":[],"datatypes":[]},"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"columns":["nom du type de données"]}},"allowUnexpectedColumns":false,"tags":["context"]}},"compositeReferences":{},"dataTypes":{"pem":{"internationalizationName":{"en":"Trap in ascent","fr":"Piégeage en Montée"},"internationalizedColumns":null,"internationalizationDisplays":{"especes":{"pattern":{"en":"espèce :{esp_nom}","fr":"espèce :{esp_nom}"}}},"format":{"headerLine":4,"firstRowLine":5,"separator":";","columns":[{"header":"projet","boundTo":{"variable":"projet","component":"value","id":"projet_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"},{"header":"site","boundTo":{"variable":"site","component":"bassin","id":"site_bassin","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"},{"header":"plateforme","boundTo":{"variable":"site","component":"plateforme","id":"site_plateforme","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"},{"header":"date","boundTo":{"variable":"date","component":"value","id":"date_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"},{"header":"espece","boundTo":{"variable":"espece","component":"value","id":"espece_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"},{"header":"Couleur des individus","boundTo":{"variable":"Couleur des individus","component":"value","id":"Couleur des individus_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"},{"header":"Nombre d'individus","boundTo":{"variable":"Nombre d'individus","component":"value","id":"Nombre d'individus_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},"presenceConstraint":"MANDATORY"}],"repeatedColumns":[],"constants":[],"allowUnexpectedColumns":false},"data":{"date":{"chartDescription":null,"components":{"value":{"checker":{"name":"Date","params":{"pattern":"dd/MM/yyyy","refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"ONE"}},"defaultValue":null}},"computedComponents":{}},"site":{"chartDescription":null,"components":{"bassin":null,"chemin":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"sites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"defaultValue":{"expression":"return references.get(\"sites\") .find{it.getRefValues().get(\"zet_chemin_parent\").equals(datum.site.bassin) && it.getRefValues().get(\"zet_nom_key\").equals(datum.site.plateforme)} .getHierarchicalKey();\n","references":["sites"],"datatypes":[]}},"plateforme":null},"computedComponents":{"site_bassin":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"sites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"computation":{"expression":"return references.get(\"sites\") .find{it.getNaturalKey().equals(datum.site.bassin)} .getHierarchicalKey();\n","references":["sites"],"datatypes":[]}}}},"espece":{"chartDescription":null,"components":{"value":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"especes","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"defaultValue":null}},"computedComponents":{}},"projet":{"chartDescription":null,"components":{"value":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"projet","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"defaultValue":null}},"computedComponents":{}},"Nombre d'individus":{"chartDescription":null,"components":{"unit":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"unites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"ONE"}},"defaultValue":{"expression":"return \"sans_unite\"","references":[],"datatypes":[]}},"value":{"checker":{"name":"Integer","params":{"pattern":null,"refType":null,"groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"ONE"}},"defaultValue":{"expression":"return 0","references":[],"datatypes":[]}}},"computedComponents":{}},"Couleur des individus":{"chartDescription":null,"components":{"unit":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"unites","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":false,"multiplicity":"ONE"}},"defaultValue":{"expression":"return \"sans_unite\"","references":[],"datatypes":[]}},"value":{"checker":{"name":"Reference","params":{"pattern":null,"refType":"valeurs_qualitatives","groovy":null,"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"defaultValue":null}},"computedComponents":{}}},"validations":{"unitOfColor":{"internationalizationName":{"fr":"vérifie l'unité de la couleur des individus"},"internationalizedColumns":null,"checker":{"name":"GroovyExpression","params":{"pattern":null,"refType":null,"groovy":{"expression":"String datatype = \"piegeage_en_montee\"; String variable = \"Couleur des individus\"; String codeVariable = \"couleur_des_individus\"; String component = \"unit\"; return referencesValues.get(\"variables_et_unites_par_types_de_donnees\") .findAll{it.get(\"nom du type de données\").equals(datatype)} .find{it.get(\"nom de la variable\").equals(codeVariable)} .get(\"nom de l'unité\").equals(datum.get(variable).get(component));\n","references":["variables_et_unites_par_types_de_donnees"],"datatypes":[]},"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"variableComponents":null},"unitOfIndividus":{"internationalizationName":{"fr":"vérifie l'unité du nombre d'individus"},"internationalizedColumns":null,"checker":{"name":"GroovyExpression","params":{"pattern":null,"refType":null,"groovy":{"expression":"String datatype = \"piegeage_en_montee\"; String variable = \"Nombre d'individus\"; String codeVariable = \"nombre_d_individus\"; String component = \"unit\"; return referencesValues.get(\"variables_et_unites_par_types_de_donnees\") .findAll{it.get(\"nom du type de données\").equals(datatype)} .find{it.get(\"nom de la variable\").equals(codeVariable)} .get(\"nom de l'unité\").equals(datum.get(variable).get(component));\n","references":["variables_et_unites_par_types_de_donnees"],"datatypes":[]},"duration":null,"transformation":{"codify":false,"groovy":null},"required":true,"multiplicity":"ONE"}},"variableComponents":null}},"uniqueness":[{"variable":"projet","component":"value","id":"projet_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},{"variable":"site","component":"chemin","id":"site_chemin","type":"PARAM_VARIABLE_COMPONENT_KEY"},{"variable":"date","component":"value","id":"date_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},{"variable":"espece","component":"value","id":"espece_value","type":"PARAM_VARIABLE_COMPONENT_KEY"}],"migrations":{},"authorization":{"timeScope":{"variable":"date","component":"value","id":"date_value","type":"PARAM_VARIABLE_COMPONENT_KEY"},"authorizationScopes":{"projet":{"internationalizationName":{"en":"Project","fr":"Projet"},"internationalizedColumns":null,"variable":"projet","component":"value","variableComponentKey":{"variable":"projet","component":"value","id":"projet_value","type":"PARAM_VARIABLE_COMPONENT_KEY"}},"localization":{"internationalizationName":{"en":"Localization","fr":"Localisation"},"internationalizedColumns":null,"variable":"site","component":"chemin","variableComponentKey":{"variable":"site","component":"chemin","id":"site_chemin","type":"PARAM_VARIABLE_COMPONENT_KEY"}}},"dataGroups":{"qualitatif":{"internationalizationName":{"en":"Qualitative","fr":"Qualitatif"},"internationalizedColumns":null,"label":"Données qualitatives","data":["Couleur des individus"]},"quantitatif":{"internationalizationName":{"en":"Quantitative","fr":"Quantitatif"},"internationalizedColumns":null,"label":"Données quantitatives","data":["Nombre d'individus"]},"referentiel":{"internationalizationName":{"en":"Repositories","fr":"Référentiels"},"internationalizedColumns":null,"label":"Référentiel","data":["date","site","projet","espece"]}},"columnsDescription":{"admin":{"internationalizationName":{"en":"Delegation","fr":"Délégation"},"internationalizedColumns":null,"display":true,"title":"admin","withPeriods":false,"withDataGroups":false,"forPublic":false,"forRequest":false},"depot":{"internationalizationName":{"en":"Deposit","fr":"Dépôt"},"internationalizedColumns":null,"display":true,"title":"depot","withPeriods":false,"withDataGroups":false,"forPublic":false,"forRequest":false},"delete":{"internationalizationName":{"en":"Deletion","fr":"Suppression"},"internationalizedColumns":null,"display":true,"title":"delete","withPeriods":false,"withDataGroups":false,"forPublic":false,"forRequest":false},"extraction":{"internationalizationName":{"en":"Extraction","fr":"Extraction"},"internationalizedColumns":null,"display":true,"title":"extraction","withPeriods":true,"withDataGroups":true,"forPublic":true,"forRequest":true},"publication":{"internationalizationName":{"en":"Publication","fr":"Publication"},"internationalizedColumns":null,"display":true,"title":"publication","withPeriods":false,"withDataGroups":false,"forPublic":false,"forRequest":false}},"internationalization":{"dataGroups":{"referentiel":{"internationalizationName":{"en":"Repositories","fr":"Référentiels"}},"qualitatif":{"internationalizationName":{"en":"Qualitative","fr":"Qualitatif"}},"quantitatif":{"internationalizationName":{"en":"Quantitative","fr":"Quantitatif"}}},"authorizationScopes":{"localization":{"internationalizationName":{"en":"Localization","fr":"Localisation"}},"projet":{"internationalizationName":{"en":"Project","fr":"Projet"}}},"columnsDescription":{"depot":{"internationalizationName":{"en":"Deposit","fr":"Dépôt"}},"publication":{"internationalizationName":{"en":"Publication","fr":"Publication"}},"admin":{"internationalizationName":{"en":"Delegation","fr":"Délégation"}},"extraction":{"internationalizationName":{"en":"Extraction","fr":"Extraction"}},"delete":{"internationalizationName":{"en":"Deletion","fr":"Suppression"}}}}},"repository":{"filePattern":"(.*)_(.*)_(.*)_(.*).csv","authorizationScope":{"projet":2,"localization":1},"startDate":{"token":3},"endDate":{"token":4}},"tags":["context","data","test"]}}},"isAdministrator":false}
            }).as('pageRef')

        cy.visit(Cypress.env('monsore_references_url'))
        cy.get('.button > :nth-child(1) > .icon').first().click()
        cy.get(':nth-child(5) > .button').contains('Consulter les autorisations')
        cy.get(':nth-child(5) > .button').should('be.disabled')
    })
})