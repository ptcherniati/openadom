import { Fetcher } from "../Fetcher";

export class DataService extends Fetcher {
  static INSTANCE = new DataService();

  constructor() {
    super();
  }

  async getDataType(applicationName, dataTypeId, params) {
    return this.get(`applications/${applicationName}/data/${dataTypeId}`, {
      downloadDatasetQuery: JSON.stringify(params),
    });
  }

  async getDataTypesCsv(applicationName, dataTypeId, params) {
    return this.getPath(
      `applications/${applicationName}/data/${dataTypeId}/csv`,
      {
        downloadDatasetQuery: JSON.stringify(params),
      },
      true
    );
  }

  getDataTypesCsvPath(applicationName, dataTypeId, params) {
    return this.getPath(
      `applications/${applicationName}/data/${dataTypeId}/csv`,
      {
        downloadDatasetQuery: JSON.stringify(params),
      },
      true
    );
  }

  async addData(applicationName, dataTypeId, dataTypeFile, params) {
    return this.post(`applications/${applicationName}/data/${dataTypeId}`, {
      file: dataTypeFile,
      params: JSON.stringify(params),
    });
  }
}