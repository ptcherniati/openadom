import { Fetcher, LOCAL_STORAGE_LANG } from "./Fetcher";

export class InternationalisationService extends Fetcher {
  static INSTANCE = new InternationalisationService();

  localeApplicationName(application) {
    if (application.configuration != null) {
      if (application.configuration.application.internationalization != null) {
        return application.configuration.application.internationalization[
          localStorage.getItem(LOCAL_STORAGE_LANG)
        ];
      } else {
        return application.name;
      }
    } else if (application.internationalization != null) {
      return application.internationalization[localStorage.getItem(LOCAL_STORAGE_LANG)];
    } else {
      return application.name;
    }
  }
  localeDatatypeName(datatype) {
    console.log(datatype);
    if (datatype.internationalizationName != null) {
      return datatype.internationalizationName[localStorage.getItem(LOCAL_STORAGE_LANG)];
    } else {
      return datatype.name;
    }
  }
  localeReferenceName(reference) {
    if (reference.internationalizationName != null)
      return reference.internationalizationName[localStorage.getItem(LOCAL_STORAGE_LANG)];
    else return reference.label;
  }
}
