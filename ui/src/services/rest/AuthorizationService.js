import { Fetcher } from "../Fetcher";

export class AuthorizationService extends Fetcher {
  static INSTANCE = new AuthorizationService();

  constructor() {
    super();
  }

  async getAuthorizations(applicationName, dataTypeId, authorizationId) {
    return applicationName?this.get(
      `applications/${applicationName}/dataType/${dataTypeId}/authorization/${authorizationId}`
    ):
      this.get("authorization" )  ;
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

  async createAuthorizedRole(roleName, userIdOrLogin, applicationPattern) {
    return this.put(
        `/authorization/${roleName}`,
        {applicationPattern,userIdOrLogin}
    );
  }

  async revokeAuthorizedRole(roleName, userIdOrLogin, applicationPattern) {
    return this.delete(
        `authorization/${roleName}`,
        {applicationPattern,userIdOrLogin}
    );
  }

  async revokeAuthorization(applicationName, dataTypeId, authorizationId) {
    return this.delete(
      `applications/${applicationName}/dataType/${dataTypeId}/authorization/${authorizationId}`
    );
  }
}