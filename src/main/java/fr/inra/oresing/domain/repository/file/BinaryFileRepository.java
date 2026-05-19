package fr.inra.oresing.domain.repository.file;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.ReferencedBinaryFiles;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BinaryFileRepository {
    List<ReferencedBinaryFiles> getReferencedBinaryFiles(String referenceType, Set<UUID> binaryfileIds);

    /**
     * Lightweight version : retourne uniquement les binaryfile ids ayant
     * au moins une liaison sortante via {@code reference_reference} . Ne
     * remonte aucun graphe ; la query SQL s'arrete au premier match
     * grace a {@code EXISTS} . Utilise pour le gating UI ( bouton publish
     * + delete ) qui n'a besoin que d'un boolean par fichier .
     */
    Set<UUID> findBinaryFileIdsWithLinks(String referenceType, Set<UUID> binaryfileIds);

    Optional<BinaryFile> findPublishedVersions(BinaryFileDataset binaryFileDataset);

    Optional<BinaryFile> tryFindByIdWithData(UUID uuid);

    UUID store(BinaryFile binaryFile);

    Optional<BinaryFile> tryFindById(UUID id);

    List<BinaryFile> findByBinaryFileDataset(String datatype, BinaryFileDataset fileDatasetID, boolean overlap);

    boolean delete(UUID id);

    void storeFileContent(UUID fileId, InputStream inputStream, long fileSize);
}