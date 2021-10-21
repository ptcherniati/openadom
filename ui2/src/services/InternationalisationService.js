import {Fetcher, LOCAL_STORAGE_LANG} from "./Fetcher";

export class InternationalisationService extends Fetcher {
  static INSTANCE = new InternationalisationService();

  mergeInternationalization(application){
    var internationalization;

    if(application?.configuration?.internationalization)
      internationalization = application?.configuration?.internationalization;
    else if (application?.internationalization)
      internationalization = application?.internationalization;

    if (!internationalization){
      application.localName = application.name;
      return application;
    }
    application.localName = this.localeApplicationName(internationalization?.application?.internationalization, application.name);
    return application;
  }

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
  localeReferenceName(references, applications) {
    if(applications.internationalization) {
      let applicationReferences = applications.internationalization.references;
      if(references.label) {
        for (let applicationReference in applicationReferences) {
          if( applicationReference === references.label ) {
            return (applicationReferences[applicationReference].internationalizationName?.[localStorage.getItem(LOCAL_STORAGE_LANG)]) ?? references.label;
          }
        }
        return references.label;
      }
    }
    return references;
  }
  treeReferenceName(refs) {
    if(refs.internationalization) {
      let applicationReferences = refs.internationalization.references;
      for (let applicationReference in applicationReferences) {
        refs.references[applicationReference] = {
          ...refs.references[applicationReference],
          refNameLocal: applicationReferences[applicationReference].internationalizationName?.[localStorage.getItem(LOCAL_STORAGE_LANG)]
        };
      }
    }
    return  refs.references;
  }
}