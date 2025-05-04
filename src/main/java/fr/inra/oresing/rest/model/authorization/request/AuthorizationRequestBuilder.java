package fr.inra.oresing.rest.model.authorization.request;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.request.AuthorizationForAll;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.authorization.request.AuthorizationWithRestriction;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.rest.model.authorization.CreateAuthorizationRequest;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;

/*
Roles {
        UPLOAD,
        DOWNLOAD,
        READ,
        ADMIN,
        PUBLICATION,
        ANY,
        DELETE
    }
 */

public class AuthorizationRequestBuilder {
    public static final String NAME = "name";
    static final YAMLMapper mapper = YAMLMapper.builder().build();
    @Getter
    private final Application application;
    @Getter
    final AuthorizationForAllBuilder authorizationForAllBuilder = new AuthorizationForAllBuilder(this);
    @Getter
    final AuthorizationWithRestrictionBuilder authorizationWithRestrictionBuilder = new AuthorizationWithRestrictionBuilder(this);
    final List<AuthorizationRequestError> errors;
    @Getter
    final List<UUID> allUsers;
    @Getter
    final List<OreSiAuthorization> authorizationsForCurrentUser;

    public AuthorizationRequestBuilder(Application application,
                                       List<UUID> allUsers,
                                       List<OreSiAuthorization> authorizationsForCurrentUser,
                                       final List<AuthorizationRequestError> errors) {
        this.application = application;
        this.allUsers = allUsers;
        this.authorizationsForCurrentUser = authorizationsForCurrentUser;
        this.errors = errors;
    }

    private boolean isValidRequest() {
        return errors.isEmpty();
    }

    public void buildError(final AuthorizationRequestException exception, Map<String, Object> params) {
        errors.add(new AuthorizationRequestError(exception, params));
    }

    public void buildError(final AuthorizationRequestException exception) {
        errors.add(new AuthorizationRequestError(exception, Map.of()));
    }

    public AuthorizationRequest build(
            CreateAuthorizationRequest authorizationRequest,
            DataRepositoryForBuffer dataRepositoryWithBuffer) {
        UUID authorizationId = authorizationRequest.uuid();
        String name = authorizationRequest.name();
        Set<UUID> userId = authorizationRequest.usersId();
        AuthorizationWithRestriction authorizationWithRestriction =
                MapUtils.isEmpty(authorizationRequest.authorizationsWithRestriction()) ?
                        null :
                        getAuthorizationWithRestrictionBuilder().build(
                                authorizationRequest.authorizationsWithRestriction(),
                                dataRepositoryWithBuffer
                        );

        AuthorizationForAll authorizationForAll =
                MapUtils.isEmpty(authorizationRequest.authorizationForAll()) ?
                        null :
                        getAuthorizationForAllBuilder().build(authorizationRequest.authorizationForAll());

        return new AuthorizationRequest(
                authorizationId,
                name,
                authorizationRequest.description(),
                application.getId(),
                userId,
                authorizationForAll,
                authorizationWithRestriction
        );
    }

    protected boolean existsReferences(Set<String> references) {
        Set<String> badReferences = new HashSet<>();
        for (String reference : references) {
            if (application
                    .findData(reference)
                    .isEmpty()) {
                badReferences.add(reference);
            }
        }
        if (CollectionUtils.isNotEmpty(badReferences)) {
            buildError(
                    AuthorizationRequestException.BAD_REFERENCES,
                    Map.of("badReferences", badReferences)
            );
            return false;
        }
        return true;
    }
}