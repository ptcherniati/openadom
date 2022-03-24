import { Fetcher } from "../Fetcher";

export class SynthesisService extends Fetcher {
  static INSTANCE = new SynthesisService();

  constructor() {
    super();
  }

  async getSynthesis(applicationName, dataTypeId, variableName) {
    if (variableName) {
      return this.get(`applications/${applicationName}/synthesis/${dataTypeId}/${variableName}`);
    } else {
      return this.get(`applications/${applicationName}/synthesis/${dataTypeId}`);
    }
  }
}
