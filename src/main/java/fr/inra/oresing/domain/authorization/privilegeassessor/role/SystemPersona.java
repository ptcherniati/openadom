package fr.inra.oresing.domain.authorization.privilegeassessor.role;

/**
 *
 ### Système SystemPersona (authentification)
 ├── **{@link ApplicationCreator}**
 │   ├── {@link ApplicationCreatorUser}
 │   └── {@link OpenAdomAdmin}
 └── **{@link ConnectedUser}** (record)
 */
public sealed interface SystemPersona permits ApplicationCreator, ConnectedUser {
}