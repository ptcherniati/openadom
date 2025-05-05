package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.UUID;

public record BinaryFileResult(
        UUID id,
        String name,
        String comment,
        long size,
        BinaryFileInfosResult params) {

    public static BinaryFileResult of(BinaryFile binaryFile, UserDescriptionResult createuser, UserDescriptionResult publisheduser){
        return new BinaryFileResult(
                binaryFile.getId(),
                binaryFile.getName(),
                binaryFile.getComment(),
                binaryFile.getSize(),
                BinaryFileInfosResult.of(binaryFile.getParams(), createuser, publisheduser)
        );
    }
}
