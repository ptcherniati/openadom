package fr.inra.oresing.domain.additionalfiles;

import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiEntity;
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
    private Map<String, String> fileInfos;
    private boolean forApplication;
}