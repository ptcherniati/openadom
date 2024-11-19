package fr.inra.oresing.persistence;


import fr.inra.oresing.domain.BinaryFileDataset;

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

    public static final BinaryFileInfos forPublish(boolean published, UUID publisheduser, String publisheddate, BinaryFileDataset binaryFileDataset) {
        return new BinaryFileInfos(
                published,
                publisheduser,
                publisheddate,
                null,
                null,
                null,
                binaryFileDataset
        );
    }

    public static BinaryFileInfos EMPTY_INSTANCE() {
        return new BinaryFileInfos(false, null, null, null, null, null, BinaryFileDataset.EMPTY_INSTANCE());
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

    public BinaryFileInfos markAsPublished(boolean published) {
        return new BinaryFileInfos(
                published,
                published?publisheduser():null,
                published?publisheddate():null,
                createuser(),
                createdate(),
                comment(),
                binaryFiledataset()
        );
    }
}
