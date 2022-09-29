package fr.inra.oresing.model.additionalfiles;

import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiEntity;
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

    public final static BinaryFileInfos EMPTY_INSTANCE(){
        return BinaryFile.EMPTY_INSTANCE();
    }
    private UUID application;
    private String fileType;
    private String fileName;
    private String comment;
    private long size;
    private byte[] data;
    private List<OreSiAuthorization> associates;
    private UUID creationUser;
    private UUID updateUser;
    Map fileInfos;
}