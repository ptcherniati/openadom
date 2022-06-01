import {Authorization} from "@/model/authorization/Authorization";

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
    #scopesId = []
    scopes = {}
    application = "";
    dataType = "";
    id = "";
    name = "";
    users = [];
    authorizations = this.ROLE;

    constructor(authorizations, authorizationsScope) {
        this.#scopesId = authorizationsScope
        this.users = authorizations.users;
        this.dataType = authorizations.dataType;
        this.name = authorizations.name;
        this.id = authorizations.id;
        this.#initStates(authorizations.authorizations);
    }

    #initStates(authorizations){
        this.authorizations = authorizations;
        for (const scope in authorizations) {
            const scopeId = this.#scopesId;
            this.scopes = authorizations[scope]
                .reduce((acc, auth) => {
                    auth = new Authorization(auth);
                    acc[scope] = acc[scope] || {}
                    acc[scope][auth.getPath(scopeId)] = auth;
                    return acc
                }, {})
        }
    }

    getState(scope, path){
        for (const scopeKey in  this.scopes[scope]) {
            if (path.startsWith(scopeKey)){
                return 1
            }else if (scopeKey.startsWith(path)){
                return -1
            }
        }
        return 0;

    }

    getCheckedAuthorization(scope, currentPath){
        for (const scopeKey in  this.scopes[scope]) {
            if (currentPath.startsWith(scopeKey)) {
                return {scopeKey, auth:this.scopes[scope][scopeKey]}
            }
        }

    }
}