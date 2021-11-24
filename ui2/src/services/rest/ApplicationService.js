import { Fetcher } from "../Fetcher";
import { InternationalisationService } from "@/services/InternationalisationService";

export class ApplicationService extends Fetcher {
  static INSTANCE = new ApplicationService();

  constructor() {
    super();
  }

  async createApplication(applicationConfig, comment) {
    return this.post("applications/" + applicationConfig.name, {
      file: applicationConfig.file,
      comment: comment,
    });
  }

  async getApplications() {
    var applications = await this.get("applications/");
    return applications.map((a) => {
      return InternationalisationService.INSTANCE.mergeInternationalization(a);
    });
  }

  async getApplication(name) {
    var application = await this.get("applications/" + name);
    return InternationalisationService.INSTANCE.mergeInternationalization(application);
  }

  async validateConfiguration(applicationConfig) {
    return this.post("validate-configuration", {
      file: applicationConfig.file,
    });
  }

  async getValidateConfiguration() {
    return this.post("validate-configuration");
  }
}
