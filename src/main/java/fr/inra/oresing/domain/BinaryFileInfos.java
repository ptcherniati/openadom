package fr.inra.oresing.domain;


import fr.inra.oresing.domain.BinaryFileDataset;

import java.util.Optional;
import java.util.UUID;

public record BinaryFileInfos(
        boolean published,
        UUID publisheduser,
        String publisheddate,
        UUID createuser,
        String createdate,
        String comment,
        BinaryFileDataset binaryFiledataset,
        String configHash) {
    /**
     * Hash de configuration ( SHA-256 hex ) du {@code StandardDataDescription}
     * applicable au datatype au moment du 1er upload . Utilise par
     * {@code PublishLifecyclePhase2Handler} pour decider si un republish
     * peut emprunter le chemin lite ( bypass {@code DataImporter} cumulatif )
     * ou doit re-valider via le chemin FULL ( config evoluee ) .
     *
     * <p>Optionnel ( {@code null} sur fichiers historiques ante-feature ) :
     * un {@code null} force le chemin FULL ( decision conservative ) .
     */
    public BinaryFileInfos(boolean published, UUID publisheduser, String publisheddate, UUID createuser, String createdate, String comment, BinaryFileDataset binaryFiledataset) {
        this(published, publisheduser, publisheddate, createuser, createdate, comment, binaryFiledataset, null);
    }

    public BinaryFileInfos(BinaryFileDataset binaryFileDataset) {
        this(false, null, null, null, null, null, binaryFileDataset, null);
    }

    public static BinaryFileInfos forPublish(boolean published, UUID publisheduser, String publisheddate, BinaryFileDataset binaryFileDataset) {
        return new BinaryFileInfos(
                published,
                publisheduser,
                publisheddate,
                null,
                null,
                Optional.ofNullable(binaryFileDataset).map(BinaryFileDataset::getComment).orElse(""),
                binaryFileDataset,
                null
        );
    }

    public BinaryFileInfos markAsPublished(boolean published) {
        return new BinaryFileInfos(
                published,
                published ? publisheduser() : null,
                published ? publisheddate() : null,
                createuser(),
                createdate(),
                comment(),
                binaryFiledataset(),
                configHash()
        );
    }

    public BinaryFileInfos withBinaryFileDataset(BinaryFileDataset binaryFiledataset) {
        return new BinaryFileInfos(
                published(),
                publisheduser(),
                publisheddate(),
                createuser(),
                createdate(),
                comment(),
                binaryFiledataset,
                configHash()
        );
    }

    /**
     * @return nouvelle instance avec {@code configHash} mis a jour ;
     *         tous les autres champs preserves a l'identique .
     */
    public BinaryFileInfos withConfigHash(String configHash) {
        return new BinaryFileInfos(
                published(),
                publisheduser(),
                publisheddate(),
                createuser(),
                createdate(),
                comment(),
                binaryFiledataset(),
                configHash
        );
    }
}