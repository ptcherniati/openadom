package fr.inra.oresing.domain.file;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.persistence.BinaryFileInfos;

import java.util.*;

public record FileOrUUID(UUID fileid, BinaryFileDataset binaryfiledataset, Boolean topublish) {


    public static FileOrUUID from(FileOrUUID params, UUID fileId) {
        BinaryFileDataset binaryFileDataset = new BinaryFileDataset();
        if (params != null) {
            binaryFileDataset = params.binaryfiledataset();
        }
        return new FileOrUUID(
                fileId,
                binaryFileDataset,
                params == null || params.topublish()
        );
    }

    public FileOrUUID withParams(BinaryFileInfos binaryFileInfos) {
        return new FileOrUUID(
                fileid(),
                binaryFileInfos.binaryFiledataset(),
                topublish()
        );

    }

    public boolean requiredAuthorizationMatchForFile(final Map<String, Set<String>>requiredAuthorizationInDataBase) {
        Optional<Map<String, List<Ltree>>> requiredAuthorizationForFile = Optional.ofNullable(binaryfiledataset())
                .map(BinaryFileDataset::getRequiredAuthorizations);
        if (requiredAuthorizationForFile.isPresent()) {
            for (final Map.Entry<String, List<Ltree>> requiredAuthorizationForFileEntry : requiredAuthorizationForFile.get().entrySet()) {
                final String scope = requiredAuthorizationForFileEntry.getKey();
                final String ltree = requiredAuthorizationForFileEntry.getValue().getFirst().getSql();
                if(requiredAuthorizationInDataBase.get(scope).stream()
                        .noneMatch(pathAuthorized -> ltree.equals(pathAuthorized) ||
                                    ltree.startsWith(pathAuthorized+Ltree.SEPARATOR))
                ){
                    return false;
                }
            }
        }
        return true;
    }
}
