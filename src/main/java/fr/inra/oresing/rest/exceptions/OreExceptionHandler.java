package fr.inra.oresing.rest.exceptions;

import com.google.common.base.Throwables;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResultRest;
import fr.inra.oresing.domain.exceptions.AuthenticationFailure;
import fr.inra.oresing.domain.exceptions.MailServiceUnavailableException;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadBinaryFileDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestControllerAdvice
@Slf4j
public class OreExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String KEY_MESSAGE = "message";

    /**
     * Defaillance du service de messagerie ( SMTP injoignable , auth refusee ,
     * timeout ) . 503 Service Unavailable + body code stable consommable
     * cote frontend pour un toast i18n . Le frontend NE redirige PAS vers
     * /login ( pas une perte d'authentification ) - cf Fetcher.ts qui
     * ne deconnecte que sur 401 .
     */
    @ExceptionHandler(MailServiceUnavailableException.class)
    public ResponseEntity<String> handleMailServiceUnavailable(final MailServiceUnavailableException ex) {
        log.warn("Mail service unavailable : {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(MailServiceUnavailableException.CODE);
    }

    // Ajoutez cette méthode pour les erreurs Spring Security
    @ExceptionHandler(AccessDeniedException.class)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse
                .builder(ex, HttpStatus.UNAUTHORIZED, ex.getMessage())
                .title("Accès refusé")
                .detail(ex.getMessage())
                .property("code", "SEC-403")
                .property("timestamp", Instant.now())
                .build();
    }

    @ExceptionHandler(ValidationError.class)
    public ResponseEntity<ValidationError> handle(final ValidationError eee) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(eee);
    }

    @ExceptionHandler(DisconnectedException.class)
    public ResponseEntity handle(final DisconnectedException disconnectedException) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(disconnectedException);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ExpiredJwtException> handle(final ExpiredJwtException expiredJwtException) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(expiredJwtException);
    }

    // #470 - Retour HTTP 401 propre sur token JWT expiré ou invalide.
    // JWTExtractor traduit ExpiredJwtException en BadCredentialsException
    // ( cf. JWTExtractor.getRequestClientFromJwt ) , qui n'avait aucun
    // gestionnaire ici : la réponse HTTP était imprévisible ( typiquement
    // 500 avec "expired JWT" dans le body ) , obligeant le frontend à
    // détecter l'expiration par string-matching fragile.
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handle(final BadCredentialsException ex) {
        String code = ex.getCause() instanceof ExpiredJwtException ? "TOKEN_EXPIRED" : "TOKEN_INVALID";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", code, KEY_MESSAGE, String.valueOf(ex.getMessage())));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(final AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "TOKEN_INVALID", KEY_MESSAGE, String.valueOf(ex.getMessage())));
    }

    @ExceptionHandler(SiOreIllegalArgumentException.class)
    public ResponseEntity<SiOreIllegalArgumentException> handle(final SiOreIllegalArgumentException eee) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(eee);
    }

    /**
     * Reject 409 Conflict lorsqu'une operation lifecycle ( PUBLISH / UNPUBLISH /
     * DELETE_FILE ) est deja en cours sur le meme fileId . Le corps de la
     * reponse contient le code {@code WORKFLOW_ALREADY_IN_PROGRESS} + les
     * details de l'operation active ( type , correlationId , login auteur )
     * pour que le frontend affiche une popup explicite a l'utilisateur ( ex
     * "Un PUBLISH est deja en cours par jdoe , veuillez patienter" ) plutot
     * qu'un message d'erreur generique .
     */
    @ExceptionHandler(fr.inra.oresing.rest.usecases.storage.versioning.WorkflowAlreadyInProgressException.class)
    public ResponseEntity<Map<String, Object>> handle(
            final fr.inra.oresing.rest.usecases.storage.versioning.WorkflowAlreadyInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code",                 "WORKFLOW_ALREADY_IN_PROGRESS",
                KEY_MESSAGE,              ex.getMessage(),
                "activeCorrelationId",  String.valueOf(ex.activeCorrelationId()),
                "activeWorkflowType",   String.valueOf(ex.activeWorkflowType()),
                "activeUserLogin",      ex.activeUserLogin() == null ? "" : ex.activeUserLogin(),
                "fileId",               String.valueOf(ex.fileId()),
                "timestamp",            Instant.now().toString()));
    }


    /**
     * Rate limit dépassé sur l'endpoint {@code GET /filters} . Map vers
     * HTTP 429 Too Many Requests avec payload structuré pour permettre
     * au frontend d'afficher un message clair " trop de requêtes ,
     * veuillez patienter " plutôt qu'une erreur générique .
     */
    @ExceptionHandler(fr.inra.oresing.rest.data.FilterListRateLimiter.FilterListRateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handle(
            final fr.inra.oresing.rest.data.FilterListRateLimiter.FilterListRateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "code",          "FILTER_LIST_RATE_LIMIT_EXCEEDED",
                "message",       ex.getMessage(),
                "userId",        String.valueOf(ex.getUserId()),
                "currentCount",  ex.getCurrentCount(),
                "maxAllowed",    ex.getMaxAllowed(),
                "windowSeconds", ex.getWindowSeconds(),
                "timestamp",     Instant.now().toString()));
    }


    @ExceptionHandler(AuthenticationFailure.class)
    public ResponseEntity<String> handle(final AuthenticationFailure eee) {
        return switch (eee.getMessage()) {
            case "INACTIVE_ACCOUNT" -> {
                final HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.set("Id", Optional.ofNullable(eee.getParams())
                        .map(m -> m.get("id"))
                        .map(Object::toString)
                        .orElse(""));
                responseHeaders.set("Login", Optional.ofNullable(eee.getParams()).map(m -> (String) m.get(("login"))).orElse(""));
                responseHeaders.set("Email", Optional.ofNullable(eee.getParams()).map(m -> (String) m.get(("email"))).orElse(""));
                responseHeaders.set("Result__State", Optional.ofNullable(eee.getParams()).map(m -> (String) m.get(("state"))).orElse(""));
                yield ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .headers(responseHeaders)
                        .body(eee.getMessage());
            }
            case "EXISTING_LOGIN" -> ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(eee.getMessage());
            // EXISTING_EMAIL = email cible deja pris par un autre utilisateur
            // ( creation OU changement d'email ) . 422 metier , pas 412
            // ( reserve a EXISTING_LOGIN historique ) , pour que le frontend
            // affiche un toast rouge i18n sans tomber dans le flow logout .
            case "EXISTING_EMAIL" -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(eee.getMessage());
            case "BAD_LOGIN_PASSWORD" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee.getMessage());
            // BAD_CURRENT_PASSWORD = mdp actuel KO dans un flow d'update
            // ( utilisateur deja loggue ) . 422 metier pour eviter l'
            // auto-logout du frontend qui se declenche sur 401 .
            case "BAD_CURRENT_PASSWORD" -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(eee.getMessage());
            // BAD_LOGIN_OR_EMAIL_PASSWORD = email saisi inconnu en DB
            // ( typiquement flow forgot-password step 1 sur un email
            // inexistant ) . 401 plutot que default 403 pour que le
            // frontend swallow optimiste ( security-through-obscurity )
            // se declenche sans leak de l'existence du compte .
            case "BAD_LOGIN_OR_EMAIL_PASSWORD" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee.getMessage());
            case "BAD_PASSWORDS" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee.getMessage());
            // BAD_VALIDATION_KEY = erreur de validation metier ( cle saisie ne
            // match pas ) , PAS une perte d'authentification . 401 declenchait
            // un logout automatique cote Fetcher.ts ( cf bug "wrong key locks
            // out user" ) . 422 = Unprocessable Entity -> le frontend peut
            // afficher un toast + permettre retry sans deconnecter .
            case "BAD_VALIDATION_KEY" -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(eee.getMessage());
            // NO_PENDING_EMAIL_CHANGE = double-submit ou flow casse , meme
            // logique : 422 pour ne pas confondre avec auth invalide .
            case "NO_PENDING_EMAIL_CHANGE" -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(eee.getMessage());
            // EMAIL_UNCHANGED = phase 1 appelee sans changement effectif . Le
            // backend N'A PAS envoye de mail ; le frontend doit afficher un
            // toast rouge ( pas vert ) - garantit l'invariant "200 OK = mail
            // bien envoye" .
            case "EMAIL_UNCHANGED" -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(eee.getMessage());
            case "BAD_REQUEST" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee.getMessage());
            default -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(eee.getMessage());
        };
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ObjectError> exception(final WebExchangeBindException ex) {
        log.error("{}", ex.getLocalizedMessage(), ex);
        return ex.getAllErrors();
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<String> handle(final BadSqlGrammarException badSqlGrammarException) {
        final Throwable rootCause = Throwables.getRootCause(badSqlGrammarException);
        if (rootCause instanceof PSQLException && rootCause.getMessage().contains("permission denied")) {
            if (log.isTraceEnabled()) {
                log.trace("erreur de permission PostgreSQL", badSqlGrammarException);
            }
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(badSqlGrammarException.getMessage());
        }
        throw badSqlGrammarException;
    }

    @ExceptionHandler(NoSuchApplicationException.class)
    public ResponseEntity<String> handle(final NoSuchApplicationException noSuchApplicationException) {
        return ResponseEntity.badRequest().body(noSuchApplicationException.getMessage());
    }

    @ExceptionHandler(BadApplicationConfigurationException.class)
    public ResponseEntity<ConfigurationException> handle(final BadApplicationConfigurationException badApplicationConfigurationException) {
        return ResponseEntity.badRequest().body(badApplicationConfigurationException.getConfigurationException());
    }

    @ExceptionHandler(OreSiTechnicalException.class)
    public ResponseEntity<OreSiTechnicalException> handle(final OreSiTechnicalException oreSiTechnicalException) {
        if ("fr.inra.oresing.domain.authorization.privilegeassessor.exception"
                .equals(oreSiTechnicalException.getClass().getPackage().getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(oreSiTechnicalException);
        }
        log.error("Technical Exception not resolved", oreSiTechnicalException);
        return ResponseEntity.badRequest().body(oreSiTechnicalException);
    }

    @ExceptionHandler(InvalidDatasetContentException.class)
    public ResponseEntity<List<ValidationCheckResultRest>> handle(final InvalidDatasetContentException invalidDatasetContentException) {
        List<ValidationCheckResultRest> validations = invalidDatasetContentException.getErrors()
                .stream()
                .map(row -> {
                    long lineNumber = row.lineNumber();
                    return row.validationCheckResult().validationCheckResultToRest(row.lineNumber());
                })
                .toList();
        return ResponseEntity.badRequest().body(validations);
    }

    @ExceptionHandler(BadBinaryFileDatasetQuery.class)
    public ResponseEntity<String> handle(final BadBinaryFileDatasetQuery badBinaryFileDatasetQuery) {
        return ResponseEntity.badRequest().body(badBinaryFileDatasetQuery.getMessage());
    }

    @ExceptionHandler(BadDownloadDatasetQuery.class)
    public ResponseEntity<String> handle(final BadDownloadDatasetQuery badDownloadDatasetQuery) {
        return ResponseEntity.badRequest().body(badDownloadDatasetQuery.getMessage());
    }

    @ExceptionHandler(BadFileOrUUIDQuery.class)
    public ResponseEntity<String> handle(final BadFileOrUUIDQuery badFileOrUUIDQuery) {
        return ResponseEntity.badRequest().body(badFileOrUUIDQuery.getMessage());
    }
}