package fr.inra.oresing.domain.repository.file;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BinaryFileRepository {

    Optional<BinaryFile> findPublishedVersions(BinaryFileDataset binaryFileDataset);

    Optional<BinaryFile> tryFindByIdWithData(UUID uuid);

    UUID store(BinaryFile binaryFile);

    Optional<BinaryFile> tryFindById(UUID id);

    List<BinaryFile> findByBinaryFileDataset(String datatype, BinaryFileDataset fileDatasetID, boolean overlap);

    boolean delete(UUID id);
}
