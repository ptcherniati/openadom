package fr.inra.oresing.rest.usecases.storage.versioning;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable payload of a {@link PublishToggleUseCase} workflow .
 *
 * <p>Carries the desired published flag value and enough context for
 * the audit log and the event listeners . Flows through the cascade
 * pipeline as the single record emitted by {@code Sources.single}.
 *
 * @param fileId          target binary file id
 * @param published       desired published state
 * @param userId          caller user id ( for audit log )
 * @param userLogin       caller login ( denormalised for audit log )
 * @param applicationName owning application name
 * @param dataName        data type / reference name
 * @param fileName        binary file display name ( for dashboard )
 * @param timestamp       request timestamp
 *
 * @author R.YAHIAOUI
 */
public record PublishTogglePayload(
        UUID    fileId,
        boolean published,
        UUID    userId,
        String  userLogin,
        String  applicationName,
        String  dataName,
        String  fileName,
        Instant timestamp
) {}
