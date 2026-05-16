package fr.inra.oresing.rest.model.data;

import java.util.UUID;

/**
 * DTO leger - etat de publication courant d'un binaryfile , lu directement
 * depuis {@code binaryfile.params} en DB . Sert de source de verite cote
 * frontend post-terminal d'un workflow publish/unpublish/cancel : plutot
 * que d'inferer le statut a partir de {@code workflow_log.status} ( qui
 * peut diverger en cas de race condition cancel/sweeper ) , le frontend
 * requete cet endpoint pour avoir l'etat reel committe en BDD .
 *
 * <p>Volontairement minimal ( 4 champs ) - pas de createUser , pas de
 * referencedFiles , pas de BinaryFileDataset . Pour les details complets
 * cf {@code FileResources.getFileInfo} ( /file/{id}/info ) qui sert
 * d'autres flux UI .
 *
 * @param fileId           identifiant binaryfile
 * @param published        flag courant ( true = visible , false = cache )
 * @param publishedOn      date ISO de derniere publication ( null si jamais publie )
 * @param publishedByLogin login utilisateur ayant publie ( null si publisheduser
 *                         absent ou utilisateur supprime entre temps )
 */
public record BinaryFilePublicationState(
        UUID    fileId,
        boolean published,
        String  publishedOn,
        String  publishedByLogin
) {
}
