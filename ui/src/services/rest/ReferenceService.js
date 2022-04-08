import { Fetcher } from "../Fetcher";

export class ReferenceService extends Fetcher {
  static INSTANCE = new ReferenceService();

  constructor() {
    super();
  }

  async getReferenceValues(applicationName, referenceId, params) {
    if (params) {
      return this.get(`applications/${applicationName}/references/${referenceId}`, params);
    } else {
      return this.get(`applications/${applicationName}/references/${referenceId}`);
    }
  }

  async getReferenceCsv(applicationName, referenceId) {
    return this.get(`applications/${applicationName}/references/${referenceId}/csv`, {}, true);
  }

  async createReference(applicationName, referenceId, refFile) {
    return this.post(`applications/${applicationName}/references/${referenceId}`, {
      file: refFile,
    });
  }
}