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

    async validateConfiguration(applicationConfig) {
        return this.post("validate-configuration", {
            file: applicationConfig.file,
        });
    }

    async getValidateConfiguration() {
        return this.post("validate-configuration");
    }
}