package fr.inra.oresing.domain;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.UUID;

@Getter
@Setter
@lombok.ToString(callSuper = true)
public class BinaryFile extends OreSiEntity {
    private UUID application;
    private String name;
    private String comment;
    private long size;
    private InputStream fileData;
    private BinaryFileInfos params;
    /**
     * Publish FAST path : cache binaire de {@code referencevalue} ( COPY
     * BINARY ) capture en async APRES un publish ( cf {@code CacheCaptureService}
     * ) pour activer le FAST path au republish suivant ( bypass DataImporter ) .
     * Stocke en colonne {@code oid} ( Large Object ) separee pour ne pas alourdir
     * les requetes courantes ( fileData / params ) et supporter > 1 GB .
     * {@code null} si jamais publie , capture desactivee / echouee , ou cache
     * vide apres un republish FAST en mode CACHED_ROTATION .
     */
    private transient InputStream processedData;
    private Long processedSize;
    private java.time.LocalDateTime processedAt;

    public void withBinaryFileDataset(BinaryFileDataset binaryfiledataset) {
        params = params.withBinaryFileDataset(binaryfiledataset);
    }

    public void markAsPublished(boolean published) {
        params = params.markAsPublished(published);
    }
}