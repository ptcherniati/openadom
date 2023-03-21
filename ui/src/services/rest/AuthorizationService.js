import { Fetcher } from "../Fetcher";

export class AuthorizationService extends Fetcher {
  static INSTANCE = new AuthorizationService();

  constructor() {
    super();
  }

  async getAuthorizations(applicationName, authorizationId) {
    return applicationName
      ? this.get(`applications/${applicationName}/authorization/${authorizationId}`)
      : this.get("authorization");
  }

  async getDataAuthorizations(applicationName) {
    return this.get(`applications/${applicationName}/authorization`);
  }

  async getReferencesAuthorizations(applicationName, params) {
    return this.get(`applications/${applicationName}/references/authorization`, params);
  }

  async getReferencesAuthorizationsForUser(applicationName, userId) {
    return this.get(
      `applications/${applicationName}/references/authorization/${userId ? userId : "null"}/`
    );
  }

  async revokeReferenceAuthorization(applicationName, id) {
    return this.delete(`applications/${applicationName}/references/authorization/${id}`);
  }

  async getAuthorizationGrantableInfos(applicationName) {
    return this.get(`applications/${applicationName}/grantable`);
  }

  async createAuthorization(applicationName, authorizationModel) {
    return this.post(`applications/${applicationName}/authorization`, authorizationModel, false);
  }

  async createOrUpdateReferencesAuthorization(authorization) {
    return this.post(
      `applications/${authorization.applicationNameOrId}/references/authorization`,
      authorization,
      false
    );
  }

  async createAuthorizedRole(roleName, userIdOrLogin, applicationPattern) {
    return this.put(`/authorization/${roleName}`, { applicationPattern, userIdOrLogin });
  }

  async revokeAuthorizedRole(roleName, userIdOrLogin, applicationPattern) {
    return this.delete(`authorization/${roleName}`, { applicationPattern, userIdOrLogin });
  }

  async revokeAuthorization(applicationName, authorizationId) {
    return this.delete(`applications/${applicationName}/authorization/${authorizationId}`);
  }
}
