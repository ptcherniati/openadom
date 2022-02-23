package fr.inra.oresing.model;

import fr.inra.oresing.persistence.BinaryFileInfos;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class BinaryFile extends OreSiEntity {
    public final static BinaryFileInfos EMPTY_INSTANCE(){
        return new BinaryFileInfos();
    }
    private UUID application;
    private String name;
    private String comment;
    private long size;
    private byte[] data;
    private BinaryFileInfos params;
}