package fr.inra.oresing.rest.model.additionalfiles.exception;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.persistence.AdditionalFileSearchHelper;
import jakarta.annotation.Nullable;
import lombok.Value;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Value
public class AdditionalFileParamsParsingResult {

    @Nullable
    AdditionalFileSearchHelper result;
    List<ValidationCheckResult> validationCheckResults;

    public AdditionalFileParamsParsingResult(final List<ValidationCheckResult> validationCheckResults, final Application application, final AdditionalFilesInfos additionalFilesInfos) {
        super();
        this.validationCheckResults = validationCheckResults;
        result = new AdditionalFileSearchHelper(application, additionalFilesInfos);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public AdditionalFileSearchHelper getResult() {
        return result;
    }

    public boolean isValid() {
        return validationCheckResults.isEmpty();
    }

    public static class Builder {

        private final List<ValidationCheckResult> validationCheckResults = new LinkedList<>();

        private Builder recordError(final String message, final ImmutableMap<String, Object> params) {
            validationCheckResults.add(DefaultValidationCheckResult.error(message, params, null));
            return this;
        }

        public void unknownAdditionalFilename(final String fileName, final Set<String> availableFileNames) {
            recordError("unknownAdditionalFileNameInAdditionalFileError",
                    ImmutableMap.of("fileName", fileName, "availableFileNames", availableFileNames));
        }

        public void unknownFieldAdditionalFilename(final String fileName, final String fieldName, final Set<String> availableFields) {
            recordError("unknownFieldForAdditionalFileNameInAdditionalFileError",
                    ImmutableMap.of(
                            "fileName", fileName,
                            "fieldName", fieldName,
                            "availableFileNames", availableFields
                    ));
        }

        public AdditionalFileParamsParsingResult build(final Application application, final AdditionalFilesInfos additionalFilesInfos) {
            return new AdditionalFileParamsParsingResult(validationCheckResults, application, additionalFilesInfos);
        }
    }
}