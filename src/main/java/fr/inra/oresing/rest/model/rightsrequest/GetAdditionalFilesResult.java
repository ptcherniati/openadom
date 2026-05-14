package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.domain.application.configuration.AdditionalFileDescription;
import fr.inra.oresing.domain.authorization.GetGrantableResult;

import java.util.List;
import java.util.SortedSet;


public record GetAdditionalFilesResult(SortedSet<GetGrantableResult.User> users,
                                       String additionalFileName,
                                       List<AdditionalBinaryFileResult> additionalBinaryFiles,
                                       AdditionalFileDescription description, List<String> fileNames) {
}