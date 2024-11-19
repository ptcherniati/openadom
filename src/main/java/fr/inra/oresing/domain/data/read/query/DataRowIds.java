package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.util.UUID;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_ID_FOR_UUID;

public record DataRowIds(UUID id) {
    public DataRowIds {
        if (id == null) {
            throw new BadDownloadDatasetQuery(MISSING_ID_FOR_UUID);
        }
    }
}
