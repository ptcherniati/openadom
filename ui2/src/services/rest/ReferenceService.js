import { Fetcher } from "../Fetcher";

export class ReferenceService extends Fetcher {
  static INSTANCE = new ReferenceService();

  constructor() {
    super();
  }

  async getReferenceValues(applicationName, referenceId) {
    return this.get(`applications/${applicationName}/references/${referenceId}`);
  }

  async getReferenceCsv(applicationName, referenceId) {
    return this.downloadFile(`applications/${applicationName}/references/${referenceId}/csv`);
  }

  async createReference(applicationName, referenceId, refFile) {
    return this.post(`applications/${applicationName}/references/${referenceId}`, {
      file: refFile,
    });
  }
}
