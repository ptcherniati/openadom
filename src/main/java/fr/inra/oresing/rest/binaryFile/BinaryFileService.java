package fr.inra.oresing.rest.binaryFile;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.cache.MemoryCache;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.services.DefaultAuthorizationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional(readOnly = true)

public class BinaryFileService implements fr.inra.oresing.domain.services.file.BinaryFileService {
    private final OreSiRepository repository;
    private final ServiceContainer serviceContainer;
    private final AuthenticationService authenticationService;
    private final JsonRowMapper<?> jsonRowMapper;

    /**
     * Cache memoire des resultats de getReferencedBinaryFiles : la query
     * sous-jacente est tres couteuse ( 3-5s sur si_acbb 9.9M rows car
     * le JOIN reference_reference traverse des millions de tuples meme
     * avec l'index composite V6 ) , et le resultat ne depend QUE des
     * relations data ( referencevalue + reference_reference ). Toggle
     * publish ne change pas ces relations donc le cache reste valide.
     *
     * Configuration ( meme pattern que filterListCache /
     * checkedFormatComponentsCache ) :
     * <ul>
     *   <li>{@code openadom.cache.referenced-files.enabled} : si {@code false} ,
     *       chaque appel re-execute la SQL ( bypass complet ) ;</li>
     *   <li>{@code openadom.cache.referenced-files.max-entries} : capacite LRU ;</li>
     *   <li>{@code openadom.cache.referenced-files.ttl-minutes} : TTL ;
     *       si {@code <= 0} l'auto-rebuild est desactive ( pas de TTL , les
     *       entrees vivent jusqu'a invalidation explicite ) .</li>
     * </ul>
     *
     * Invalidation : sur toute mutation reelle ( import , delete ,
     * unpublish-with-delete ) via {@link #invalidateReferencedFilesCache} .
     *
     * Cle : applicationId + "::" + dataType + "::" + singleFileId .
     * Valeur : List<ReferencedBinaryFiles> immutable .
     */
    @org.springframework.beans.factory.annotation.Value(
            "${openadom.cache.referenced-files.enabled:true}")
    private boolean referencedFilesCacheEnabled;

    @org.springframework.beans.factory.annotation.Value(
            "${openadom.cache.referenced-files.max-entries:200}")
    private int referencedFilesCacheMaxEntries;

    @org.springframework.beans.factory.annotation.Value(
            "${openadom.cache.referenced-files.ttl-minutes:30}")
    private long referencedFilesCacheTtlMinutes;

    private MemoryCache<String, List<ReferencedBinaryFiles>> referencedFilesCache;

    @jakarta.annotation.PostConstruct
    void initReferencedFilesCache() {
        this.referencedFilesCache = new MemoryCache<>(
                "referencedFiles",
                referencedFilesCacheMaxEntries,
                referencedFilesCacheTtlMinutes);
        log.info("referencedFilesCache initialised : enabled={} maxEntries={} ttlMinutes={}",
                referencedFilesCacheEnabled,
                referencedFilesCacheMaxEntries,
                referencedFilesCacheTtlMinutes);
    }

    /**
     * LiteImporter : capture du hash de config datatype au moment du upload .
     * Optionnel ( {@code @Autowired(required = false)} ) : test unitaires
     * passent sans this bean wired .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.rest.usecases.storage.versioning.ConfigHashService configHashService;

    public BinaryFileService(OreSiRepository repository, ServiceContainer serviceContainer, AuthenticationService authenticationService, JsonRowMapper jsonRowMapper) {
        this.repository = repository;
        this.serviceContainer = serviceContainer;
        this.authenticationService = authenticationService;
        this.jsonRowMapper = jsonRowMapper;
    }

    /**
     * Invalide les entrees cache referencedFiles pour une application
     * donnee. A appeler depuis tout chemin qui mute referencevalue /
     * reference_reference ( import , delete , unpublish-with-delete ) .
     *
     * @param applicationName  application qui a vu sa data muter
     */
    public void invalidateReferencedFilesCache(String applicationName) {
        if (applicationName == null) return;
        String prefix = applicationName + "::";
        referencedFilesCache.invalidateMatching(k -> k.startsWith(prefix));
    }

    /** Observabilité : taille courante du cache referencedFiles . */
    public int getReferencedFilesCacheSize() {
        return referencedFilesCache == null ? 0 : referencedFilesCache.size();
    }

    /**
     * Observabilité : taille mémoire approximative du cache
     * referencedFiles via sérialisation Jackson . Appelée uniquement
     * par CacheSizeEstimator , pas en hot path .
     */
    public long estimateReferencedFilesCacheSizeBytes(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return referencedFilesCache == null ? 0L : referencedFilesCache.estimateSizeBytes(mapper);
    }

    @Override
    @Transactional
    public UUID storeFile(final Application application, final DataFile file, String comment, final BinaryFileDataset binaryFileDataset) throws IOException {
        authenticationService.setRoleForClient();
        // creation du fichier
        comment = Optional.ofNullable(binaryFileDataset)
                .map(BinaryFileDataset::getComment)
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .orElse(comment);
        final BinaryFile binaryFile = new BinaryFile();
        binaryFile.setApplication(application.getId());
        binaryFile.setComment(comment);
        binaryFile.setName(file.fileName() != null ? file.fileName() : "charte.pdf");
        binaryFile.setSize(file.fileSize());
        binaryFile.setFileData(file.inputStream());
        BinaryFileInfos binaryFileInfos = BinaryFileInfos.forPublish(false, OreSiApiRequestContext.getRequestUserId(), LocalDateTime.now().toString(), binaryFileDataset);
        // LiteImporter : capturer le hash de config du datatype au moment du
        // upload . Utilise plus tard au republish par
        // PublishLifecyclePhase2Handler pour decider lite vs FULL ( si le
        // datatype n'est pas resolu , on garde null = forcer FULL ) .
        String datatype = Optional.ofNullable(binaryFileDataset)
                .map(BinaryFileDataset::getDatatype)
                .orElse(null);
        if (datatype != null && configHashService != null) {
            String hash = configHashService.computeHash(application, datatype).orElse(null);
            if (hash != null) {
                binaryFileInfos = binaryFileInfos.withConfigHash(hash);
            }
        }
        binaryFile.setParams(binaryFileInfos);
        return getBinaryFileRepository(application).store(binaryFile);
    }

    @Override
    public Optional<BinaryFile> getFile(final String applicationNameOrID, final UUID id) {
        authenticationService.setRoleForClient();
        return getBinaryFileRepository(applicationNameOrID).tryFindById(id);
    }

    @Override
    public Optional<BinaryFile> getFileWithData(final String applicationNameOrID, final UUID id) {
        authenticationService.setRoleForClient();
        return getBinaryFileRepository(applicationNameOrID).tryFindByIdWithData(id);
    }

    private BinaryFileRepository getBinaryFileRepository(Application application) {
        return repository.getRepository(application).binaryFile();
    }

    private BinaryFileRepository getBinaryFileRepository(String applicationNameOrId) {
        return repository.getRepository(applicationNameOrId).binaryFile();
    }

    @Transactional
    @Override
    public Optional<UUID> removeFile(Application application, UUID id) {
        return getFile(application.getName(), id)
                .map(BinaryFile::getId)
                .map(getBinaryFileRepository(application)::delete)
                .orElse(false) ? Optional.of(id) : Optional.empty();
    }

    @Override
    public ReportErrors findPublishedVersion(final String nameOrId, final String dataType, final FileOrUUID params, final Set<BinaryFile> filesToStore, final boolean searchOverlaps) {
        if (params != null && params.binaryfiledataset() != null) {
            if (searchOverlaps) {
                final List<BinaryFile> overlapingFiles = getFilesOnRepository(nameOrId, dataType, params.binaryfiledataset(), true);
                if (!overlapingFiles.isEmpty()) {
                    final List<CsvRowValidationCheckResult> errors = List.of(
                            new CsvRowValidationCheckResult(
                                    DefaultValidationCheckResult.error(
                                            "overlappingpublishedversion",
                                            ImmutableMap.of("fileOrUUID", params,
                                                    "files", overlapingFiles.stream()
                                                            .map(f -> f.getParams().binaryFiledataset().toString())
                                                            .collect(Collectors.toSet()
                                                            )
                                            ),
                                            null
                                    ),
                                    -1
                            )
                    );
                    final ReportErrors reportErrors = new ReportErrors(jsonRowMapper);
                    reportErrors.addAll(errors);
                    return reportErrors;
                }
            }
            getFilesOnRepository(nameOrId, dataType, params.binaryfiledataset(), false)
                    .stream()
                    .filter(f -> f.getParams().published())
                    .forEach(f -> {
                        f.markAsPublished(false);
                        filesToStore.add(f);
                    });
        }
        return new ReportErrors(jsonRowMapper);
    }

    @Override
    public List<BinaryFile> getFilesOnRepository(final String nameOrId, final String datatype, final BinaryFileDataset binaryFileDataset, final boolean overlap) {
        authenticationService.setRoleForClient();
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        DataRepository dataRepository = serviceContainer.dataService().getDataRepository(application);
        return getBinaryFileRepository(nameOrId).findByBinaryFileDataset(datatype, binaryFileDataset.testrequiredAuthorizationsAndReturnHierarchicalKeys(dataRepository), overlap);
    }

    @Override
    public AdditionalBinaryFileResult getAdditionalBinaryFileResult(AdditionalBinaryFile additionalBinaryFile) {

        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        DefaultAuthorizationService.authorizationsToParsedAuthorizations(
                additionalBinaryFile.getAssociates(),
                authorizationsParsed);
        return new AdditionalBinaryFileResult(additionalBinaryFile, authorizationsParsed);
    }

    @Override
    public Set<UUID> findBinaryFileIdsWithLinks(UUID applicationId, String datatype, Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        // Reutilise le cache JVM existant ( referencedFilesCache ) via le
        // chemin {@link #getReferencedBinaryFiles} ; on extrait juste les
        // ids distincts qui ont au moins une entree . Hit cache = sub-ms ,
        // miss = unique query SQL (3s 1ere fois) cachee ensuite .
        return getReferencedBinaryFiles(applicationId, datatype, ids).stream()
                .map(ReferencedBinaryFiles::binaryFileId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<ReferencedBinaryFiles> getReferencedBinaryFiles(UUID applicationId, String datatype, Set<UUID> binaryFileIds) {
        if (binaryFileIds == null || binaryFileIds.isEmpty()) return List.of();

        // Bypass complet si cache desactive : query SQL directe pour le set
        // entier , pas de write cache . Permet de mesurer la latence "naked"
        // ou de desactiver le cache temporairement en prod sans rebuild .
        if (!referencedFilesCacheEnabled) {
            log.debug("referencedFiles cache disabled , querying directly for {} ids", binaryFileIds.size());
            return getBinaryFileRepository(applicationId.toString())
                    .getReferencedBinaryFiles(datatype, binaryFileIds);
        }

        // Cache PER binaryFileId ( pas sur le set complet ) : sinon la
        // cle change a chaque toggle publish/depublie ( liste publishedIds
        // mute ) et le cache est systematiquement miss . Ici chaque file id
        // a sa propre cle ; un toggle qui ajoute / retire un id N+1 garde
        // les N hits valides .
        Set<UUID> toQuery = new HashSet<>();
        List<ReferencedBinaryFiles> result = new ArrayList<>(binaryFileIds.size());
        for (UUID id : binaryFileIds) {
            String key = applicationId + "::" + datatype + "::" + id;
            List<ReferencedBinaryFiles> cached = referencedFilesCache.get(key);
            if (cached != null) {
                log.debug("referencedFiles cache hit  for {}", key);
                result.addAll(cached);
            } else {
                toQuery.add(id);
            }
        }
        if (!toQuery.isEmpty()) {
            log.debug("referencedFiles cache miss for {} ids , querying", toQuery.size());
            List<ReferencedBinaryFiles> fresh = getBinaryFileRepository(applicationId.toString())
                    .getReferencedBinaryFiles(datatype, toQuery);
            // Regrouper le resultat par binaryFileId pour pouvoir cacher
            // par ID . Tout id absent du resultat ( aucune reference )
            // recoit une liste vide cachee pour eviter les futures
            // queries inutiles .
            Map<UUID, List<ReferencedBinaryFiles>> grouped = fresh.stream()
                    .collect(Collectors.groupingBy(ReferencedBinaryFiles::binaryFileId));
            for (UUID id : toQuery) {
                List<ReferencedBinaryFiles> sub = grouped.getOrDefault(id, List.of());
                referencedFilesCache.put(applicationId + "::" + datatype + "::" + id, sub);
                result.addAll(sub);
            }
        }
        return result;
    }
}