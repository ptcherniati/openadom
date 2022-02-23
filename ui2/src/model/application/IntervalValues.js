export class IntervalValues {
  from;
  to;
  intervalValues;
  constructor(fromOrIntervalValues, to) {
    if (typeof fromOrIntervalValues == "object") {
      Object.keys(this).forEach(
        (key) => (this[key] = fromOrIntervalValues[key] ? fromOrIntervalValues[key] : null)
      );
    } else {
      this.from = fromOrIntervalValues;
      this.to = to;
    }
  }
}
