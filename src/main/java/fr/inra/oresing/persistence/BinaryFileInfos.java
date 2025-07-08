package fr.inra.oresing.persistence;


import fr.inra.oresing.domain.BinaryFileDataset;

import java.util.Optional;
import java.util.UUID;

public record BinaryFileInfos(
        boolean published,
        UUID publisheduser,
        String publisheddate,
        UUID createuser,
        String createdate,
        String comment,
        BinaryFileDataset binaryFiledataset) {
    public BinaryFileInfos(BinaryFileDataset binaryFileDataset) {
        this(false, null, null, null, null, null, binaryFileDataset);
    }

    public static BinaryFileInfos forPublish(boolean published, UUID publisheduser, String publisheddate, BinaryFileDataset binaryFileDataset) {
        return new BinaryFileInfos(
                published,
                publisheduser,
                publisheddate,
                null,
                null,
                Optional.ofNullable(binaryFileDataset).map(BinaryFileDataset::getComment).orElse(""),
                binaryFileDataset
        );
    }

    public BinaryFileInfos markAsPublished(boolean published) {
        return new BinaryFileInfos(
                published,
                published ? publisheduser() : null,
                published ? publisheddate() : null,
                createuser(),
                createdate(),
                comment(),
                binaryFiledataset()
        );
    }

    public BinaryFileInfos withBinaryFileDataset(BinaryFileDataset binaryfiledataset) {
        return new BinaryFileInfos(
                published(),
                publisheduser(),
                publisheddate(),
                createuser(),
                createdate(),
                comment(),
                binaryFiledataset
        );
    }
}