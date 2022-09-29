import {Fetcher} from "../Fetcher";

export class AdditionalFileService extends Fetcher {
    static INSTANCE = new AdditionalFileService();

    constructor() {
        super();
    }

    async saveAdditionalFile(
        id,
        fileType,
        applicationName,
        additionalFileName,
        file,
        fields,
        associates) {
        /* associates = Object.keys(associates)
             .reduce((acc, dataType)=>{
           acc[dataType] = associates[dataType].scopes.associate;
           return acc;
         }, {});*/
        return this.post(`applications/${applicationName}/additionalFiles/${additionalFileName}`, {
            file,
            params: JSON.stringify({
                id,
                fileType,
                fields,
                associates
            })
        });
    }

    async getAdditionalFilesWithGrantable(applicationName) {
        return this.get(`applications/${applicationName}/additionalFiles`);
    }

    async getAdditionalFiles(applicationName, additionalFileName, params) {
        return this.get(`applications/${applicationName}/additionalFiles/${additionalFileName}`, {
            downloadDatasetQuery: JSON.stringify(params),
        });
    }

    async addAdditionalFile(applicationName, additionalFileName, additionalFile, params) {
        return this.post(`applications/${applicationName}/additionalFiles/${additionalFileName}`, {
            file: additionalFile,
            params: JSON.stringify(params),
        });
    }
}