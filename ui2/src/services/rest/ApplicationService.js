import { Fetcher } from "../Fetcher";

export class ApplicationService extends Fetcher {
  static INSTANCE = new ApplicationService();

  constructor() {
    super();
  }

  createApplication(applicationConfig) {
    return this.post("applications/" + applicationConfig.name, {
      file: applicationConfig.file,
    });
  }
}
