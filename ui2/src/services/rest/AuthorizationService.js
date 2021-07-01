import { Fetcher } from "../Fetcher";

export class AuthorizationService extends Fetcher {
  static INSTANCE = new AuthorizationService();

  constructor() {
    super();
  }

  async getDataAuthorizations(applicationName, dataTypeId) {
    return this.get(`applications/${applicationName}/dataType/${dataTypeId}/authorization`);
  }
}
