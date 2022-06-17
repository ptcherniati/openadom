export class BinaryFileDataset {
  datatype;
  requiredAuthorizations = {};
  from;
  to;
  comment;
  constructor(datatypeOrBinaryDataset, requiredAuthorizations, from, to, comment) {
    if (typeof datatypeOrBinaryDataset == "object") {
      Object.keys(this).forEach(
        (key) => (this[key] = datatypeOrBinaryDataset[key] ? datatypeOrBinaryDataset[key] : null)
      );
    } else {
      this.datatype = datatypeOrBinaryDataset;
      this.requiredAuthorizations = requiredAuthorizations == null ? {} : requiredAuthorizations;
      this.from = from;
      this.to = to;
      this.comment = comment;
    }
  }
}
