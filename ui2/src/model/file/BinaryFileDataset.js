export class BinaryFileDataset {
  datatype;
  requiredauthorizations = {};
  from;
  to;
  constructor(datatypeOrBinaryDataset, requiredauthorizations, from, to) {
    if (typeof datatypeOrBinaryDataset == "object") {
      Object.keys(this).forEach(
        (key) => (this[key] = datatypeOrBinaryDataset[key] ? datatypeOrBinaryDataset[key] : null)
      );
    } else {
      this.datatype = datatypeOrBinaryDataset;
      this.requiredauthorizations = requiredauthorizations == null ? {} : requiredauthorizations;
      this.from = from;
      this.to = to;
    }
  }
}
