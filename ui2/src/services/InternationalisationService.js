import {Fetcher, LOCAL_STORAGE_LANG} from "./Fetcher";

export class InternationalisationService extends Fetcher {
  static INSTANCE = new InternationalisationService();

  localeApplicationName(applicationInternationalization, defautName) {
    return (applicationInternationalization?.[localStorage.getItem(LOCAL_STORAGE_LANG)]) ?? defautName ;
  }
  localeDatatypeName(datatype) {
    if (datatype.internationalizationName != null) {
      return datatype.internationalizationName[localStorage.getItem(LOCAL_STORAGE_LANG)];
    }else {
      return datatype.name;
    }
  }
  localeReferenceName(reference) {
    console.log(localStorage.getItem(LOCAL_STORAGE_LANG))
    if(reference.internationalizationName != null)
      return reference.internationalizationName[localStorage.getItem(LOCAL_STORAGE_LANG)];
    else
      return reference.label;
  }


}