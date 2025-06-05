package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;

/**
 * ## 🌳 Arbre d'héritage des interfaces
 *
 * ### ApplicationPersona (racine)
 * │
 * ├── **{@link ApplicationUser}**
 * │   │
 * │   ├── {@link ApplicationDataReaderUser}
 * │   │
 * │   └── **{@link ApplicationDataWriter}**
 * │       │
 * │       ├── {@link ApplicationDataDelete}
 * │       │   │
 * │       │   ├── {@link ApplicationAdminUser}
 * │       │   │
 * │       │   └── {@link ApplicationDeleteUser}
 * │       │   │
 * │       │   └── {@link ApplicationManagerUser}
 * │       │
 * │       ├── {@link ApplicationDepositWriterUser}
 * │       │
 * │       └── {@link ApplicationPublishWriterUser}
 * │
 * └── **{@link ApplicationManager}**
 *     │
 *     ├── {@link ApplicationAdminUser}
 *     │
 *     └── {@link ApplicationManagerUser}
 */
public sealed interface ApplicationPersona permits ApplicationUser, ApplicationManager {
    Application application();
}