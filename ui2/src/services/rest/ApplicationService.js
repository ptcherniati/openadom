import { Fetcher } from "../Fetcher";
import { InternationalisationService } from "@/services/InternationalisationService";

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
  mergeInternationalization(application) {
    var internationalization = application?.configuration?.internationalization;
    if (!internationalization) {
      application.localName = application.name;
      return application;
    }
    application.localName = InternationalisationService.INSTANCE.localeApplicationName(
      internationalization?.application?.internationalization,
      application.name
    );
    return application;
  }

  async getApplications() {
    var applications = await this.get("applications/");
    return applications.map((a) => {
      return this.mergeInternationalization(a);
    });
  }

  async getApplication(name) {
    var application = await this.get("applications/" + name);
    return this.mergeInternationalization(application);
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
