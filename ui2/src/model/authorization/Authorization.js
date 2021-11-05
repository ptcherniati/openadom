export class Authorization {
datagroups=[] ;
from=null;
to=null;

  constructor(datagroupsOrAuthorization, from, to) {
    if (typeof datagroupsOrAuthorization == "object") {
      Object.keys(this).forEach(
          (key) =>
              (this[key] = datagroupsOrAuthorization[key]
                  ? datagroupsOrAuthorization[key]
                  : null)
      );
    } else {
      this.datagroups = datagroupsOrAuthorization;
      this.from = from;
      this.to = to;
    }
  }
}