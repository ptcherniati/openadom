package fr.inra.oresing.rest.model.application;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.CurrentApplicationUserRolesResult;
import fr.inra.oresing.rest.model.authorization.CurrentUserRolesResult;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;


public record ApplicationLightResult(
        Application application,
        CurrentApplicationUserRolesResult currentApplicationUserRolesResult,
        boolean hasSignedCharte,
        boolean hasSignedLastCharte
) {

    public static ApplicationLightResult of(Application application, CurrentUserRoles currentUserRoles) {
        Timestamp charteSignedAt = Optional.ofNullable(currentUserRoles)
                .map(CurrentUserRoles::user)
                .map(OreSiUser::getChartes)
                .map(chartes->chartes.get(application.getId()))
                .orElse(null);
        Timestamp lastChartes = application.getLastChartes();
        return new ApplicationLightResult(
                application,
                CurrentApplicationUserRolesResult.of(
                        currentUserRoles,
                        application.getId()),
                charteSignedAt != null,
                charteSignedAt != null && lastChartes.before(charteSignedAt)
        );
    }

}