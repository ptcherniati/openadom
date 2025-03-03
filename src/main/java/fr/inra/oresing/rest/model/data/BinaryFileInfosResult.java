package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.persistence.BinaryFileInfos;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

public record BinaryFileInfosResult(
        BinaryFileDatasetResult binaryFileDataset,
        String comment,
        String createdate,
        UserDescriptionResult createuser,
        boolean published,
        String publisheddate,
        UserDescriptionResult publisheduser
) {
    public static final BinaryFileInfosResult of(BinaryFileInfos binaryFileInfos, UserDescriptionResult createuser, UserDescriptionResult publisheduser){
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
