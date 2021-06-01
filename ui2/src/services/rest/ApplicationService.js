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

  async getApplication(id) {
    return this.get("applications/" + id);
  }
}
