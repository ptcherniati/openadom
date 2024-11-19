package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record ApplicationUserResult(
        UUID applicationId,
        UUID id,
        String label,
        String email,
        boolean isApplicationManager,
        boolean isUserManager,
        boolean isApplicationUser,
        boolean isActiveApplicationUser
) {

    public static final List<String> getApplicationRoles(Application application) {
        return List.of(
                OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole(),
                OreSiRightOnApplicationRole.userAdminOn(application).getAsSqlRole()
        );
    }

    public static ApplicationUserResult of(UUID applicationId, OreSiUser oreSiUser,
                                           Map<String, List<String>> administratorRoles, Timestamp charteTimestamp) {
        Boolean isApplicationManager = administratorRoles.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(OreSiRightOnApplicationRole.APPLICATION_MANAGER))
                .anyMatch(entry -> entry.getValue().contains(oreSiUser.getId().toString()));
        Boolean isUserManager = isApplicationManager|| administratorRoles.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(OreSiRightOnApplicationRole.USER_MANAGER))
                .anyMatch(entry -> entry.getValue().contains(oreSiUser.getId().toString()));
        Optional<Timestamp> charteTimeStampOpt = Optional.ofNullable(oreSiUser)
                .filter(user -> user.getAccountstate().equals(OreSiUser.OreSiUserStates.active))
                .map(OreSiUser::getChartes)
                .map(chartes -> chartes.get(applicationId.toString()));
        boolean isApplicationUser = isUserManager|| charteTimeStampOpt.isPresent();
        boolean isActiveApplicationUser = charteTimeStampOpt.stream().anyMatch(
                timestamp -> charteTimestamp == null ? true : timestamp.after(charteTimestamp));
        return new ApplicationUserResult(
                applicationId,
                oreSiUser.getId(),
                oreSiUser.getLogin(),
                oreSiUser.getEmail(),
                isApplicationManager,
                isUserManager,
                isApplicationUser,
                isActiveApplicationUser
        );
    }
}
