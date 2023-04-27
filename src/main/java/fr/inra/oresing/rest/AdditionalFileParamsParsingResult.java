package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.persistence.AdditionalFileSearchHelper;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Value
public class AdditionalFileParamsParsingResult {

    @Nullable
    AdditionalFileSearchHelper result;
    private final List<ValidationCheckResult> validationCheckResults;

    public AdditionalFileParamsParsingResult(List<ValidationCheckResult> validationCheckResults, Application application, AdditionalFilesInfos additionalFilesInfos) {
        this.validationCheckResults = validationCheckResults;
        this.result = new AdditionalFileSearchHelper(application, additionalFilesInfos);
    }

    public static Builder builder() {
        return new Builder();
    }

    /*public static Builder builder(AdditionalFileSearchHelper) {
        return new Builder();
    }*/

    @Nullable
    public AdditionalFileSearchHelper getResult() {
        return result;
    }

    public boolean isValid() {
        return validationCheckResults.isEmpty();
    }

    public static class Builder {

        private final List<ValidationCheckResult> validationCheckResults = new LinkedList<>();

        private Builder recordError(String message, ImmutableMap<String, Object> params) {
            validationCheckResults.add(DefaultValidationCheckResult.error(message, params));
            return this;
        }
        //Set<String> availableFileNames = application.getConfiguration().getAdditionalFiles().keySet();

        public void unknownAdditionalFilename(String fileName, Set<String> availableFileNames) {
            recordError("unknownAdditionalFileNameInAdditionalFileError",
                    ImmutableMap.of("fileName", fileName, "availableFileNames", availableFileNames));
        }

        public void unknownFieldAdditionalFilename(String fileName, String fieldName, Set<String> availableFields) {
            recordError("unknownFieldForAdditionalFileNameInAdditionalFileError",
                    ImmutableMap.of(
                            "fileName", fileName,
                            "fieldName", fieldName,
                            "availableFileNames", availableFields
                    ));
        }

        public AdditionalFileParamsParsingResult build(Application application, AdditionalFilesInfos additionalFilesInfos) {
            return new AdditionalFileParamsParsingResult(validationCheckResults, application, additionalFilesInfos);
        }
    }
}