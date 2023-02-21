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
    static  getRefForRet(ret){
        return  Object.values(ret)
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
                ret[key]?.authorizationScope
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
        let returnValues = {};
        for (const referenceValue of referencesValues) {
            var previousKeySplit = currentPath ? currentPath.split(".") : [];
            var keys = referenceValue.hierarchicalKey.split(".");
            var references = referenceValue.hierarchicalReference.split(".");
            if (previousKeySplit.length == keys.length) {
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
                    auth.completeLocalName
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
}