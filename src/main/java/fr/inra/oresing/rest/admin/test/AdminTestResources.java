package fr.inra.oresing.rest.admin.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'administration pour tester la connectivite des integrations
 * externes : mail SMTP + FileSender REST .
 *
 * <p>Tous les endpoints sont sous garde {@code SYSTEM_OPENADOM_ADMIN} -
 * pas accessibles aux utilisateurs standards .
 *
 * <ul>
 *   <li>{@code GET  /api/v1/admin/test/mail/sample} :
 *       valeurs par defaut pre-remplissant le formulaire IHM .</li>
 *   <li>{@code POST /api/v1/admin/test/mail} :
 *       envoie un mail test , retourne resultat structure .</li>
 *   <li>{@code GET  /api/v1/admin/test/filesender/sample} :
 *       valeurs par defaut pre-remplissant le formulaire IHM .</li>
 *   <li>{@code POST /api/v1/admin/test/filesender} :
 *       genere un petit fichier + envoie via FileSender , retourne
 *       resultat structure ( success + downloadUrl ) .</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/admin/test", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin tests", description = "Tests admin des integrations SMTP et FileSender")
public class AdminTestResources {

    private final MailTestService mailTestService;
    private final FileSenderTestService fileSenderTestService;
    private final String mailFrom;
    private final String mailHost;
    private final String mailPort;
    private final String mailUsername;
    private final boolean mailStarttls;
    private final String fileSenderUser;
    private final String fileSenderBaseUrl;

    public AdminTestResources(MailTestService mailTestService,
                              FileSenderTestService fileSenderTestService,
                              @Value("${spring.mail.from:openadom@inrae.fr}") String mailFrom,
                              @Value("${spring.mail.host:}") String mailHost,
                              @Value("${spring.mail.port:}") String mailPort,
                              @Value("${spring.mail.username:}") String mailUsername,
                              @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean mailStarttls,
                              @Value("${filesender.username:}") String fileSenderUser,
                              @Value("${filesender.baseurl:}") String fileSenderBaseUrl) {
        this.mailTestService = mailTestService;
        this.fileSenderTestService = fileSenderTestService;
        this.mailFrom = mailFrom;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.mailUsername = mailUsername;
        this.mailStarttls = mailStarttls;
        this.fileSenderUser = fileSenderUser;
        this.fileSenderBaseUrl = fileSenderBaseUrl;
    }

    /**
     * Configuration effective des intégrations ( affichée en lecture seule dans
     * l'IHM admin pour confirmer la config ) : serveur / port / expéditeur /
     * compte SMTP + compte / URL FileSender .
     *
     * <p>Aucun secret n'est exposé : le mot de passe SMTP et la clé API
     * FileSender ne sont jamais lus côté backend ni renvoyés ; l'IHM affiche
     * une valeur masquée.
     */
    public record SenderInfo(String mailFrom,
                             String mailHost,
                             String mailPort,
                             String mailUsername,
                             boolean mailStarttls,
                             String fileSenderUser,
                             String fileSenderBaseUrl) {
    }

    @Operation(summary = "Configuration effective ( SMTP + FileSender , sans secret )")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping("/sender-info")
    public ResponseEntity<SenderInfo> senderInfo() {
        return ResponseEntity.ok(new SenderInfo(
                mailFrom, mailHost, mailPort, mailUsername, mailStarttls,
                fileSenderUser, fileSenderBaseUrl));
    }

    @Operation(summary = "Valeurs par defaut pour le test mail")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping("/mail/sample")
    public ResponseEntity<MailTestRequest> mailSample() {
        return ResponseEntity.ok(mailTestService.defaultSample());
    }

    @Operation(summary = "Envoyer un mail de test administrateur")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PostMapping(value = "/mail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MailTestResult> sendTestMail(@Valid @RequestBody MailTestRequest request) {
        return ResponseEntity.ok(mailTestService.send(request));
    }

    @Operation(summary = "Valeurs par defaut pour le test FileSender")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping("/filesender/sample")
    public ResponseEntity<FileSenderTestRequest> fileSenderSample() {
        return ResponseEntity.ok(fileSenderTestService.defaultSample());
    }

    @Operation(summary = "Envoyer un fichier test via FileSender")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PostMapping(value = "/filesender", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FileSenderTestResult> sendTestFile(@Valid @RequestBody FileSenderTestRequest request) {
        return ResponseEntity.ok(fileSenderTestService.send(request));
    }
}
