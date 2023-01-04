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

  async getApplications(filter) {
    var applications = await this.get("applications/", {filter});
    return applications.map((a) => {
      return InternationalisationService.INSTANCE.mergeInternationalization(a);
    });
  }

  async getApplication(name, filter) {
    var application = await this.get("applications/" + name, {filter});
    return InternationalisationService.INSTANCE.mergeInternationalization(application);
  }

  async validateConfiguration(applicationConfig) {
    return this.post("validate-configuration", {
      file: applicationConfig.file,
    });
  }
  async changeConfiguration(applicationConfig, comment) {
    return this.post("/applications/" + applicationConfig.name + "/configuration", {
      file: applicationConfig.file,
      comment: comment,
    });
  }

  async getValidateConfiguration() {
    return this.post("validate-configuration");
  }
}