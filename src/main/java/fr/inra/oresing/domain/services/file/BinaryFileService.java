package fr.inra.oresing.domain.services.file;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BinaryFileService {
    UUID storeFile(Application application, DataFile file, String comment, BinaryFileDataset binaryFileDataset) throws IOException;


    Optional<BinaryFile> getFile(String applicationNameOrID, UUID id);

    Optional<BinaryFile> getFileWithData(String applicationNameOrID, UUID id);

    Optional<UUID> removeFile(Application application, UUID id);

    ReportErrors findPublishedVersion(String nameOrId, String dataType, FileOrUUID params, Set<BinaryFile> filesToStore, boolean searchOverlaps);

    List<BinaryFile> getFilesOnRepository(
            String nameOrId,
            String datatype,
            BinaryFileDataset fileDatasetID,
            boolean overlap
    );

    AdditionalBinaryFileResult getAdditionalBinaryFileResult(
            AdditionalBinaryFile additionalBinaryFile);

    List<ReferencedBinaryFiles> getReferencedBinaryFiles(UUID applicationId, String datatype, Set<UUID> id);

    /**
     * Lightweight version : retourne uniquement le {@link Set} des
     * binaryfile ids ayant au moins une liaison sortante via
     * {@code reference_reference} . Utilise pour le gating UI ( bouton
     * publish/depublie + delete ) qui n'a besoin que d'un boolean par
     * fichier . Beaucoup plus rapide que
     * {@link #getReferencedBinaryFiles} sur gros volumes ( EXISTS s'arrete
     * au premier match au lieu d'enumerer tout le graphe ).
     */
    Set<UUID> findBinaryFileIdsWithLinks(UUID applicationId, String datatype, Set<UUID> ids);
}