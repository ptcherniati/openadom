package fr.inra.oresing.domain;
import fr.inra.oresing.domain.BinaryFileInfos;
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
     * Publish FAST path : cache CSV processed ( JSON lines DataValue
     * serialisees ) capture au 1er upload pour activer le FAST path au
     * republish ( bypass DataImporter ) . Stocke en colonne bytea separee
     * pour ne pas alourdir les requetes courantes ( fileData / params ) .
     * {@code null} pour fichiers pre-feature ou si capture desactivee .
     */
    private InputStream processedData;
    private Long processedSize;
    private java.time.LocalDateTime processedAt;

    public void withBinaryFileDataset(BinaryFileDataset binaryfiledataset) {
        params = params.withBinaryFileDataset(binaryfiledataset);
    }

    public void markAsPublished(boolean published) {
        params = params.markAsPublished(published);
    }
}