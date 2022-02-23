import { BinaryFileInfos } from "./BinaryFileInfos";
export class BinaryFile {
  id;
  params;
  application;
  name;
  size;
  data;
  constructor(params) {
    if (typeof params == "object") {
      Object.keys(this).forEach((key) => (this[key] = params[key] ? params[key] : null));
    }
    this.params = new BinaryFileInfos(this.params);
  }
  get createuser() {
    return (
      this.params &&
      this.params.createuser &&
      this.params.createuser.length &&
      this.params.createuser.slice(0, 8)
    );
  }
  get publisheduser() {
    return (
      this.params &&
      this.params.publisheduser &&
      this.params.publisheduser.length &&
      this.params.publisheduser.slice(0, 8)
    );
  }
}
