export class Authorization {
    requiredAuthorization = {}
    dataGroups = [];
    from = null;
    to = null;

    constructor(datagroupsOrAuthorization, requiredAuthorization, from, to) {
        if (typeof datagroupsOrAuthorization == "object") {
            Object.keys(this).forEach(
                (key) =>
                    (this[key] = datagroupsOrAuthorization[key]
                        ? datagroupsOrAuthorization[key]
                        : null)
            );
            this.requiredAuthorization = requiredAuthorization
        } else {
            this.dataGroups = [...(datagroupsOrAuthorization || [])];
            this.from = from;
            this.to = to;
            this.requiredAuthorization = requiredAuthorization
        }
    }

    parse() {
        return {
            requiredauthorizations: this.requiredAuthorization,
            dataGroup:(this.dataGroups || []).map(dataGroups => dataGroups.id),
            intervalDates: {
                fromDay: this.parseDate(this.from),
                toDay: this.parseDate((this.to)),
            }
        }
    }

    parseDate(date) {
        let parsedDate = null;
        if (date) {
            parsedDate = [
                date.getFullYear(),
                date.getMonth() + 1,
                date.getDate(),
            ];
        }
        return parsedDate
    }
}