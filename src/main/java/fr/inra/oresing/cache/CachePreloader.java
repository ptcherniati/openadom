package fr.inra.oresing.cache;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prechauffe les caches JVM pour une application donnee .
 *
 * <p>Strategie :
 * <ul>
 *   <li>Enumere les dataNames depuis la configuration de l'app
 *       ( {@code Application.getAllDataNames()} ) ; aucun acces SQL
 *       supplementaire au-dela de ce que ferait un user lambda .</li>
 *   <li>Pour chaque dataName : appelle
 *       {@code DataService.getFilterListResult} ( remplit
 *       {@code filterListCache} ) et
 *       {@code DataService.getCheckedFormatComponents} ( remplit
 *       {@code checkedFormatComponentsCache} ) . Aucun nouvel endpoint
 *       SQL n'est introduit .</li>
 *   <li>Option {@code includeReferencedFiles} : si activee , liste les
 *       binaryfiles publies par dataName et appelle
 *       {@code findBinaryFileIdsWithLinks} ( remplit
 *       {@code referencedFilesCache} ) .</li>
 *   <li>Option {@code parallel} : execute via {@link ExecutorService}
 *       de taille bornee ; sinon execute sequentiellement dans le
 *       thread appelant .</li>
 * </ul>
 *
 * <p>Out of scope : {@code authorizationScopesCache} et
 * {@code data_versioning_scope_cache} sont scopes par utilisateur ; on
 * ne peut pas les prechauffer sans connaitre l'identite de chaque
 * appelant futur .
 *
 * <p>Erreurs : chaque task est isolee ; un echec sur 1 dataName ne
 * stoppe pas les autres . Le compteur d'erreurs est remonte dans la
 * reponse .
 */
@Slf4j
@Component
public class CachePreloader {

    public record PreloadReport(
            int filterListPreloaded,
            int checkedFormatPreloaded,
            int referencedFilesPreloaded,
            int errors,
            long durationMs,
            boolean parallel,
            int parallelism) {}

    @Value("${openadom.cache.preload.parallelism:6}")
    private int parallelism;

    private final ServiceContainer serviceContainer;

    public CachePreloader(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }

    /**
     * @param application             application cible
     * @param includeReferencedFiles  si true , liste les binaryfiles
     *                                publies de chaque dataName et
     *                                prechauffe le graphe de liaisons
     * @param parallel                si true , execute les tasks via un
     *                                pool borne ; sinon sequentiel
     * @return rapport synthese
     */
    public PreloadReport preload(Application application,
                                  boolean includeReferencedFiles,
                                  boolean parallel) {
        long t0 = System.currentTimeMillis();
        List<String> dataNames = application.getAllDataNames();

        AtomicInteger filterListCount = new AtomicInteger();
        AtomicInteger checkedFormatCount = new AtomicInteger();
        AtomicInteger refFilesCount = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        Runnable refFilesTask = includeReferencedFiles
                ? () -> {
                    try {
                        // Liste tous les binaryfile publies de l'app et
                        // declenche findBinaryFileIdsWithLinks par
                        // datatype - remplit referencedFilesCache .
                        // Implementation deportee : on appelle
                        // findBinaryFileIdsWithLinks avec l'ensemble
                        // des ids publies remontes par
                        // getFilesOnRepository ; le repository regroupe
                        // par datatype , le service cache par fileId .
                        refFilesCount.addAndGet(preloadReferencedFiles(application, dataNames));
                    } catch (RuntimeException ex) {
                        log.warn("preload referencedFiles failed for {} : {}",
                                application.getName(), ex.getMessage());
                        errors.incrementAndGet();
                    }
                }
                : null;

        if (parallel) {
            int effectiveParallelism = Math.clamp(parallelism, 1, dataNames.size() + 1);
            ExecutorService pool = Executors.newFixedThreadPool(effectiveParallelism, r -> {
                Thread t = new Thread(r, "cache-preload-" + application.getName());
                t.setDaemon(true);
                return t;
            });
            try {
                List<CompletableFuture<Void>> tasks = new java.util.ArrayList<>(dataNames.size() * 2 + 1);
                for (String dataName : dataNames) {
                    tasks.add(CompletableFuture.runAsync(() -> tryFilterList(application, dataName, filterListCount, errors), pool));
                    tasks.add(CompletableFuture.runAsync(() -> tryCheckedFormat(application, dataName, checkedFormatCount, errors), pool));
                }
                if (refFilesTask != null) {
                    tasks.add(CompletableFuture.runAsync(refFilesTask, pool));
                }
                CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            } finally {
                pool.shutdown();
                try {
                    if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                        pool.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            for (String dataName : dataNames) {
                tryFilterList(application, dataName, filterListCount, errors);
                tryCheckedFormat(application, dataName, checkedFormatCount, errors);
            }
            if (refFilesTask != null) {
                refFilesTask.run();
            }
        }

        long durationMs = System.currentTimeMillis() - t0;
        PreloadReport report = new PreloadReport(
                filterListCount.get(),
                checkedFormatCount.get(),
                refFilesCount.get(),
                errors.get(),
                durationMs,
                parallel,
                parallel ? Math.clamp(parallelism, 1, dataNames.size() + 1) : 1
        );
        log.info("CachePreloader {} : {} dataNames , {} ms , filterList={} checkedFormat={} refFiles={} errors={} ( parallel={} )",
                application.getName(), dataNames.size(), durationMs,
                report.filterListPreloaded(), report.checkedFormatPreloaded(),
                report.referencedFilesPreloaded(), report.errors(), parallel);
        return report;
    }

    private void tryFilterList(Application app, String dataName, AtomicInteger count, AtomicInteger errors) {
        try {
            serviceContainer.dataService().getFilterListResult(app, dataName);
            count.incrementAndGet();
        } catch (RuntimeException ex) {
            log.debug("preload filterList failed for {}::{} : {}",
                    app.getName(), dataName, ex.getMessage());
            errors.incrementAndGet();
        }
    }

    private void tryCheckedFormat(Application app, String dataName, AtomicInteger count, AtomicInteger errors) {
        try {
            serviceContainer.dataService().getCheckedFormatComponents(app.getName(), dataName);
            count.incrementAndGet();
        } catch (RuntimeException ex) {
            log.debug("preload checkedFormatComponents failed for {}::{} : {}",
                    app.getName(), dataName, ex.getMessage());
            errors.incrementAndGet();
        }
    }

    private int preloadReferencedFiles(Application app, List<String> dataNames) {
        int total = 0;
        fr.inra.oresing.rest.binaryFile.BinaryFileService bfs =
                (fr.inra.oresing.rest.binaryFile.BinaryFileService) serviceContainer.binaryFileService();
        for (String dataName : dataNames) {
            try {
                fr.inra.oresing.domain.BinaryFileDataset bfd = new fr.inra.oresing.domain.BinaryFileDataset();
                bfd.setDatatype(dataName);
                java.util.List<fr.inra.oresing.domain.BinaryFile> files = bfs.getFilesOnRepository(
                        app.getName(), dataName, bfd, false);
                java.util.Set<java.util.UUID> publishedIds = files.stream()
                        .filter(f -> f.getParams() != null && f.getParams().published())
                        .map(fr.inra.oresing.domain.BinaryFile::getId)
                        .collect(java.util.stream.Collectors.toSet());
                if (publishedIds.isEmpty()) continue;
                bfs.findBinaryFileIdsWithLinks(app.getId(), dataName, publishedIds);
                total += publishedIds.size();
            } catch (RuntimeException ex) {
                log.debug("preload referencedFiles failed for {}::{} : {}",
                        app.getName(), dataName, ex.getMessage());
            }
        }
        return total;
    }
}
