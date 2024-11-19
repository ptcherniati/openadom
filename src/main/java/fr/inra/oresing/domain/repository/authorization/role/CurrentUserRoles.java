package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CurrentUserRoles (List<String> memberOf, boolean isDataBaseSuper, OreSiUser user){

    public static final CurrentUserRoles EMPTY = new CurrentUserRoles( null, false,null);

    public CurrentUserRoles(final String currentUser, final List<String> memberOf) {
        this(memberOf,false,null);
    }

    public String userLogin(){
        return Optional.ofNullable(user).map(OreSiUser::getLogin).orElse(null);
    }

    public UUID userId(){
        return Optional.ofNullable(user).map(OreSiUser::getId).orElse(null);
    }

    public boolean isOpenAdomAdmin(){
        return memberOf().contains(OreSiRole.openAdomAdmin().getAsSqlRole());
    }

    public static CurrentUserRoles empty(){
        return EMPTY;
    }
    public boolean isApplicationCreator(){
        return Optional.ofNullable(memberOf())
                .map(roles -> roles.stream()
                        .anyMatch(List.of(
                                OreSiRole.applicationCreator().getAsSqlRole(),
                                OreSiRole.openAdomAdmin().getAsSqlRole()
                                )::contains)
                )
                .orElse(false);
    }


    public CurrentUserRoles(
                            final List<String> memberOf,
                            final boolean isDataBaseSuper,
                            final OreSiUser user) {
        this.memberOf = memberOf==null?List.of():List.copyOf(memberOf);
        this.isDataBaseSuper = isDataBaseSuper;
        this.user = user;
    }
    public CurrentUserRoles withUSer(OreSiUser user){
        return new CurrentUserRoles(memberOf(), isDataBaseSuper(), user);
    }

    public Boolean applicationManagerOf(Application application) {
        return memberOf().contains(OreSiRole.applicationManagerOf(application).getAsSqlRole());
    }

    public boolean userManagerOf(Application application) {
        return memberOf().contains(OreSiRole.userManagerOf(application).getAsSqlRole());
    }
}