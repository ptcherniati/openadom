import {Authorization} from "@/model/authorization/Authorization";
import {LOCAL_STORAGE_LANG} from "@/services/Fetcher";
import {InternationalisationService} from "@/services/InternationalisationService";

export class Authorizations {
    static ROLES() {
        return {
            suppression: [],
            extraction: [],
            admin: [],
            depot: [],
            publication: [],
        };
    }

    static internationalizationService = InternationalisationService.INSTANCE;
    static COLUMNS_VISIBLE = {
        label: {
            title: "Label",
            display: true,
            forPublic: true,
            internationalizationName: {fr: "Domaine", en: "Domain"},
        },
    };

    #scopesId = [];
    scopes = {};
    uuid = "";
    applicationNameOrId = "";
    dataType = "";
    name = "";
    users = [];
    authorizations = this.ROLES;

    constructor(authorizations, authorizationsScope) {
        this.#scopesId = authorizationsScope;
        this.users = authorizations.users || [];
        this.applicationNameOrId = authorizations.applicationNameOrId;
        this.dataType = authorizations.dataType;
        this.name = authorizations.name;
        this.uuid = authorizations.uuid;
        this.initStates(authorizations.authorizations);
    }

    initStates(authorizations) {
        this.authorizations = authorizations || {};
        this.scopes = {};
        for (const scope in authorizations) {
            const scopeId = this.#scopesId;
            let scopes = authorizations[scope].reduce((acc, auth) => {
                auth = new Authorization(auth);
                acc[scope] = acc[scope] || {};
                acc[scope][auth.getPath(scopeId)] = auth;
                return acc;
            }, {});
            this.scopes = {...this.scopes, ...scopes};
        }
    }

    getDependants(scope, path) {
        if (this.authorizations[scope]) {
            return this.authorizations[scope].filter((auth) => {
                let pathToCompare = new Authorization(auth).getPath(this.#scopesId);
                if (pathToCompare.startsWith(path) && pathToCompare != path) {
                    return true;
                }
                return false;
            });
        }
        return [];
    }

    getState(scope, path, publicState) {
        let state = {
            state: 0,
            fromPath: "",
            dataGroups: [],
            from: null,
            to: null,
            fromAuthorization: null,
            publicState,
        };
        if (this.authorizations[scope]) {
            for (const auth in this.authorizations[scope]) {
                let authorizationElement = new Authorization(this.authorizations[scope][auth]);
                let pathToCompare = authorizationElement.getPath(this.#scopesId);
                if (path.startsWith(pathToCompare)) {
                    state.fromPath = pathToCompare;
                    state.fromAuthorization = authorizationElement;
                    state.fromDay = authorizationElement.fromDay;
                    state.toDay = authorizationElement.toDay;
                    state.from = authorizationElement.fromDay ? new Date(authorizationElement.fromDay) : null;
                    state.to = authorizationElement.toDay ? new Date(authorizationElement.toDay) : null;
                    state.dataGroups = authorizationElement.dataGroups;
                    state.hasPublicStates = false;
                    state.localState = 0;
                }
            }
        }
        for (const scopeKey in this.scopes[scope]) {
            if (path.startsWith(scopeKey)) {
                state.state = 1;
                state.localState = 1;
                return state;
            } else if (scopeKey.startsWith(path)) {
                state.state = -1;
                state.localState = -1;
                return state;
            }
        }
        if (state.state == 0 && state.publicState != 0) {
            state.hasPublicStates = true;
            state.localState = state.publicState;
        } else if (state.publicState == 1 && state.state == 1) {
            state.hasPublicStates = true;
            state.localState = state.publicState;
        } else {
            state.hasPublicStates = false;
            state.localState = state.state;
        }
        return state;
    }

    getCheckedAuthorization(scope, currentPath) {
        for (const scopeKey in this.scopes[scope]) {
            if (currentPath.startsWith(scopeKey)) {
                return {scopeKey, auth: this.scopes[scope][scopeKey]};
            }
        }
    }

    static getRefForRet(ret) {
        return Object.values(ret)
            .reduce(
                (acc, k) => [
                    ...acc,
                    ...k.references.referenceValues.reduce(
                        (a, b) => [...a, ...b.hierarchicalReference.split(".")],
                        acc
                    ),
                ],
                []
            )
            .reduce((a, b) => {
                if (a.indexOf(b) < 0) {
                    a.push(b);
                }
                return a;
            }, []);
    }

    static async remainingAuthorizations(ret, getOrLoadReferences) {
        var remainingAuthorizations = [];
        for (const key in ret) {
            let partition = await Authorizations.partitionReferencesValues(
                getOrLoadReferences,
                ret[key]?.references?.referenceValues,
                ret[key]?.authorizationScope,
                null,
                null
            );
            remainingAuthorizations[key] = partition;
        }
        if (!remainingAuthorizations.length) {
            remainingAuthorizations = [
                {
                    __DEFAULT__: {
                        authorizationScope: {
                            id: "__DEFAULT__",
                            localName: "root",
                        },
                        completeLocalName: "__.__",
                        currentPath: "__.__",
                        isLeaf: true,
                        localName: "__.__fr",
                        reference: {},
                        referenceValues: {},
                    },
                },
            ];
        }
        return remainingAuthorizations
    }

    static async partitionReferencesValues(
        getOrLoadReferences,
        referencesValues,
        authorizationScope,
        currentPath,
        currentCompleteLocalName
    ) {
        let
            returnValues = {};

        for (

            const
                referenceValue
            of
            referencesValues
            ) {
            var
                previousKeySplit = currentPath ? currentPath.split(".") : [];
            var
                keys = referenceValue.hierarchicalKey.split(".");
            var
                references = referenceValue.hierarchicalReference.split(".");

            if (previousKeySplit

                    .length
                ==
                keys
                    .length
            ) {
                continue;
            }

            for (let i = 0; i < previousKeySplit.length; i++) {
                keys.shift();
                references.shift();
            }
            var key = keys.shift();
            let newCurrentPath = (currentPath ? currentPath + "." : "") + key;
            var reference = references.shift();
            let refValues = await getOrLoadReferences(reference);
            Authorizations.internationalizationService.getUserPrefLocale();
            let lang = localStorage.getItem(LOCAL_STORAGE_LANG);
            let localName = refValues.referenceValues.find((r) => r.naturalKey == key);
            if (localName?.values?.["__display_" + lang]) {
                localName = localName?.values?.["__display_" + lang];
            } else {
                localName = key;
            }
            if (!localName) {
                localName = key;
            }
            var completeLocalName =
                typeof currentCompleteLocalName === "undefined" ? "" : currentCompleteLocalName;
            completeLocalName = completeLocalName + (completeLocalName == "" ? "" : ",") + localName;
            let authPartition = returnValues[key] || {
                key,
                reference,
                authorizationScope,
                referenceValues: [],
                localName,
                isLeaf: false,
                currentPath: newCurrentPath,
                completeLocalName,
            };
            authPartition.referenceValues.push(referenceValue);
            returnValues[key] = authPartition;
        }
        for (const returnValuesKey in returnValues) {
            var auth = returnValues[returnValuesKey];
            let referenceValueLeaf = auth.referenceValues?.[0];
            if (
                auth.referenceValues.length <= 1 &&
                referenceValueLeaf.hierarchicalKey == auth.currentPath
            ) {
                returnValues[returnValuesKey] = {
                    ...auth,
                    authorizationScope,
                    isLeaf: true,
                    referenceValues: {...referenceValueLeaf, authorizationScope},
                };
            } else {
                var r = await this.partitionReferencesValues(
                    getOrLoadReferences,
                    auth.referenceValues,
                    authorizationScope,
                    auth.currentPath,
                    auth.completeLocalName,
                );
                returnValues[returnValuesKey] = {
                    ...auth,
                    isLeaf: false,
                    referenceValues: r,
                };
            }
        }
        return returnValues;
    }

    static parseGrantableInfos(grantableInfos, datatypes, isRepository) {
        let parsing = {
            authorizationScopes: {},
            dataGroups: {},
            users: [],
            publicUser: [],
            publicAuthorizations: {},
            isApplicationAdmin: false,
            ownAuthorizations: {},
            ownAuthorizationsColumnsByPath: {},
            columnsVisible: {},
            columnsVisibleForPublic: {},
        }
        let authorizationsForUser;
        ({
            authorizationScopes: parsing.authorizationScopes,
            dataGroups: parsing.dataGroups,
            users: parsing.users,
            authorizationsForUser: authorizationsForUser,
            publicAuthorizations: parsing.publicAuthorizations,
        } = grantableInfos);
        parsing.publicUser = parsing.users.shift()
        parsing.isApplicationAdmin = authorizationsForUser.isAdministrator
        let authorizationsForUserByPath = authorizationsForUser.authorizationByPath;
        let ownAuthorizationsForUser = authorizationsForUser.authorizationResults;
        for (const datatype in datatypes) {
            let ownAuthorizationsforDatatype = [];
            for (const scope in ownAuthorizationsForUser[datatype]) {
                let scopeAuthorizations = ownAuthorizationsForUser[datatype][scope];
                scopeAuthorizations
                    .map(auth => new Authorization(auth))
                    .filter(auth => {
                        const path = auth.getPath(parsing.authorizationScopes[datatype].map((a) => a.id));
                        return ownAuthorizationsforDatatype.indexOf(path) === -1 &&
                            !ownAuthorizationsforDatatype.find((pa) => path.startsWith(pa));
                    })
                    .map(auth => auth.path)
                    .forEach(auth => ownAuthorizationsforDatatype.push(auth))
                parsing.ownAuthorizations[datatype] = ownAuthorizationsforDatatype;
            }
            let ownAuthorizationsColumnsByPathForDatatype = {}
            for (const path of (parsing.ownAuthorizations[datatype] || [])) {
                for (const scopeId in authorizationsForUserByPath[datatype]) {
                    if (authorizationsForUserByPath[datatype][scopeId]) {
                        for (const pathKey in authorizationsForUserByPath[datatype][scopeId]) {
                            if (pathKey.startsWith(path) || path.startsWith(pathKey)) {
                                let autorizedPath = pathKey.startsWith(path) ? path : pathKey;
                                ownAuthorizationsColumnsByPathForDatatype[autorizedPath] =
                                    ownAuthorizationsColumnsByPathForDatatype[autorizedPath] || [];
                                ownAuthorizationsColumnsByPathForDatatype[autorizedPath].push(scopeId);
                            }
                        }
                    }
                }
            }
            parsing.ownAuthorizationsColumnsByPath[datatype] = ownAuthorizationsColumnsByPathForDatatype;
            let columnsVisibleForDatatype = {...(this.COLUMNS_VISIBLE || {}), ...grantableInfos.columnsDescription[datatype]};
            if (!isRepository) {
                columnsVisibleForDatatype.publication = {...columnsVisibleForDatatype.publication, display: false};
            }
            parsing.columnsVisible[datatype] = columnsVisibleForDatatype
            columnsVisibleForDatatype = {}
            for (const scope in parsing.columnsVisible[datatype]) {
                let columnsVisibleFordatatypeAndScope = parsing.columnsVisible[datatype][scope]
                if (columnsVisibleFordatatypeAndScope.forPublic) {
                    columnsVisibleForDatatype[scope] = columnsVisibleFordatatypeAndScope;
                }
            }
            parsing.columnsVisibleForPublic[datatype] = columnsVisibleForDatatype;
        }
        this.extractPublicAuthorizations(parsing.publicAuthorizations, parsing.authorizationScopes)
        return parsing;
    }

    static extractPublicAuthorizations(publicAuthorizations, authorizationScopes) {
        let publicAuthorizationToReturn = {};
        for (const datatype in publicAuthorizations) {
            let auths = publicAuthorizations[datatype];
            for (const scope in auths) {
                publicAuthorizationToReturn[scope] = [];
                let scopeAuthorizations = auths[scope];
                for (const scopeAuthorizationsKey in scopeAuthorizations) {
                    let scopeAuthorization = new Authorization(
                        scopeAuthorizations[scopeAuthorizationsKey]
                    );
                    let path = scopeAuthorization.getPath2(authorizationScopes[datatype].map((a) => a.id));
                    if (publicAuthorizationToReturn[scope].indexOf(path) === -1) {
                        if (!publicAuthorizationToReturn[scope]
                            .find(
                                (pa) => path.startsWith(pa)
                            )
                        ) {
                            publicAuthorizationToReturn[scope] = publicAuthorizationToReturn[scope]
                                .filter(
                                    (pa) => !pa.startsWith(path)
                                );
                            publicAuthorizationToReturn[scope].push(path);
                        }
                    }
                }
            }
            publicAuthorizations[datatype] = publicAuthorizationToReturn;
        }
        return publicAuthorizations;
    }

    static
    async initAuthReferences(configuration, authorizations, authorizationScopes, getOrLoadReferences) {
        let authReferences = {}
        for (const datatype in authorizationScopes) {
            let info = authorizationScopes[datatype]
            info.reverse()
            let ret = {};
            for (let auth in info) {
                let authorizationScope = info[auth];
                let vc = authorizations[datatype][authorizationScope?.label];
                var reference =
                    configuration[datatype].data[vc.variable].components[vc.component].checker.params.refType;
                let ref = await getOrLoadReferences(reference);
                ret[auth] = {references: ref, authorizationScope: authorizationScope.label};
            }
            let refs = Authorizations.getRefForRet(ret)
            var remainingAuthorizations = await Authorizations.remainingAuthorizations(ret, getOrLoadReferences);
            authReferences[datatype] = remainingAuthorizations;

            for (const refsKey in refs) {
                await getOrLoadReferences(refs[refsKey]);
            }
        }
        return authReferences;
    }
}