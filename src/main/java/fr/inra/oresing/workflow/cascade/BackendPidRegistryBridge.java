package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bridge Spring -> static field {@link StagingFinalizeSql#setBackendPidRegistry} .
 *
 * <p>{@link StagingFinalizeSql} est une classe utilitaire static ( pas un bean
 * Spring ) appelee depuis cascade / pipelines de plus bas niveau . Pour acceder
 * au registry de pid sans casser cette API , on injecte la reference statique
 * une fois au demarrage Spring via ce bridge .
 *
 * <p>Pourquoi pas un bean direct sur {@code StagingFinalizeSql} : changerait
 * la signature publique de {@code runFinalize} ( methode static ) , utilisee
 * depuis plusieurs sites dont des tests . Le bridge est un compromis minimal
 * qui n'impacte aucun caller existant .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendPidRegistryBridge {

    private final BackendPidRegistry backendPidRegistry;
    private final ImportProperties importProperties;

    @PostConstruct
    void wire() {
        StagingFinalizeSql.setBackendPidRegistry(backendPidRegistry);
        // P4b : wire le supplier vers ImportProperties pour que runFinalize puisse
        // lire la valeur courante du flag a chaque invocation ( supporte edition
        // live via oa-live admin sans redeploy ) . Lambda capture le bean Spring ,
        // donc une edition du field via setter est vue immediatement .
        StagingFinalizeSql.setUseColumnExtractionUpsertSupplier(importProperties::isUseColumnExtractionUpsert);

        // Robustness layers 1 + 2 : wire les 4 suppliers de config retry/lock_timeout .
        // Edition live via oa-live admin sans redeploy , effective au prochain finalize .
        StagingFinalizeSql.setLockTimeoutMinutesSupplier(importProperties::getFinalizeLockTimeoutMinutes);
        StagingFinalizeSql.setLockRetryMaxAttemptsSupplier(importProperties::getFinalizeLockRetryMaxAttempts);
        StagingFinalizeSql.setLockRetryBackoffInitialMsSupplier(importProperties::getFinalizeLockRetryBackoffInitialMs);
        StagingFinalizeSql.setLockRetryBackoffMaxMsSupplier(importProperties::getFinalizeLockRetryBackoffMaxMs);

        log.info("StagingFinalizeSql wired : BackendPidRegistry + useColumnExtractionUpsert={}"
                        + " + lockTimeout={}min , lockRetry max={} backoff={}ms cap={}ms",
                importProperties.isUseColumnExtractionUpsert(),
                importProperties.getFinalizeLockTimeoutMinutes(),
                importProperties.getFinalizeLockRetryMaxAttempts(),
                importProperties.getFinalizeLockRetryBackoffInitialMs(),
                importProperties.getFinalizeLockRetryBackoffMaxMs());
    }
}
