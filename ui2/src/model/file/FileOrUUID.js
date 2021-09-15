import { BinaryFileDataset } from "./BinaryFileDataset";
export class FileOrUUID {
    fileid;
    binaryfiledataset;
    topublish;
    constructor(fileidOrFileOrUUID, binaryfiledataset, topublish) {
        if (this.fileid && typeof fileidOrFileOrUUID == "object") {
            Object.keys(this).forEach(
                (key) => (this[key] = fileidOrFileOrUUID[key] ? fileidOrFileOrUUID[key] : null)
            );
        } else {
            this.fileid = fileidOrFileOrUUID;
            this.binaryfiledataset = new BinaryFileDataset(binaryfiledataset);
            this.topublish = topublish;
        }
    }
}