import {FieldFilters} from "@/model/additionalFiles/FieldFilters";

export class AdditionalFileInfos{
    fieldFilters;
    constructor(
    fieldFilters) {
        this.fieldFilters = fieldFilters?fieldFilters.map(fieldFilter=>new FieldFilters(
            fieldFilter.field,
            fieldFilter.filter,
            fieldFilter.type,
            fieldFilter.format,
            fieldFilter.intervalValues,
            fieldFilter.isRegExp
        )):[];
    }
}