import { Fetcher } from "../Fetcher";

export class FileService extends Fetcher {
  static INSTANCE = new FileService();

  constructor() {
    super();
  }

  async getFiles(applicationName, dataTypeId, params) {
    return this.get(`applications/${applicationName}/filesOnRepository/${dataTypeId}`, {
      repositoryId: JSON.stringify(params),
    });
  }

  async remove(applicationName, uuid) {
    return this.delete(`applications/${applicationName}/file/${uuid}`);
  }

  async download(applicationName, uuid) {
    return this.downloadFile(`applications/${applicationName}/file/${uuid}`);
  }
}
