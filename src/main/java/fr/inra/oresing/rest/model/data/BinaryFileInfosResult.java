package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFileInfos;

public record BinaryFileInfosResult(
        BinaryFileDatasetResult binaryFileDataset,
        String comment,
        String createdate,
        UserDescriptionResult createuser,
        boolean published,
        String publisheddate,
        UserDescriptionResult publisheduser
) {
    /**
     * Retourne un objet vide (published=false, tous les champs null) quand
     * {@code binaryFileInfos} est null, afin d'éviter tout NPE côté client
     * (frontend JS lirait {@code params.published} sans vérification préalable).
     */
    public static BinaryFileInfosResult of(BinaryFileInfos binaryFileInfos, UserDescriptionResult createuser, UserDescriptionResult publisheduser) {
        if (binaryFileInfos == null) {
            return new BinaryFileInfosResult(null, null, null, null, false, null, null);
        }
        return new BinaryFileInfosResult(
                BinaryFileDatasetResult.of(binaryFileInfos.binaryFiledataset()),
                binaryFileInfos.comment(),
                binaryFileInfos.createdate(),
                createuser,
                binaryFileInfos.published(),
                binaryFileInfos.publisheddate(),
                publisheduser
        );
    }
}