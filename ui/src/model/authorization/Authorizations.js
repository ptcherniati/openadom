import { Authorization } from "@/model/authorization/Authorization";

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

  #scopesId = [];
  scopes = {};
  uuid = "";
  applicationNameOrId = "";
  dataType = "";
  name = "";
  users = [];
  authorizations = this.ROLE;

  constructor(authorizations, authorizationsScope) {
    this.#scopesId = authorizationsScope;
    this.users = authorizations.users || [];
    this.applicationNameOrId = authorizations.applicationNameOrId;
    this.dataType = authorizations.dataType;
    this.name = authorizations.name;
    this.uuid = authorizations.uuid;
    this.#initStates(authorizations.authorizations);
  }

  #initStates(authorizations) {
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
      this.scopes = { ...this.scopes, ...scopes };
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
        return { scopeKey, auth: this.scopes[scope][scopeKey] };
      }
    }
  }
}
