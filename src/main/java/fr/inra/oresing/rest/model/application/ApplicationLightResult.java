package fr.inra.oresing.rest.model.application;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.rest.model.authorization.CurrentApplicationUserRolesResult;

import java.sql.Timestamp;
import java.util.*;


public record ApplicationLightResult(
        List<ApplicationResult.DataSynthesis> dataSyntheses, Application application,
        CurrentApplicationUserRolesResult currentApplicationUserRolesResult,
        boolean hasSignedCharte,
        boolean hasSignedLastCharte
) {

    public static ApplicationLightResult of(Application application, CurrentUserRoles currentUserRoles, List<ApplicationResult.DataSynthesis> dataSyntheses) {
        Timestamp charteSignedAt = Optional.ofNullable(currentUserRoles)
                .map(CurrentUserRoles::user)
                .map(OreSiUser::getChartes)
                .map(chartes->chartes.get(application.getId()))
                .orElse(null);
        Timestamp lastChartes = application.getLastChartes();
        return new ApplicationLightResult(
                dataSyntheses,
                application,
                CurrentApplicationUserRolesResult.of(
                        currentUserRoles,
                        application.getId()),
                charteSignedAt != null,
                charteSignedAt != null && lastChartes.before(charteSignedAt)
        );
    }

}