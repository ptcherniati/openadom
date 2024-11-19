package fr.inra.oresing.rest.model.additionalfiles;

import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class AdditionalBinaryFileResult {
    UUID id;
    UUID application;
    UUID user;
    UUID updateUser;

    String additionalBinaryFileType;

    String comment;
    String fileName;
    String fileType;
    Long size;
    Map<String, String> additionalBinaryFileForm;
    Map<String, List<AuthorizationParsed>> associates;
    LocalDateTime updateDate;
    Boolean forApplication;

    public AdditionalBinaryFileResult(
            final AdditionalBinaryFile additionalBinaryFile,
            final Map<String, List<AuthorizationParsed>> associatesParsed
    ) {
        super();
        id = additionalBinaryFile.getId();
        application = additionalBinaryFile.getApplication();
        user = additionalBinaryFile.getCreationUser();
        comment = additionalBinaryFile.getComment();
        additionalBinaryFileForm = additionalBinaryFile.getFileInfos();
        associates = associatesParsed;
        additionalBinaryFileType = additionalBinaryFile.getFileType();
        fileName = additionalBinaryFile.getFileName();
        size = additionalBinaryFile.getSize();
        updateUser = additionalBinaryFile.getUpdateUser();
        fileType = additionalBinaryFile.getFileType();
        updateDate = additionalBinaryFile.getUpdateDate();
        forApplication = additionalBinaryFile.isForApplication();
    }
}