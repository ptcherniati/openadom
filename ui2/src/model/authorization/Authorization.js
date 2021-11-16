export class Authorization {
dataGroups=[] ;
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
      this.dataGroups = [...(datagroupsOrAuthorization ||[])];
      this.from = from;
      this.to = to;
    }
  }
}