package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.ReferencedBinaryFiles;

import java.util.List;
import java.util.UUID;

public record BinaryFileResult(
        UUID id,
        String name,
        String comment,
        long size,
        List<ReferencedBinaryFiles> referencedFiles,
        BinaryFileInfosResult params) {

    public static BinaryFileResult of(
            BinaryFile binaryFile,
            UserDescriptionResult createuser,
            UserDescriptionResult publisheduser,
            List<ReferencedBinaryFiles> referencedFiles) {
        BinaryFileInfosResult params = binaryFile.getParams() == null
                ? null
                : BinaryFileInfosResult.of(binaryFile.getParams(), createuser, publisheduser);
        return new BinaryFileResult(
                binaryFile.getId(),
                binaryFile.getName(),
                binaryFile.getComment(),
                binaryFile.getSize(),
                referencedFiles,
                params
        );
    }
}