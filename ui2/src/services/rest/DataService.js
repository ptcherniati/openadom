import { Fetcher } from "../Fetcher";

export class DataService extends Fetcher {
  static INSTANCE = new DataService();

  constructor() {
    super();
  }

  async getDataType(applicationName, dataTypeId) {
    return this.get(`applications/${applicationName}/data/${dataTypeId}`);
  }

  async getDataTypesCsv(applicationName, dataTypeId) {
    return this.get(`applications/${applicationName}/data/${dataTypeId}/csv`);
  }

  async addData(applicationName, dataTypeId, dataTypeFile) {
    return this.post(`applications/${applicationName}/data/${dataTypeId}`, {
      file: dataTypeFile,
    });
  }
}
