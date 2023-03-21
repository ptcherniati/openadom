import { Fetcher } from "../Fetcher";

export class RequestRightsService extends Fetcher {
  static INSTANCE = new RequestRightsService();

  constructor() {
    super();
  }

  async getRightsRequests(applicationName, params) {
    if (params) {
      return this.get(
        `applications/${applicationName}/rightsRequest`,
        { params: JSON.stringify(params) },
        false
      );
    } else {
      return this.get(`applications/${applicationName}/rightsRequest`);
    }
  }

  async createRequestRights(applicationName, requestRights) {
    return this.post(`/applications/${applicationName}/rightsRequest`, requestRights, false);
  }
}
