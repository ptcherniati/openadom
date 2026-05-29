package fr.inra.oresing.rest.binaryFile;

import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

/**
 * Service de telechargement du CSV normalise ( {@code binaryfile.processed_data} ,
 * Postgres Large Object ) .
 *
 * <p>SRP : ne fait que ( a ) ouvrir le Large Object en streaming via le
 * repository existant , ( b ) verifier la presence du contenu , ( c )
 * retourner la taille pour le header HTTP Content-Length .
 *
 * <p>DRY : reutilise {@link BinaryFileRepository#streamProcessedData} +
 * {@link BinaryFileRepository#findProcessedSize} - aucun nouveau SQL .
 *
 * <p>Transaction : declaree {@code @Transactional ( readOnly )} car la
 * Postgres Large Object API exige une connexion JDBC en transaction
 * ouverte pendant toute la duree du stream . Le stream est ferme par
 * le caller ( {@code FileResources} ) ; le wrapper {@code FilterInputStream}
 * retourne par le repository releve la connexion au close .
 */
@Slf4j
@Service
public class BinaryFileNormalizedDownloadService {

    private final OreSiRepository repository;
    private final AuthenticationService authenticationService;

    public BinaryFileNormalizedDownloadService(OreSiRepository repository,
                                               AuthenticationService authenticationService) {
        this.repository = repository;
        this.authenticationService = authenticationService;
    }

    /**
     * Resultat d'une demande de streaming de processed_data .
     *
     * @param stream         le contenu - jamais null
     * @param sizeBytes      taille connue pour Content-Length ( 0 si pas dispo )
     * @param hasContent     true si la colonne contient un blob non vide
     */
    public record StreamResult(InputStream stream, long sizeBytes, boolean hasContent) {}

    /**
     * Ouvre un stream sur processed_data du fichier . Pour un binaryfile
     * dont la colonne est NULL ou taille 0 , retourne un resultat
     * {@code hasContent=false} - le caller doit emettre 204 No Content .
     */
    @Transactional(readOnly = true)
    public StreamResult openNormalizedStream(String applicationNameOrId, UUID fileId) {
        authenticationService.setRoleForClient();
        BinaryFileRepository binaryFiles = repository.getRepository(applicationNameOrId).binaryFile();
        long size = binaryFiles.findProcessedSize(fileId);
        if (size <= 0) {
            return new StreamResult(InputStream.nullInputStream(), 0, false);
        }
        InputStream is = binaryFiles.streamProcessedData(fileId);
        return new StreamResult(is, size, true);
    }
}
