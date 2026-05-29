package fr.inra.oresing.rest.admin.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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

    public AdminTestResources(MailTestService mailTestService,
                              FileSenderTestService fileSenderTestService) {
        this.mailTestService = mailTestService;
        this.fileSenderTestService = fileSenderTestService;
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
    public ResponseEntity<MailTestResult> sendTestMail(@RequestBody MailTestRequest request) {
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
    public ResponseEntity<FileSenderTestResult> sendTestFile(@RequestBody FileSenderTestRequest request) {
        return ResponseEntity.ok(fileSenderTestService.send(request));
    }
}
