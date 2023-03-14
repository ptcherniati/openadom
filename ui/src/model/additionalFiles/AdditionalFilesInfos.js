
import {Authorization} from "@/model/authorization/Authorization";
import {AdditionalFileInfos} from "@/model/additionalFiles/AdditionalFileInfos";

const browserLocale = window.navigator.language.substring(0, 2);
export class AdditionalFilesInfos{
    uuids;
    fileNames;
    authorizations;
    additionalFilesInfos;
    locale = browserLocale;
    offset = 0;
    limit;
    constructor(
    uuids,
    fileNames,
    authorizations,
    additionalFilesInfos,
    locale,
    offset,
    limit) {
        this.uuids = uuids;
        this.fileNames = fileNames;
        if(authorizations) {
            this.authorizations = authorizations.map(authorization => new Authorization(authorization));
        }
        if (additionalFilesInfos){
            this.additionalFilesInfos = Object.entries(additionalFilesInfos)
                .reduce((acc,entry)=>{
                    let key = entry[0]
                    let value = entry[1]
                    acc[key] = new AdditionalFileInfos(
                        value?value.fieldFilters:null
                    )
                    return acc

                },{})
        }
        this.locale = locale;
        this.offset = offset;
        this.limit = limit;
    }
}