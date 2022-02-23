export class BinaryFileDataset {
  datatype;
  requiredauthorizations = {};
  from;
  to;
  comment;
  constructor(datatypeOrBinaryDataset, requiredauthorizations, from, to, comment) {
    if (typeof datatypeOrBinaryDataset == "object") {
      Object.keys(this).forEach(
        (key) => (this[key] = datatypeOrBinaryDataset[key] ? datatypeOrBinaryDataset[key] : null)
      );
    } else {
      this.datatype = datatypeOrBinaryDataset;
      this.requiredauthorizations = requiredauthorizations == null ? {} : requiredauthorizations;
      this.from = from;
      this.to = to;
      this.comment = comment;
    }
  }
}
