package fr.inra.oresing.domain.authorization.privilegeassessor;


import com.google.common.base.Strings;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.AuthenticationServiceImpl;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.user.file.UserRepository;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record PrivilegeAssessorDomainForNotConnectedUser<P extends PrivilegeSystemDomainEnum>(
        AuthenticationServiceImpl authenticationService,
        UserRepository userRepository,
        P domain
) implements PrivilegeAssessorDomain {

    public LoginAdminResult forLoginPassword(String login, String password) throws AuthenticationFailure {
        return authenticationService().login(login, password);
    }

    public NotConnectedUser forUpdateUser(CreateUserRequest createUserRequest) throws AuthenticationFailure {
        final String login = createUserRequest.getLogin();
        final String password = createUserRequest.getPassword();
        final String email = createUserRequest.getEmail();
        final String verificationKey = createUserRequest.getVerificationKey();
        final String charte = createUserRequest.getCharte();
        if (!Strings.isNullOrEmpty(login) && !Strings.isNullOrEmpty(password)) {
            final LoginAdminResult loginAdminResult = authenticationService().checkLoginPassword(login, password);
            final OreSiUser user = userRepository.findById(loginAdminResult.id());
            if (Strings.isNullOrEmpty(verificationKey)) {
                return new NotConnectedAuthentifiedActiveUser(user, createUserRequest);
            } else if (OreSiUser.OreSiUserStates.active == OreSiUser.OreSiUserStates.valueOf(loginAdminResult.state())) {
                return new NotConnectedAuthentifiedActiveUser(user, createUserRequest);
            } else {
                return new NotConnectedAuthentifiedIdleUser(user, createUserRequest);
            }
        } else if (!Strings.isNullOrEmpty(login) && !Strings.isNullOrEmpty(email)) {
            final Optional<OreSiUser> loginResult =
                    userRepository.findByLoginAndEmail(login, email);
            if (loginResult.isEmpty()) {
                throw new AuthenticationFailure(
                        AuthenticationFailure.INVALID_ACCOUNT,
                        new LoginAdminResult(null,
                                login,
                                email,
                                "",
                                null,
                                Set.of(),
                                Map.of()
                        ));
            }
            if (!Strings.isNullOrEmpty(charte)) {
                return new NotConnectedAuthentifiedActiveUserNotSignedCharte(loginResult.get(), createUserRequest, charte);
            } else if (!Strings.isNullOrEmpty(verificationKey)) {
                return new NotConnectedAuthentifiedMissingPasswordUser(loginResult.get(), createUserRequest);
            } else {
                return new NotConnectedAuthentifiedPendingUser(loginResult.get());
            }
        }
        return new NotConnectedUnauthentifiedUser(createUserRequest);
    }

    public NotConnectedUnauthentifiedUserForCreate forCreateUser() {
        return new NotConnectedUnauthentifiedUserForCreate();
    }
}