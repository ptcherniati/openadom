import { Fetcher } from "../Fetcher";

export class ApplicationService extends Fetcher {
  static INSTANCE = new ApplicationService();

  constructor() {
    super();
  }

  async createApplication(applicationConfig) {
    return this.post("applications/" + applicationConfig.name, {
      file: applicationConfig.file,
    });
  }

  async getApplications() {
    return this.get("applications/");
  }

  async getApplication(name) {
    return this.get("applications/" + name);
  }

  async getDataset(dataset, applicationName) {
    return this.get(`applications/${applicationName}/data/${dataset}`);
  }

  async getReference(reference, applicationName) {
    return this.get(`applications/${applicationName}/references/${reference}`);
  }
}
