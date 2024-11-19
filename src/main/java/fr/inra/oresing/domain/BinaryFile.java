package fr.inra.oresing.domain;

import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.persistence.BinaryFileInfos;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class BinaryFile extends OreSiEntity {
    private UUID application;
    private String name;
    private String comment;
    private long size;
    private byte[] fileData;
    private BinaryFileInfos params;

    public void withBinaryFileDataset(BinaryFileDataset binaryfiledataset) {
        params = params.withBinaryFileDataset(binaryfiledataset);
    }

    public void markAsPublished(boolean published) {
        params= params.markAsPublished(published);
    }
}