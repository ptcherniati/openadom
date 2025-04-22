package fr.inra.oresing.rest.authentication;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.data.publication.StoreFile;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.security.AuthorizationFilter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Optional;

public class OreSiAuthenticationToken  extends AbstractAuthenticationToken implements Authentication {

    private final Object principal;
    private Object credentials;
    private ApplicationPersona applicationUser;
    private SystemPersona systemPersona;
    private String applicationName;
    private String dataName;
    private StoreFile storeFile;

    public OreSiAuthenticationToken(Object principal, String credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(true);
    }

    public boolean isLogin(){
        return getPath()
                .stream().anyMatch(s->s.endsWith("/login"));
    }

    public boolean isUpdate(){
        return getPath()
                .stream()
                .anyMatch(s->s.endsWith("/users")) &&
                getAuthorities().contains(AuthorizationFilter.ROLE_UNAUTHENTIFIED_UPDATE_USER);
    }

    public boolean isCreate(){
        return getPath()
                .stream()
                .anyMatch(s->s.endsWith("/users"))&&
                getAuthorities().contains(AuthorizationFilter.ROLE_UNAUTHENTIFIED_CREATE_USER);
    }

    private Optional<String> getPath() {
        return Optional.ofNullable(credentials)
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
    public static OreSiUserRequestClient getrequestClient(OreSiAuthenticationToken token) {
        return switch (token.getPrincipal()){
            case NotConnectedUnauthentifiedUserForCreate notConnected ->null;
            case NotConnectedAuthentifiedIdleUser notConnectedUser-> new OreSiUserRequestClient(
                    notConnectedUser.user().getId(),
                    OreSiUserRole.forUser(notConnectedUser.user())
            );
            case NotConnectedAuthentifiedPendingUser notConnectedUser-> new OreSiUserRequestClient(
                    notConnectedUser.user().getId(),
                    OreSiUserRole.forUser(notConnectedUser.user())
            );
            case NotConnectedAuthentifiedActiveUser notConnectedUser-> new OreSiUserRequestClient(
                    notConnectedUser.user().getId(),
                    OreSiUserRole.forUser(notConnectedUser.user())
            );
            case NotConnectedAuthentifiedMissingPasswordUser notConnectedUser-> new OreSiUserRequestClient(
                    notConnectedUser.oreSiUser().getId(),
                    OreSiUserRole.forUser(notConnectedUser.oreSiUser())
            );
            case OreSiUserRequestClient requestClient1-> requestClient1;
            default -> null;
        };
    }


    @Override
    public Object getCredentials() {
        return credentials;
    }

    public LoginAdminResult getLoginAdminResult(){
        return Optional.ofNullable(getPrincipal())
                .filter(LoginAdminResult.class::isInstance)
                .map(LoginAdminResult.class::cast)
                .orElse(null);
    }

    public NotConnectedUser getNotConnectedUser(){
        return Optional.ofNullable(getPrincipal())
                .filter(NotConnectedUser.class::isInstance)
                .map(NotConnectedUser.class::cast)
                .orElse(null);
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    public void setApplicationPersonna(ApplicationPersona applicationUser) {
        this.applicationUser=applicationUser;
    }

    public void setApplicationName(String applicationName){
        this.applicationName=applicationName;
    }

    public void setDataName(String dataName) {
        this.dataName=dataName;
    }

    public String  getApplicationName() {
        return applicationName;
    }

    public String  getDataName() {
        return dataName;
    }

    public void setSystemPersona(SystemPersona systemPersona) {
        this.systemPersona = systemPersona;
    }

    public StoreFile setStoreFile(StoreFile storeFile) {
        this.storeFile=storeFile;
        return storeFile;
    }

    public StoreFile getStoreFile() {
        return storeFile;
    }

    public ApplicationPersona getApplicationPersona() {
        return applicationUser;
    }

    public SystemPersona getSystemPersona() {
        return systemPersona;
    }
}

