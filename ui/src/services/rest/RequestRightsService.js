import { Fetcher } from "../Fetcher";

export class RequestRightsService extends Fetcher {
  static INSTANCE = new RequestRightsService();

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

  async createRequestRights(applicationName, requestRights) {
    return this.post(`/applications/${applicationName}/rightsRequest`,  requestRights, false);
  }
}