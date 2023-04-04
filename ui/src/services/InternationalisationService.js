import { Fetcher, LOCAL_STORAGE_LANG } from "./Fetcher";

export class InternationalisationService extends Fetcher {
  static INSTANCE = new InternationalisationService();

  getApplicationPath() {
    return `application.internationalization`;
  }
  getDataTypeDisplay(reference, localThis) {
    localThis = localThis || this;
    return `dataTypes.${localThis.dataTypeId}.internationalizationDisplay.${reference}.pattern`;
  }
  getDataTypeName(localThis) {
    localThis = localThis || this;
    return `dataTypes.${localThis.dataTypeId}.internationalizationName`;
  }
  // getDataTypeColumns(column) {
  //     return `dataTypes.${this.dataTypeId}.internationalizationColumns.${column}.pattern`;
  // }
  getReferenceDisplay(localThis) {
    localThis = localThis || this;
    return `references.${localThis.refId}.internationalizationDisplay.pattern`;
  }
  getReferenceName(localThis) {
    localThis = localThis || this;
    return `references.${localThis.refId}.internationalizationName`;
  }
  getReferenceeColumns(column, localThis) {
    localThis = localThis || this;
    return `references.${localThis.refId}.internationalizationColumns.${column}`;
  }
  getAuthorizationScopePath(authorizationScopes, localThis) {
    localThis = localThis || this;
    return `dataTypes.${localThis.dataTypeId}.authorization.authorizationScopes.${authorizationScopes}.internationalizationName`;
  }
  getDataGroupsPath(dataGroups, localThis) {
    localThis = localThis || this;
    return `dataTypes.${localThis.dataTypeId}.authorization.dataGroups.${dataGroups}.internationalizationName`;
  }

  getLocaleforPath(application, path, defaultValue) {
    if (!path || !path.length) {
      return defaultValue;
    }
    if (application.internationalization) {
      var navigateConfiguration = application.internationalization;
      let pathArray = path.split(".");
      var pathItem = pathArray.shift();
      while (pathItem) {
        navigateConfiguration = navigateConfiguration[pathItem];
        if (!navigateConfiguration) {
          return defaultValue;
        }
        pathItem = pathArray.shift();
      }
      return navigateConfiguration[localStorage.getItem(LOCAL_STORAGE_LANG)] || defaultValue;
    }
    return defaultValue;
  }

  mergeInternationalization(application) {
    var internationalization;

    if (application?.configuration?.internationalization)
      internationalization = application?.configuration?.internationalization;
    else if (application?.internationalization)
      internationalization = application?.internationalization;

    if (!internationalization) {
      application.localName = application.name;
      return application;
    }
    application.localName = this.localeApplicationName(
      internationalization?.application?.internationalizationName,
      application.name
    );
    return application;
  }

  localeApplicationName(applicationInternationalization, defautName) {
    return (
      applicationInternationalization?.[localStorage.getItem(LOCAL_STORAGE_LANG)] ?? defautName
    );
  }

  localeDataTypeIdName(application, datatype) {
    if (
      application?.internationalization?.dataTypes?.[datatype.id]?.internationalizationName != null
    ) {
      return application.internationalization.dataTypes[datatype.id].internationalizationName[
        localStorage.getItem(LOCAL_STORAGE_LANG)
      ];
    } else {
      return datatype.name;
    }
  }

  localeAdditionalFilesName(application) {
    if (application.internationalization != null) {
      let additionalFiles = application.internationalization.additionalFiles;
      for (let additionalFilesKey in additionalFiles) {
        localStorage.getItem(LOCAL_STORAGE_LANG);
        application.additionalFiles[additionalFilesKey] = {
          ...application.additionalFiles[additionalFilesKey],
          localName:
            additionalFiles[additionalFilesKey].internationalizationName?.[
              localStorage.getItem(LOCAL_STORAGE_LANG)
            ] || additionalFilesKey,
        };
      }
    } else {
      let additionalFiles = application.additionalFiles;
      for (let additionalFilesKey in additionalFiles) {
        application.additionalFiles[additionalFilesKey] = {
          ...application.additionalFiles[additionalFilesKey],
          localName: application.additionalFiles[additionalFilesKey].name,
        };
      }
    }
    return application.additionalFiles;
  }

  localeDatatypeName(application) {
    if (application.internationalization != null) {
      let applicationDataTypes = application.internationalization.dataTypes;
      for (let applicationDataType in applicationDataTypes) {
        localStorage.getItem(LOCAL_STORAGE_LANG);
        application.dataTypes[applicationDataType] = {
          ...application.dataTypes[applicationDataType],
          localName:
            applicationDataTypes[applicationDataType].internationalizationName?.[
              localStorage.getItem(LOCAL_STORAGE_LANG)
            ] || applicationDataType,
        };
      }
    } else {
      let applicationDataTypes = application.dataTypes;
      for (let applicationDataType in applicationDataTypes) {
        application.dataTypes[applicationDataType] = {
          ...application.dataTypes[applicationDataType],
          localName: application.dataTypes[applicationDataType].name,
        };
      }
    }
    return application.dataTypes;
  }

  localeReferenceName(references, applications) {
    if (applications.internationalization) {
      let applicationReferences = applications.internationalization.references;
      if (references.label) {
        for (let applicationReference in applicationReferences) {
          if (applicationReference === references.label) {
            return (
              applicationReferences[applicationReference].internationalizationName?.[
                localStorage.getItem(LOCAL_STORAGE_LANG)
              ] ?? references.label
            );
          }
        }
        return references.label;
      }
    }
    return references;
  }
  additionalFilesNames(refs) {
    if (refs.internationalization) {
      let additionalFiles = refs.internationalization.additionalFiles;
      for (let additionalFilesKey in additionalFiles) {
        let format = additionalFiles[additionalFilesKey].format;
        let localFields = {};
        for (const formatKey in format) {
          localFields[formatKey] = format[formatKey]?.[localStorage.getItem(LOCAL_STORAGE_LANG)];
        }
        refs.additionalFiles[additionalFilesKey] = {
          ...refs.additionalFiles[additionalFilesKey],
          localFields,
          refNameLocal:
            additionalFiles[additionalFilesKey]?.[localStorage.getItem(LOCAL_STORAGE_LANG)],
          name: additionalFilesKey,
        };
      }
    } else {
      let additionalFiles = refs.additionalFiles;
      for (let additionalFilesKey in additionalFiles) {
        refs.additionalFiles[additionalFilesKey] = {
          ...refs.additionalFiles[additionalFilesKey],
          refNameLocal: refs.additionalFiles[additionalFilesKey].name,
          name: refs.additionalFiles[additionalFilesKey].name,
        };
      }
    }
    return refs.additionalFiles;
  }

  treeReferenceName(refs) {
    if (refs.internationalization) {
      let applicationReferences = refs.internationalization.references;
      for (let applicationReference in applicationReferences) {
        refs.references[applicationReference] = {
          ...refs.references[applicationReference],
          refNameLocal:
            applicationReferences[applicationReference].internationalizationName?.[
              localStorage.getItem(LOCAL_STORAGE_LANG)
            ],
        };
      }
    } else {
      let applicationReferences = refs.references;
      for (let applicationReference in applicationReferences) {
        refs.references[applicationReference] = {
          ...refs.references[applicationReference],
          refNameLocal: refs.references[applicationReference].name,
        };
      }
    }
    return refs.references;
  }
}
