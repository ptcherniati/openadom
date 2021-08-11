import { Fetcher } from "../Fetcher";

export class DataService extends Fetcher {
    static INSTANCE = new DataService();

    constructor() {
        super();
    }

    async getDataType(applicationName, dataTypeId, offset, limit) {
        return this.get(`applications/${applicationName}/data/${dataTypeId}`, {
            offset: offset,
            limit: limit,
        });
    }

    async getDataTypesCsv(applicationName, dataTypeId, offset, limit) {
        return this.downloadFile(`applications/${applicationName}/data/${dataTypeId}/csv`, {
            offset: offset,
            limit: limit,
        });
    }

    async addData(applicationName, dataTypeId, dataTypeFile) {
        return this.post(`applications/${applicationName}/data/${dataTypeId}`, {
            file: dataTypeFile,
        });
    }
}