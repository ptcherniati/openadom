import { IntervalValues } from "@/model/application/IntervalValues";
export class FieldFilters {
  field;
  filter;
  type;
  format;
  intervalValues;
  isRegExp;
  constructor(field, filter, type, format, intervalValues, isRegExp) {
    this.field = field;
    this.filter = filter;
    this.type = type;
    this.format = format;
    this.intervalValues = new IntervalValues(intervalValues);
    this.isRegExp = isRegExp;
  }
}
