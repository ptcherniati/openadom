import { Fetcher } from "../Fetcher";

export class AuthorizationService extends Fetcher {
  static INSTANCE = new AuthorizationService();

  constructor() {
    super();
  }

  async getDataAuthorizations(applicationName, dataTypeId) {
    return this.get(`applications/${applicationName}/dataType/${dataTypeId}/authorization`);
  }

  async getAuthorizationGrantableInfos(applicationName, dataTypeId) {
    return this.get(`applications/${applicationName}/dataType/${dataTypeId}/grantable`);
  }

  async createAuthorization(applicationName, dataTypeId, authorizationModel) {
    return this.post(
      `applications/${applicationName}/dataType/${dataTypeId}/authorization`,
      authorizationModel,
      false
    );
  }
}
