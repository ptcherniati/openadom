package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.ReferencedBinaryFiles;

import java.util.List;
import java.util.UUID;

/**
 * Result DTO for the binary file listing endpoint .
 *
 * <p>{@code hasLinks} ( ajoute par l'optimisation B / scaling 900M ) :
 * boolean leger qui indique uniquement si ce fichier est reference par
 * d'autres fichiers via {@code reference_reference} . Suffit pour le
 * gating UI ( bouton publish/depublie + delete desactives quand = true )
 * sans avoir a enumerer le graphe complet des liaisons cote backend
 * ( query SQL O ( 870 K rows par fichier ) reduite a un EXISTS qui
 * s'arrete au premier match ) .
 *
 * <p>{@code referencedFiles} reste present pour la compatibilite ascendante
 * et les futurs use cases qui auraient besoin du graphe complet
 * ( aucun aujourd'hui apres adoption de hasLinks ) .
 */
public record BinaryFileResult(
        UUID id,
        String name,
        String comment,
        long size,
        boolean hasLinks,
        List<ReferencedBinaryFiles> referencedFiles,
        BinaryFileInfosResult params) {

    public static BinaryFileResult of(
            BinaryFile binaryFile,
            UserDescriptionResult createuser,
            UserDescriptionResult publisheduser,
            boolean hasLinks,
            List<ReferencedBinaryFiles> referencedFiles) {
        BinaryFileInfosResult params = binaryFile.getParams() == null
                ? null
                : BinaryFileInfosResult.of(binaryFile.getParams(), createuser, publisheduser);
        return new BinaryFileResult(
                binaryFile.getId(),
                binaryFile.getName(),
                binaryFile.getComment(),
                binaryFile.getSize(),
                hasLinks,
                referencedFiles,
                params
        );
    }

    /**
     * Backward-compatible factory ( legacy callers ) : derive hasLinks de
     * la presence d'au moins une entree dans referencedFiles avec un
     * tableau non vide .
     */
    public static BinaryFileResult of(
            BinaryFile binaryFile,
            UserDescriptionResult createuser,
            UserDescriptionResult publisheduser,
            List<ReferencedBinaryFiles> referencedFiles) {
        boolean hasLinks = referencedFiles != null && referencedFiles.stream()
                .filter(r -> r.referencedBinaryFileIdsByReferencetype() != null)
                .flatMap(r -> r.referencedBinaryFileIdsByReferencetype().values().stream())
                .anyMatch(arr -> arr != null && !arr.isEmpty());
        return of(binaryFile, createuser, publisheduser, hasLinks, referencedFiles);
    }
}