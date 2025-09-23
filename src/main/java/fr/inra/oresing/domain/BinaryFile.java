package fr.inra.oresing.domain;
import fr.inra.oresing.persistence.BinaryFileInfos;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.UUID;

@Getter
@Setter
@lombok.ToString(callSuper = true)
public class BinaryFile extends OreSiEntity {
    private UUID application;
    private String name;
    private String comment;
    private long size;
    private InputStream fileData;
    private BinaryFileInfos params;

    public void withBinaryFileDataset(BinaryFileDataset binaryfiledataset) {
        params = params.withBinaryFileDataset(binaryfiledataset);
    }

    public void markAsPublished(boolean published) {
        params = params.markAsPublished(published);
    }
}