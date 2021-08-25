import { Application } from "../Application";
import { VariableComponentFilters } from "./VariableComponentFilters";
import { VariableComponentOrderBy } from "./VariableComponentOrderBy";

export class DownloadDatasetQuery {
    application = new Application();
    applicationNameOrId;
    dataType;
    offset = 0;
    limit = 15;
    variableComponentSelects = [];
    variableComponentFilters = [];
    variableComponentOrderBy = [];
    constructor(
        downloadDatasetQueryOrApplication,
        applicationNameOrId,
        dataType,
        offset = 0,
        limit = 15,
        variableComponentSelects,
        variableComponentFilters,
        variableComponentOrderBy
    ) {
        if (typeof downloadDatasetQueryOrApplication == "object") {
            Object.keys(this).forEach(
                (key) =>
                (this[key] = downloadDatasetQueryOrApplication[key] ?
                    downloadDatasetQueryOrApplication[key] :
                    null)
            );
        } else {
            this.variableComponentKey = downloadDatasetQueryOrApplication;
            this.applicationNameOrId = applicationNameOrId;
            this.dataType = dataType;
            this.offset = offset ? offset : 0;
            this.limit = limit ? limit : 15;
            this.variableComponentSelects = [];
            for (const select in variableComponentSelects) {
                this.variableComponentSelects.push(select);
            }
            this.variableComponentFilters = [];
            for (const filter in variableComponentFilters) {
                this.variableComponentFilters.push(new VariableComponentFilters(filter));
            }
            this.variableComponentOrderBy = [];
            for (const orderBy in variableComponentOrderBy) {
                this.variableComponentOrderBy.push(new VariableComponentOrderBy(orderBy));
            }
        }
    }
}