package fr.inra.oresing.rest.data.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class AuthorizationPublicationServiceBuilder {

    public static AuthorizationForUserBuilder BUILDER(ReportErrors errors,
                                                      final Application application,
                                                      final String dataName,
                                                      String fileName,
                                                      String params,
                                                      Function<Map<String, List<Ltree>>, Map<String, List<Ltree>>> requiredAuthorizationResolver,
                                                      Function<UUID, Optional<BinaryFile>> resolveFileById) {
        AuthorizationPublicationService builder = new AuthorizationPublicationService(
                errors,
                application,
                dataName,
                deserialiseFileOrUUIDQuery(dataName, params, resolveFileById));
        boolean hasSubmissionScope = application.findData(dataName)
                                             .map(StandardDataDescription::submission)
                                             .map(Submission::submissionScope)
                                             .map(Submission.SubmissionScope::referenceScopes)
                                             .map(List::size)
                                             .orElse(-1) > 0;
        boolean hasNoFileId = Optional.of(builder)
                .map(AuthorizationPublicationService::getParams)
                .map(fileOrUUID -> true)
                .isEmpty();
        if (hasNoFileId && hasSubmissionScope) {
            return new FileNameResolver(builder)
                    .resolveFileName(fileName)
                    .resolveParams(requiredAuthorizationResolver);
        }
        return new AuthorizationForUserBuilder(builder);
    }

    private static FileOrUUID deserialiseFileOrUUIDQuery(final String datatype, final String params, Function<UUID, Optional<BinaryFile>> resolveFileById) {
        if (Strings.isNullOrEmpty(params) || "undefined".equals(params)) {
            return null;
        }

        try {
            final FileOrUUID fileOrUUID = new ObjectMapper().readValue(params, FileOrUUID.class);
            Optional.ofNullable(fileOrUUID)
                    .map(FileOrUUID::binaryfiledataset)
                    .ifPresent(binaryFileDataset -> {
                        String resolvedDatatype = Optional.of(binaryFileDataset)
                                .map(BinaryFileDataset::getDatatype)
                                .filter(Predicate.not(String::isBlank))
                                .orElse(datatype);
                        binaryFileDataset.setDatatype(resolvedDatatype);
                    });
            boolean isNotDefinedDatatype = Optional.ofNullable(fileOrUUID)
                    .map(FileOrUUID::binaryfiledataset)
                    .map(BinaryFileDataset::getDatatype).isEmpty();
            if(isNotDefinedDatatype) {
                Optional<UUID> uuid = Optional.ofNullable(fileOrUUID)
                        .map(FileOrUUID::fileid);
                if(uuid.isEmpty()) {
                    return fileOrUUID;
                }
                return uuid
                        .map(resolveFileById)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(BinaryFile::getParams)
                        .map(fileOrUUID::withParams)
                        .orElseThrow(IllegalArgumentException::new);
            }
            return fileOrUUID;
        } catch (final IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }
    }
}
