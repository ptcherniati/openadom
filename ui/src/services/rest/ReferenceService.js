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
  async getReferenceValuesByKey(applicationName, referenceType, referenceKey) {
    let params = { _row_key_: referenceKey };
    return this.get(`applications/${applicationName}/references/${referenceType}`, params);
  }

  async getReferenceCsv(applicationName, referenceId) {
    return this.get(`applications/${applicationName}/references/${referenceId}/csv`, {}, true);
  }

  async createReference(applicationName, referenceId, refFile) {
    return this.post(`applications/${applicationName}/references/${referenceId}`, {
      file: refFile,
    });
  }

  async deleteReferenceValuesByKey(applicationName, referenceId, referenceKey) {
    let params = { _row_key_: referenceKey };
    return this.delete(`applications/${applicationName}/references/${referenceId}`, params);
  }
  async deleteReference(applicationName, referenceId, params) {
    return this.delete(`applications/${applicationName}/references/${referenceId}`, params);
  }
}
