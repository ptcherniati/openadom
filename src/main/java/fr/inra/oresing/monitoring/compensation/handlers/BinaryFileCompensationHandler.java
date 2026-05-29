package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationHandler;
import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Compensation handler pour {@code IMPORT_BINARYFILE} : supprime une row
 * binaryfile orpheline laissee par un import qui a fail apres la Phase 1
 * ( INSERT binaryfile committed ) mais avant le confirm Phase 3
 * ( workflow / cascade rollback ) .
 *
 * <p>Delegue la logique smart-delete ( REGLE D'OR anti-perte : on ne
 * supprime la binaryfile que si aucune {@code referencevalue} ne la
 * reference encore ) a {@link OrphanBinaryFileCleaner} , partagee avec
 * {@link DeleteFileRowCompensationHandler} ( DRY ) .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class BinaryFileCompensationHandler implements CompensationHandler {

    public static final String OP_TYPE = "IMPORT_BINARYFILE";

    private final OrphanBinaryFileCleaner cleaner;

    public BinaryFileCompensationHandler(OrphanBinaryFileCleaner cleaner) {
        this.cleaner = cleaner;
    }

    @Override
    public String operationType() { return OP_TYPE; }

    @Override
    public void compensate(CompensationLogEntry entry) {
        cleaner.deleteIfOrphan(entry.targetSchema(), UUID.fromString(entry.targetId()));
    }
}
