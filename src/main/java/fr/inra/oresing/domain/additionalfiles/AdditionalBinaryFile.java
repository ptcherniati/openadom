package fr.inra.oresing.domain.additionalfiles;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.persistence.BinaryFileInfos;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)

public class AdditionalBinaryFile extends OreSiEntity {

    private UUID application;
    private String fileType;
    private String fileName;
    private String comment;
    private long size;
    private byte[] data;
    private List<OreSiAuthorization> associates;
    private UUID creationUser;
    private UUID updateUser;
    Map<String, String> fileInfos;
    boolean forApplication;
}