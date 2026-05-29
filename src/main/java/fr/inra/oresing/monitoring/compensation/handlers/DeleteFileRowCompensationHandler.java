package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationHandler;
import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Compensation handler pour {@code DELETE_FILE_ROW} ( audit P0-4 ) .
 *
 * <p>Contexte : un DELETE_FILE supprime d'abord les {@code referencevalue}
 * par paquets ( phase DELETE_ROWS , committee ) puis la binaryfile
 * ( phase DELETE_FILE_ROW , tx REQUIRES_NEW ) . Si cette derniere phase
 * echoue ( ou crash entre les deux ) , il reste une binaryfile ORPHELINE
 * ( visible dans l'historique mais sans donnees ) . La suppression etant
 * voulue , on la TERMINE EN AVANT plutot que de rollback : ce handler
 * rejoue la suppression de la binaryfile .
 *
 * <p>Declenche par {@code compensateNow} ( tentative synchrone immediate
 * dans le finally ) et , en filet , par le {@code CompensationSweeper}
 * ( retry avec backoff apres TTL ) . Idempotent + smart-check anti-perte
 * delegues a {@link OrphanBinaryFileCleaner} ( meme logique que
 * {@link BinaryFileCompensationHandler} , DRY ) .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class DeleteFileRowCompensationHandler implements CompensationHandler {

    public static final String OP_TYPE = "DELETE_FILE_ROW";

    private final OrphanBinaryFileCleaner cleaner;

    public DeleteFileRowCompensationHandler(OrphanBinaryFileCleaner cleaner) {
        this.cleaner = cleaner;
    }

    @Override
    public String operationType() { return OP_TYPE; }

    @Override
    public void compensate(CompensationLogEntry entry) {
        cleaner.deleteIfOrphan(entry.targetSchema(), UUID.fromString(entry.targetId()));
    }
}
