package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.persistence.BinaryFileInfos;

public record BinaryFileInfosResult(
        BinaryFileDatasetResult binaryFileDataset,
        String comment,
        String createdate,
        UserDescriptionResult createuser,
        boolean published,
        String publisheddate,
        UserDescriptionResult publisheduser
) {
    public static BinaryFileInfosResult of(BinaryFileInfos binaryFileInfos, UserDescriptionResult createuser, UserDescriptionResult publisheduser){
        return new BinaryFileInfosResult(
                BinaryFileDatasetResult.of(binaryFileInfos.binaryFiledataset()),
                binaryFileInfos.comment(),
                binaryFileInfos.createdate(),
                createuser,
                binaryFileInfos.published(),
                binaryFileInfos.publisheddate(),
                publisheduser
        );
    }
}