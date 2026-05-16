package fr.inra.oresing.rest.usecases.storage.versioning;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Event Spring publie post-COMMIT de la phase 1 du
 * {@code PublishLifecycleService} . Trigger l'execution async de la
 * phase 2 ( cascade pipeline pour PUBLISH , DELETE SQL pour UNPUBLISH /
 * DELETE_FILE , recompute synthesis , cache invalidation , mail END ) .
 *
 * <p>Listener : {@code PublishLifecyclePhase2Listener} ( methode
 * annotee {@code @Async @TransactionalEventListener ( AFTER_COMMIT )} ) .
 *
 * <p>L'event capture tout le contexte necessaire pour que la phase 2
 * soit independante du HttpRequest d'origine ( SecurityContext / locale
 * propages explicitement ) .
 *
 * @param correlationId   id unique du workflow ( utilise dans workflow_log )
 * @param applicationName scope applicatif
 * @param fileId          binaryfile cible
 * @param dataName        datatype concerne ( pour cascade pipeline )
 * @param fileName        nom du fichier ( pour mails et logs )
 * @param action          {@link PublishLifecycleAction}
 * @param wasPublished    etat published AVANT la phase 1 ( utile pour
 *                        DELETE_FILE pour decider de DELETE rows ou non )
 * @param locale          locale du user pour les mails
 * @param userId          user declenchant l'action
 * @param userLogin       login user ( denormalise pour audit )
 * @param userEmail       email user ( capture en phase 1 , mail end peut
 *                        echouer si user supprime entre temps mais on a
 *                        l'adresse historique )
 * @param startTime       timestamp demarrage phase 1
 *
 * @author R.YAHIAOUI
 */
public record PublishLifecycleEvent(
        UUID                    correlationId,
        String                  applicationName,
        UUID                    fileId,
        String                  dataName,
        String                  fileName,
        PublishLifecycleAction  action,
        boolean                 wasPublished,
        Locale                  locale,
        UUID                    userId,
        String                  userLogin,
        String                  userEmail,
        Instant                 startTime
) {}
