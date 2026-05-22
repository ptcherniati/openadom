# Ticket : Découpler l'envoi d'e-mail du thread HTTP (anti-blocage SMTP)

## Contexte

Lors du diagnostic du self-deadlock `compensateNow` (2026-05-22, cf
`mem:TICKET_TRANSACTION_BLOCK_DETECTION`), une autre source de blocage
potentiel a été identifiée : **l'envoi d'e-mail synchrone**.

`EmailService` appelle `mailSender.send(mailMessage)` **5 fois en synchrone**
sur le thread courant (typiquement un thread `heavy-*` portant la requête
HTTP) :

| Méthode | Ligne | Quand |
|---|---|---|
| `sendValidationOnAccountEmail` (privé) | `EmailService.java:288` | création compte |
| `sendNewMailEmail` (privé) | `EmailService.java:316` | changement email |
| `sendUpoadSuccessMail` | `EmailService.java:355` | upload OK ( **after-commit** d'un createData ) |
| `sendUpoadErrorsMail` | `EmailService.java:379` | **upload KO ( catch block de createData )** ← chemin du bug 2026-05-22 |
| `RightsRequestNotificationService.send...` | `RightsRequestNotificationService.java:434` | demande de droits |

**Risque** : si le serveur SMTP est lent / down / accepte la connexion mais
ne répond pas (cas pathologique typique : firewall qui drop , reverse-proxy
en surcharge ) , `mailSender.send()` peut bloquer plusieurs minutes par
défaut. **Aucun timeout SMTP n'est configuré actuellement** :

```bash
$ grep -r "smtp.connectiontimeout\|smtp\.timeout\|smtp\.writetimeout" src/main/
# 0 résultats
```

Par défaut , JavaMail utilise `-1` ( = `Socket.setSoTimeout(0)` = pas de
timeout JVM ) : la connexion repose uniquement sur le TCP keepalive OS qui
peut durer **plus de 2 heures** ( `tcp_keepalive_time` Linux = 7200s ) .

Concrètement , aujourd'hui un mail bloqué = un thread `heavy-*` bloqué =
saturation rapide du pool `heavyExecutorService` ( parallelism 4 par défaut ) =
toutes les requêtes d'import suivantes en file d'attente jusqu'au prochain
restart ou GC magique . L'utilisateur voit un timeout HTTP générique sans
indication de la cause .

## Hypothèses du fix actuel ( filets de sécurité 2026-05-22 )

| Filet | Couvre-t-il l'e-mail ? |
|---|---|
| `openadom.http.streaming.timeout` ( 6h prod , 3 min test ) | ✅ Le thread `heavy-*` finira par lever `TimeoutException` → `dumpRelevantThreads()` → on saura |
| Profil `testmail` qui mock `JavaMailSender` | ✅ En test uniquement |
| Postgres `lock_timeout` 15s en test | ❌ Pas concerné ( SMTP ≠ DB ) |

→ En production , l'e-mail synchrone est protégé **uniquement** par le timeout
HTTP global ( 6h ! ) . Ce n'est pas un filet , c'est un drap mortuaire .

## Objectif

Que **l'envoi d'e-mail ne bloque jamais** la pipeline de réponse HTTP , même
si le serveur SMTP est down ou répond en 30 minutes .

## Solutions ( par effort croissant )

### Option A — Timeouts SMTP côté JavaMail ( minimum vital , 30 min )

Ajouter dans `application.properties` :
```properties
# Timeouts SMTP ( en ms ) - obligatoires pour ne pas bloquer
# indefiniment le thread appelant si le serveur SMTP est down/lent .
spring.mail.properties.mail.smtp.connectiontimeout=${SPRING_MAIL_SMTP_CONNECTIONTIMEOUT:5000}
spring.mail.properties.mail.smtp.timeout=${SPRING_MAIL_SMTP_TIMEOUT:10000}
spring.mail.properties.mail.smtp.writetimeout=${SPRING_MAIL_SMTP_WRITETIMEOUT:10000}
```

- `connectiontimeout=5s` : connexion TCP au serveur SMTP
- `timeout=10s` : socket read ( server responses )
- `writetimeout=10s` : socket write ( body upload )

Total worst-case : ~25s par mail . Toujours synchrone , mais borné .

**Pro** : 1-liner config , zéro changement de code . **Contra** : 25s bloqué
reste très long sur le chemin HTTP ; en cas de panne SMTP prolongée , la
saturation du pool peut quand même survenir .

### Option B — `@Async` Spring sur les méthodes `EmailService.send*` ( 2-3h )

Ajouter `@EnableAsync` + executor dédié :
```java
@Configuration
@EnableAsync
public class MailAsyncConfig {
    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("mail-");
        // CRITIQUE : reject policy = CALLER_RUNS pour fail-fast quand
        // SMTP est down et que la queue se remplit . Sinon , 200 mails
        // accumules dans la JVM heap .
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
```

```java
@Async("mailExecutor")
public void sendUpoadErrorsMail(...) {
    // ... existing code ...
    mailSender.send(mailMessage);
}
```

→ Le thread HTTP appelant rend la main immédiatement . Le mail part dans le
pool `mail-*` , bloque le pool `mail-*` au pire si SMTP est down ( mais
pool isolé du pool `heavy-*` ) .

**Pro** : isolation de pool , thread HTTP libre . **Contra** :
- en cas de redémarrage JVM avec mails en queue → mails perdus
- l'utilisateur voit la réponse HTTP "succès" alors que le mail n'est
  pas garanti
- si SMTP reste down longtemps , la queue se remplit ( 200 max ) puis
  `CallerRunsPolicy` ramène le blocage sur le caller ( mais avec
  Option A ce caller est borné à 25s , donc OK )

À combiner **impérativement** avec Option A .

### Option C — Pattern outbox SMTP ( 1-2 j )

Une nouvelle table `oa_audit.mail_outbox` :
```sql
CREATE TABLE oa_audit.mail_outbox (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    to_address      varchar(256) NOT NULL,
    from_address    varchar(256) NOT NULL,
    subject         varchar(512) NOT NULL,
    body            text         NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING',
        -- PENDING , SENDING , SENT , FAILED
    attempts        int          NOT NULL DEFAULT 0,
    last_error      text,
    next_attempt_at timestamptz,
    sent_at         timestamptz
);
CREATE INDEX ON oa_audit.mail_outbox (status, next_attempt_at)
    WHERE status IN ('PENDING','FAILED');
```

`EmailService.sendUpoadErrorsMail()` devient un `INSERT` dans `mail_outbox`
( ~1ms , borné ) . Un worker `@Scheduled` ( ou `LISTEN/NOTIFY` ) consomme
les `PENDING` , tente `mailSender.send()` avec retry exponentiel
( 1min , 5min , 30min ) , marque `SENT` ou `FAILED` au bout de N tentatives .

**Pro** :
- thread HTTP totalement decouple ( zero risque de blocage )
- mails persistants ( survie au restart JVM )
- retry automatique sur panne SMTP transitoire
- visibilité / admin via une page "Outbox" pour les SUPER_ADMIN
- pattern déjà utilisé dans le projet ( cf `CompensationLogService` :
  même structure outbox PENDING/FAILED + sweeper background )

**Contra** :
- migration Flyway + 1 nouveau service `MailOutboxService` + 1 sweeper
- transactionnalité : il faut écrire dans `mail_outbox` **dans la même tx**
  que l'INSERT métier ( garantit qu'on n'envoie pas un mail "succès" si
  la tx métier rollback ) → couplage léger , à documenter

À combiner avec Option A pour les retries .

### Option D — Try/catch + fallback log ( quick win , 15 min )

```java
public void sendUpoadErrorsMail(...) {
    try {
        SimpleMailMessage mailMessage = ...;
        mailSender.send(mailMessage);
    } catch (MailException ex) {
        log.warn("Mail upload errors failed ( best-effort ) : to={} subject={} cause={}",
                currentUser.getEmail(), subject, ex.getMessage());
        // Pas de rethrow : le mail est best-effort , la reponse HTTP doit partir
    }
}
```

Sans timeout SMTP ( Option A ) , le `send()` peut quand même bloquer ; donc
ce patch seul ne suffit pas , il faut Option A en plus . Avec Option A il
devient utile pour empêcher une `MailSendException` à 25s de masquer la
vraie erreur métier ( `InvalidDatasetContentException` qui doit remonter ) .

## Recommandation

**Plan d'attaque échelonné** :

1. **MUST** ( Option A ) : timeouts SMTP en config → 30 min , 0 risque .
   Couvre 99% du risque de blocage prolonge .
2. **SHOULD** ( Option D ) : try/catch fail-safe sur tous les sites de send → 1h .
   Garantit que les mails échoués n'écrasent jamais l'erreur métier.
3. **NICE TO HAVE** ( Option C ) : outbox pour les mails non urgents
   ( upload errors / success , notifications droits ) → 1-2 j .
   Les mails synchrones critiques ( création compte , changement email )
   peuvent rester syncrones avec Option A+D .

Option B ( `@Async` ) **non recommandée seule** : compromis intermédiaire
sans persistence , peu de gain par rapport à C qui est plus robuste .

## Critères d'acceptation ( phase 1 = A+D )

1. Avec un serveur SMTP simulé qui accepte la connexion mais ne répond
   jamais ( `nc -l -p 2525` ) , `sendUpoadErrorsMail()` retourne en
   `< 20s` ( au lieu de bloquer indefiniment ) .
2. Le test ne fail pas : un `WARN` est logue avec la cause , la pipeline
   HTTP continue normalement et la `InvalidDatasetContentException` métier
   remonte au client .
3. Les properties `mail.smtp.*timeout` sont surchargeables via env vars
   ( `SPRING_MAIL_SMTP_CONNECTIONTIMEOUT` , etc. ) pour les ops .

## Critères d'acceptation ( phase 2 = C , optionnelle )

4. Un mail envoyé apparait dans `oa_audit.mail_outbox` avec status `PENDING` ,
   puis `SENT` après le sweeper next run ( ou `FAILED` après N retries ) .
5. Un restart JVM en plein traitement n'entraine pas de mail perdu : le row
   reste `PENDING` , le sweeper le rattrape au boot suivant .
6. Endpoint admin `GET /api/v1/admin/mail-outbox?status=FAILED` pour visualiser
   les échecs ( SUPER_ADMIN uniquement ) , avec retry manuel via `POST` .

## Tests à ajouter

- `EmailServiceTimeoutTest` ( Testcontainers + un container `mailpit` lent ) :
  vérifie qu'un SMTP qui ne répond pas en 30s déclenche `MailSendException`
  borné par les timeouts JavaMail .
- Re-jouer le scenario `monsoere "ajout d'un fichier invalide"` avec un SMTP
  down dans un profil `testmail-broken` ( JavaMailSender configure pour
  pointer vers `localhost:1` ) : la reponse HTTP `400 Bad Request` doit
  arriver en `< 30s` ( au lieu de bloquer indefiniment ) .

## Estimation

| Phase | Travail | Effort |
|---|---|---|
| A + D | Properties + try/catch + tests | 1/2 j |
| C | Migration Flyway + service + sweeper + endpoint admin + tests | 1-2 j |
| Doc | Section "Mail outbox" dans `ARCHITECTURE_DEPOT_FICHIER.md` | 2h |

**Total recommande ( A+D seul )** : 1/2 j .
**Total complet ( A+D+C )** : 2-3 j .

## Références

- Fichier source : `src/main/java/fr/inra/oresing/mail/EmailService.java`
- Properties Spring Mail : `src/main/resources/application.properties:102-109`
- Profil mock SMTP : `OreSiNg.java:216` ( `@Profile("testmail")` )
- Bug 2026-05-22 dont l'investigation a leve ce risque latent :
  cf `TICKET_TRANSACTION_BLOCK_DETECTION.md`
- Pattern outbox déjà en place dans le code : `CompensationLogService`
  ( `monitoring/compensation/` ) → bonne référence pour Option C