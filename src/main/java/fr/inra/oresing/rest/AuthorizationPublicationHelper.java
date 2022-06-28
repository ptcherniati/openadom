package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.BinaryFileDataset;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AuthorizationPublicationHelper {
    Method storeFile;
    OreSiRepository.RepositoryForApplication repo;
    AuthorizationService authorizationService;
    OreSiService oreSiService;
    private AuthorizationRepository authorizationRepository;

    private UserRepository userRepository;
    private Application application;
    private String dataType;
    private Optional<CurrentUserRoles> currentUserRolesOptional;
    @Getter
    private boolean isApplicationCreator;
    private Boolean canPublishOrUnPublish;
    private Boolean canDeposit;
    private AuthorizationsResult authorizationsForUser;
    @Getter
    private boolean isRepository;
    private FileOrUUID params;

    private boolean requiredAuthorizationMatchForFile(Map<String, String> requiredAuthorizationInDataBase) {
        final Optional<Map<String, Ltree>> requiredAuthorizationForFile = Optional.ofNullable(this.params)
                .map(FileOrUUID::getBinaryfiledataset)
                .map(BinaryFileDataset::getRequiredAuthorizations);
        if (requiredAuthorizationForFile.isPresent()) {
            for (Map.Entry<String, Ltree> requiredAuthorizationForFileEntry : requiredAuthorizationForFile.get().entrySet()) {
                String scope = requiredAuthorizationForFileEntry.getKey();
                String ltree = requiredAuthorizationForFileEntry.getValue().getSql();
                String toCompareLtree = requiredAuthorizationInDataBase.getOrDefault(scope, "");
                if (!ltree.startsWith(toCompareLtree)) {
                    return false;
                }
            }
            return true;
        } else {
            return true;
        }
    }

    public AuthorizationPublicationHelper(OreSiRepository.RepositoryForApplication repositoryForApplication, AuthorizationService authorizationService, OreSiService oreSiService) {
        this.repo = repositoryForApplication;
        this.authorizationService = authorizationService;
        this.oreSiService = oreSiService;
    }

    public AuthorizationPublicationHelper init(Application application, UserRepository userRepository,String dataType, FileOrUUID params) {
        this.authorizationRepository = repo.authorization();
        this.userRepository = userRepository;
        this.application = application;
        this.dataType = dataType;
        Optional.ofNullable(params)
                .map(par -> par.getBinaryfiledataset() != null ?
                        params.getBinaryfiledataset()
                        : BinaryFileDataset.EMPTY_INSTANCE())
                .ifPresent(binaryFileDataset -> {
                    binaryFileDataset.setDatatype(dataType);
                    this.params = params;
                });
        this.currentUserRolesOptional = Optional.ofNullable(userRepository.getRolesForCurrentUser());
        this.isApplicationCreator = currentUserRolesOptional
                .map(CurrentUserRoles::getMemberOf)
                .map(roles -> roles.stream().anyMatch(role -> OreSiRole.applicationCreator().getAsSqlRole().equals(role)))
                .orElse(false);
        this.authorizationsForUser = authorizationService.getAuthorizationsForUser(application.getName(), dataType, currentUserRolesOptional.map(CurrentUserRoles::getCurrentUser).orElse(""));
        final Optional<BinaryFileDataset> binaryFileDataset = Optional.ofNullable(params)
                .map(par -> par.getBinaryfiledataset() != null ?
                        params.getBinaryfiledataset()
                        : BinaryFileDataset.EMPTY_INSTANCE());
        this.isRepository = isRepository(application, dataType);
        return this;
    }

    private boolean isRepository(Application application, String dataType) {
        return Optional.of(application.getConfiguration())
                .map(Configuration::getDataTypes)
                .map(conf -> conf.get(dataType))
                .map(Configuration.DataTypeDescription::getRepository)
                .isPresent();
    }


    private boolean canDoOperation(OperationType operationType, String errorMessage) {
        final SiOreIllegalArgumentException siOreIllegalArgumentException = new SiOreIllegalArgumentException(errorMessage, Map.of(
                "dataType", dataType,
                "application", application.getName())
        );
        final boolean hasRightForOperationType = hasRightForOperationType(operationType);
        if (this.isRepository) {
            if (hasRightForOperationType) {
                return true;
            }
            throw siOreIllegalArgumentException;
        }
        if (isApplicationCreator || (hasRightForOperationType && Optional.ofNullable(authorizationsForUser)
                .map(AuthorizationsResult::getAuthorizationResults)
                .map(authorizationResult -> authorizationResult.get(operationType))
                .map(parsedAuhorizations -> parsedAuhorizations.stream().anyMatch(parsedAuhorization -> {
                    final Map<String, String> requiredAuthorizationsInDatabase = parsedAuhorization.getRequiredAuthorizations();
                    if (requiredAuthorizationsInDatabase.isEmpty()) {
                        return false;
                    } else {
                        return requiredAuthorizationMatchForFile(requiredAuthorizationsInDatabase);
                    }
                }))
                .orElse(false))) {
            return true;
        }
        throw siOreIllegalArgumentException;
    }

    public boolean hasRightForDeposit() {
        if (canDeposit == null) {
            this.canDeposit = canDoOperation(OperationType.depot, "noRightsForDeposit");
        }
        return canDeposit;
    }

    public boolean hasRightForPublishOrUnPublish() {
        if (canPublishOrUnPublish == null) {
            this.canPublishOrUnPublish = canDoOperation(OperationType.publication, "noRightForPublish");
        }
        return canPublishOrUnPublish;
    }

    private boolean hasRightForOperationType(OperationType operationType) {
        return isApplicationCreator || Optional.ofNullable(authorizationsForUser)
                .map(AuthorizationsResult::getAuthorizationResults)
                .map(authorizationResult -> authorizationResult.get(operationType))
                .map(list -> !list.isEmpty())
                .orElse(false);
    }

    public BinaryFile loadOrCreateFile(MultipartFile file, FileOrUUID params, Application app) {
        assert hasRightForDeposit();
        BinaryFile storedFile = Optional.ofNullable(params)
                .map(param -> param.getFileid())
                .map(uuid -> repo.binaryFile().tryFindByIdWithData(uuid).orElse(null))
                .orElseGet(() -> {
                    UUID fileId = null;
                    try {
                        fileId = this.oreSiService.storeFile(app, file, "", Optional.ofNullable(params).map(p->p.getBinaryfiledataset()).orElse(null));
                    } catch (IOException e) {
                        throw null;
                    }
                    BinaryFile binaryFile = repo.binaryFile().tryFindByIdWithData(fileId).orElse(null);
                    if (binaryFile == null) {
                        return null;
                    }
                    if (params != null) {
                        binaryFile.getParams().binaryFiledataset = params.binaryfiledataset;
                    }
                    fileId = repo.binaryFile().store(binaryFile);
                    return repo.binaryFile().tryFindByIdWithData(fileId).orElse(null);
                });
        return storedFile;
    }

}