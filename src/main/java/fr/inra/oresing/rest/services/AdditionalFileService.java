package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.AdditionalFileDescription;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AdditionalFileRepository;
import fr.inra.oresing.persistence.AdditionalFileSearchHelper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.model.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.model.additionalfiles.exception.AdditionalFileParamsParsingResult;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import fr.inra.oresing.rest.model.rightsrequest.GetAdditionalFilesResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;


@Slf4j
@Component
@Transactional(readOnly = true)
public class AdditionalFileService {
    public static final String CHARTE = "__charte__";
    @Value("classpath:charte/default_charte.pdf")
    Resource defaultCharte;
    @Setter
    private ServiceContainer serviceContainer;

    private final UserRepository userRepository;

    private final OreSiRepository repository;
    private final RepositoryEntityLinks repositoryEntityLinks;

    public AdditionalFileService(
            UserRepository userRepository,
            ServiceContainer serviceContainer,
            OreSiRepository repository,
            RepositoryEntityLinks repositoryEntityLinks) {
        this.userRepository = userRepository;
        this.repository = repository;
        this.repositoryEntityLinks = repositoryEntityLinks;
        this.serviceContainer = serviceContainer;
    }

    @Transactional
    void addAdditionalfile(final Application application, final String refType, final MultipartFile file, final UUID fileId) {
        AdditionalFileRepository additionalFileRepository = repository.getRepository(application).additionalBinaryFile();
    }


    @Transactional(readOnly = true)
    public AdditionalBinaryFile findCharte(final Application application) {
        return repository.getRepository(application).additionalBinaryFile()
                .findById(application.getId());
    }

    /**
     *
     */
    List<AdditionalBinaryFile> findAdditionalFile(final Application application, final AdditionalFilesInfos additionalFilesInfos) {
        AdditionalFileSearchHelper additionalFileSearchHelper = new AdditionalFileSearchHelper(application, additionalFilesInfos);
        String where = additionalFileSearchHelper.buildWhereRequest();
        serviceContainer.authenticationService().setRoleForClient();
        return repository
                .getRepository(application)
                .additionalBinaryFile()
                .findByCriteria(additionalFileSearchHelper);
    }

    private Application getApplication(final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        return repository.application().findApplication(nameOrId);
    }

    @Transactional()
    public UUID createOrUpdate(final CreateAdditionalFileRequest createAdditionalFileRequest,
                               final String additionalFileName,
                               final String nameOrId,
                               final MultipartFile file) {
        serviceContainer.authenticationService().setRoleForClient();
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);

        AdditionalBinaryFile additionalBinaryFile = Optional.of(createAdditionalFileRequest)
                .map(CreateAdditionalFileRequest::id)
                .map(id -> {
                    UUID id1 = id;
                    if (CHARTE.equals(additionalFileName)) {
                        id1 = application.getId();
                    }
                    AdditionalBinaryFile abf = repository.getRepository(application).additionalBinaryFile().findById(id1);
                    if (abf == null) {
                        abf = new AdditionalBinaryFile();
                        abf.setId(id1);
                        abf.setApplication(id1);
                        abf.setFileType(CHARTE);
                        abf.setForApplication(true);
                    }
                    return abf;
                })
                .orElseGet(AdditionalBinaryFile::new);
        additionalBinaryFile.setFileInfos(createAdditionalFileRequest.fields());
        additionalBinaryFile.setApplication(application.getId());
        additionalBinaryFile.setForApplication(
                Optional.ofNullable(createAdditionalFileRequest.forApplication())
                        .orElse(CHARTE.equals(additionalFileName)));
        if (file != null) {
            additionalBinaryFile.setSize(file.getSize());
            additionalBinaryFile.setFileName(file.getOriginalFilename());
            try {
                additionalBinaryFile.setData(file.getBytes());
            } catch (final IOException e) {
                throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
            }
        }
        OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
        additionalBinaryFile.setComment(createAdditionalFileRequest.comment());
        additionalBinaryFile.setFileType(createAdditionalFileRequest.fileType());
        additionalBinaryFile.setCreationUser(additionalBinaryFile.getCreationUser() == null ? currentUser.getId() : additionalBinaryFile.getCreationUser());
        additionalBinaryFile.setUpdateUser(currentUser.getId());
        additionalBinaryFile.setId(additionalBinaryFile.getId() == null ? UUID.randomUUID() : additionalBinaryFile.getId());
        OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
        oreSiAuthorization.setId(additionalBinaryFile.getId());
        oreSiAuthorization.setApplication(application.getId());

        /*TODO Optional.ofNullable(createAdditionalFileRequest)
                .map(CreateAdditionalFileRequest::associates)
                .ifPresent(associate -> oreSiAuthorization
                        .setAuthorizations(associate.authorizations())
                )
        ;*/
        List<OreSiAuthorization> authorizations = List.of(oreSiAuthorization);
        additionalBinaryFile.setAssociates(authorizations);
        final UUID store = repository.getRepository(application).additionalBinaryFile().store(additionalBinaryFile);
        if (CHARTE.equals(additionalBinaryFile.getFileType()) && store != null) {
            userRepository.invalidateCharte(store);
        }
        return store;
    }

    public void getCharte(final OutputStream out, final HttpServletResponse response, final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) throws IOException {
        AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        final AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();

        final AdditionalFileRepository additionalFileRepository = repository.getRepository(nameOrId).additionalBinaryFile();
        serviceContainer.authenticationService().setRoleForClient();
        final Stream<AdditionalBinaryFile> additionnalFilesStream = additionalFileRepository
                .findByCriteriaStream(Objects.requireNonNull(additionalFileSearchHelper));
        final Mono<byte[]> mono = Mono.just(additionnalFilesStream)

                .map(Stream::findFirst)
                .mapNotNull(o -> o.orElse(null))//orElseGet(() -> getDefaultCharte(additionalFilesInfos, nameOrId)))
                .map(additionalBinaryFile -> {
                    response.setHeader("Content-Disposition", "inline; filename=" + additionalBinaryFile.getFileName());
                    response.setHeader("Content-Length", Long.toString(additionalBinaryFile.getSize()));
                    return additionalBinaryFile;
                })
                .map(AdditionalBinaryFile::getData)
                .onErrorComplete();
        final byte[] block = mono.block();
        if (block != null && block.length > 0) {
            out.write(block);
            out.flush();
        } else {
            out.write(FileCopyUtils.copyToByteArray(defaultCharte.getInputStream()));
        }
    }


    public void getAdditionalFilesNamesZipStream(final ZipOutputStream zipOutputStream, final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        BadAdditionalFileParamsSearchException.check(additionalFileParamsParsingResult);
        final AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();
        final AtomicLong counter = new AtomicLong(0);
        repository
                .getRepository(application).additionalBinaryFile()
                .findByCriteriaStream(Objects.requireNonNull(additionalFileSearchHelper))
                .forEach(additionalBinaryFile -> {
                    try {
                        if (counter.incrementAndGet() % 1000 == 0) {
                            zipOutputStream.flush();
                        }
                        additionalFileSearchHelper.addAdditionalFilesToZip(additionalBinaryFile, zipOutputStream, "");
                    } catch (final IOException e) {
                        throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                    }
                });

    }

    @Transactional()
    public List<UUID> deleteAdditionalFiles(final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        BadAdditionalFileParamsSearchException.check(additionalFileParamsParsingResult);
        final AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();
        try {
            return repository
                    .getRepository(application).additionalBinaryFile()
                    .deleteByCriteria(Objects.requireNonNull(additionalFileSearchHelper));
        } catch (final DataIntegrityViolationException e) {
            return null;
        }
    }

    public AdditionalFileParamsParsingResult getAdditionalFileSearchHelper(final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        final Application application = "__charte__".equals(additionalFilesInfos.getFiletype()) ? serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId) : serviceContainer.applicationService().getApplication(nameOrId);
        final AdditionalFileParamsParsingResult.Builder builder = AdditionalFileParamsParsingResult.builder();
        for (final Map.Entry<String, AdditionalFilesInfos.AdditionalFileInfos> entry : additionalFilesInfos.getAdditionalFilesInfos().entrySet()) {
            final String additionalFileName = entry.getKey();
            AdditionalFileDescription additionalFileDescription = application.getConfiguration().additionalFiles().get(additionalFileName);
            if (additionalFileDescription == null) {
                builder.unknownAdditionalFilename(additionalFileName, additionalFilesInfos.getAdditionalFilesInfos().keySet());
            } else {
                AdditionalFilesInfos.AdditionalFileInfos value = entry.getValue();
                if (value != null && !CollectionUtils.isEmpty(value.getFieldFilters())) {
                    for (final AdditionalFilesInfos.FieldFilters filter : value.getFieldFilters()) {
                        if (additionalFileDescription.formFields().get(filter.field) == null) {
                            builder.unknownFieldAdditionalFilename(additionalFileName, filter.field, additionalFileDescription.formFields().keySet());
                        }
                    }
                }
            }

        }
        return builder.build(application, additionalFilesInfos);
    }

    public GetAdditionalFilesResult findAdditionalFile(final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final AdditionalFileDescription description = Optional.ofNullable(application.getConfiguration().additionalFiles()).map(map -> map.get(additionalFilesInfos.getFiletype())).orElseGet(AdditionalFileDescription::emptyInstance);
        List<AdditionalBinaryFile> additionalFiles = serviceContainer.additionalFileService().findAdditionalFile(application, additionalFilesInfos);
        List<AdditionalBinaryFileResult> additionalBinaryFileResults = additionalFiles.stream().map(af -> serviceContainer.binaryFileService().getAdditionalBinaryFileResult(af)).toList();
        ImmutableSortedSet<GetGrantableResult.User> grantableUsers = serviceContainer.authorizationService().getGrantableUsers();
        List<String> fileNamesForFiletype = repository.getRepository(application).additionalBinaryFile().getFileNamesForFiletype(additionalFilesInfos.getFiletype());
        return new GetAdditionalFilesResult(grantableUsers, additionalFilesInfos.getFiletype(), additionalBinaryFileResults, description, fileNamesForFiletype);
    }

}