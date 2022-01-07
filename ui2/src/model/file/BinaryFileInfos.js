import { BinaryFileDataset } from "./BinaryFileDataset";
export class BinaryFileInfos {
  binaryFiledataset;
  published = false;
  publisheduser;
  publisheddate;
  createuser;
  createdate;
  datatype;
  constructor(binaryFileInfos) {
    if (typeof binaryFileInfos == "object") {
      Object.keys(this).forEach(
        (key) => (this[key] = binaryFileInfos[key] != null ? binaryFileInfos[key] : null)
      );
      this.binaryFiledataset = new BinaryFileDataset(this.binaryFiledataset);
    }
  }
}