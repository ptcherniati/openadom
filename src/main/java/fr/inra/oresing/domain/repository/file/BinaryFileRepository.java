package fr.inra.oresing.domain.repository.file;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.ReferencedBinaryFiles;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public interface BinaryFileRepository {
    List<ReferencedBinaryFiles> getReferencedBinaryFiles(String referenceType, Set<UUID> binaryfileIds);

    Optional<BinaryFile> findPublishedVersions(BinaryFileDataset binaryFileDataset);

    Optional<BinaryFile> tryFindByIdWithData(UUID uuid);

    UUID store(BinaryFile binaryFile);

    Optional<BinaryFile> tryFindById(UUID id);

    List<BinaryFile> findByBinaryFileDataset(String datatype, BinaryFileDataset fileDatasetID, boolean overlap);

    boolean delete(UUID id);

    void storeFileContent(UUID fileId, InputStream inputStream, long fileSize);
}