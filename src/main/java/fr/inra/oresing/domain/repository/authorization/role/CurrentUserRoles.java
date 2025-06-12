package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record CurrentUserRoles(List<String> memberOf, boolean isDataBaseSuper, OreSiUser user) {

    public static final CurrentUserRoles EMPTY = new CurrentUserRoles(null, false, null);


    public CurrentUserRoles(
            final List<String> memberOf,
            final boolean isDataBaseSuper,
            final OreSiUser user) {
        this.memberOf = memberOf == null ? List.of() : List.copyOf(memberOf);
        this.isDataBaseSuper = isDataBaseSuper;
        this.user = user;
    }

    public static CurrentUserRoles empty() {
        return EMPTY;
    }

    public String userLogin() {
        return Optional.ofNullable(user).map(OreSiUser::getLogin).orElse(null);
    }

    public UUID userId() {
        return Optional.ofNullable(user).map(OreSiUser::getId).orElse(null);
    }

    public boolean isOpenAdomAdmin() {
        return memberOf().contains(OreSiRole.openAdomAdmin().getAsSqlRole());
    }

    public boolean isApplicationCreator() {
        return Optional.ofNullable(memberOf())
                .map(roles -> roles.stream()
                        .anyMatch(List.of(
                                OreSiRole.applicationCreator().getAsSqlRole(),
                                OreSiRole.openAdomAdmin().getAsSqlRole()
                        )::contains)
                )
                .orElse(false);
    }

    public CurrentUserRoles withUSer(OreSiUser user) {
        return new CurrentUserRoles(memberOf(), isDataBaseSuper(), user);
    }

    public boolean applicationManagerOf(Application application) {
        return memberOf().contains(OreSiRole.applicationManagerOf(application).getAsSqlRole());
    }

    public boolean userManagerOf(Application application) {
        return memberOf().contains(OreSiRole.userManagerOf(application).getAsSqlRole());
    }

    public Map<String, List<String>> applicationRoles() {
        return memberOf().stream()
                .map(Pattern.compile("(.*)_(applicationManager|userManager|reader|writer)")::matcher)
                .filter(Matcher::matches)
                .collect(Collectors.groupingBy(
                        m -> m.group(1),
                        Collectors.mapping(m -> m.group(2), Collectors.toList())
                ));
    }

    public List<String> applicationRoles(UUID applicationid) {
        return applicationRoles().get(applicationid.toString());
    }
}