package fr.inra.oresing.rest;

import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.Date;
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
    Map<String, Map<OperationType, List<AuthorizationParsed>>> associates;
    Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> associatesByDatatypeAndPath;
    private final Date updateDate;
    private Boolean forApplication;

    public AdditionalBinaryFileResult(AdditionalBinaryFile additionalBinaryFile, Map<String, Map<OperationType, List<AuthorizationParsed>>> associatesParsed, Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> associatesByDatatypeAndPath) {
        this.id = additionalBinaryFile.getId();
        this.application = additionalBinaryFile.getApplication();
        this.user = additionalBinaryFile.getCreationUser();
        this.comment = additionalBinaryFile.getComment();
        this.additionalBinaryFileForm = additionalBinaryFile.getFileInfos();
        this.associates = associatesParsed;
        this.associatesByDatatypeAndPath = associatesByDatatypeAndPath;
        this.additionalBinaryFileType = additionalBinaryFile.getFileType();
        this.fileName = additionalBinaryFile.getFileName();
        this.size = additionalBinaryFile.getSize();
        this.updateUser = additionalBinaryFile.getUpdateUser();
        this.fileType = additionalBinaryFile.getFileType();
        this.updateDate = additionalBinaryFile.getUpdateDate();
        this.forApplication = additionalBinaryFile.isForApplication();
    }
}