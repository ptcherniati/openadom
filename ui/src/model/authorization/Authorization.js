export class Authorization {
  requiredAuthorizations = {};
  dataGroups = [];
  from = null;
  to = null;

  constructor(datagroupsOrAuthorization, requiredAuthorizations, from, to) {
    if (typeof datagroupsOrAuthorization == "object") {
      Object.assign(this, datagroupsOrAuthorization);
    } else {
      this.dataGroups = [...(datagroupsOrAuthorization || [])];
      this.from = from;
      this.to = to;
      this.requiredAuthorizations = requiredAuthorizations;
    }
  }

  getPath(scopeId) {
    var path = [];
    for (const scopeIdKey in scopeId) {
      if (this.requiredAuthorizations[scopeId[scopeIdKey]]) {
        path.push(this.requiredAuthorizations[scopeId[scopeIdKey]]);
      }
    }
    path = path.join(".");
    return path;
  }

  equals(auth, scopes) {
    for (const scope in scopes) {
      if (
        this.requiredAuthorizations[scopes[scope]] != auth.requiredAuthorizations[scopes[scope]]
      ) {
        return false;
      }
    }
    return true;
  }

  parse() {
    return {
      requiredAuthorizations: this.requiredAuthorizations,
      dataGroups: (this.dataGroups || []).map((dataGroups) => dataGroups.id),
      intervalDates: {
        fromDay: this.parseDate(this.from),
        toDay: this.parseDate(this.to),
      },
    };
  }

  parseDate(date) {
    let parsedDate = null;
    if (date) {
      parsedDate = [date.getFullYear(), date.getMonth() + 1, date.getDate()];
    }
    return parsedDate;
  }
}
