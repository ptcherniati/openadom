package fr.inra.oresing.client

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.io.file.AccumulatorPathVisitor
import org.apache.commons.io.file.Counters
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.cookie.BasicCookieStore
import org.apache.hc.client5.http.entity.mime.FileBody
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpEntity
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@Grab(group = "commons-io", module = "commons-io", version = "2.8.0")
@Grab(group = "org.apache.httpcomponents.client5", module = "httpclient5", version = "5.2.1")

ClientConfiguration clientConfiguration = readConfiguration()

String applicationName = clientConfiguration.applicationName()
URI instanceUrl = clientConfiguration.instanceUrl()

String login
String password
boolean interactive = true
Scanner scanner = new Scanner(System.in)
if (interactive) {
    System.out.println("Veuillez saisir les informations de connexion à " + instanceUrl)
    System.out.print("identifiant : ")
    login = scanner.nextLine()
    System.out.print("mot de passe : ")
    password = scanner.nextLine()
} else {
    login = "poussin"
    password = "xxxx"
}

BasicCookieStore cookieStore = new BasicCookieStore()
UriFactory uriFactory = new UriFactory(instanceUrl, applicationName)
HttpClients.custom()
        .setDefaultCookieStore(cookieStore)
        .build()
        .withCloseable { httpclient ->

            ClassicHttpRequest loginRequest = ClassicRequestBuilder.post()
                    .setUri(uriFactory.forLogin())
                    .addParameter("login", login)
                    .addParameter("password", password)
                    .build()

            httpclient.execute(loginRequest, response -> {
                switch (response.getCode()) {
                    case HttpURLConnection.HTTP_OK -> {
                        EntityUtils.consume(response.getEntity())
                        switch (cookieStore.getCookies().size()) {
                            case 0 -> fail("authentification échouée : pas de cookie d’authentification retourné")
                            case 1 -> {
                                if (cookieStore.getCookies().get(0).getName().equals("si-ore-jwt")) {
                                    log("authentification OK")
                                } else {
                                    fail("authentification échouée : pas de cookie d’authentification retourné")
                                }
                            }
                            default -> fail("${cookieStore.getCookies().size()} cookies retournés à l’authentification")
                        }
                    }
                    case HttpURLConnection.HTTP_UNAUTHORIZED -> fail("authentification échouée : identifiant ou mot de passe faux ?")
                    default -> fail("${response.getCode()} en code de retour HTTP inattendu à l’authentification")
                }
                null
            })

            ClassicHttpRequest getApplicationDataTypesRequest = ClassicRequestBuilder
                    .get(uriFactory.forApplicationDataTypes(applicationName))
                    .build()

            List<String> data = httpclient.execute(getApplicationDataTypesRequest, { response ->
                switch (response.getCode()) {
                    case HttpURLConnection.HTTP_OK:
                        return parseJsonInResponseBody(response, new TypeReference<List<String>>() {})
                    case HttpURLConnection.HTTP_UNAUTHORIZED:
                        throw new IllegalStateException("pas l’autorisation pour l’application " + applicationName)
                    default:
                        throw new IllegalStateException("${response.getCode()} en code de retour HTTP inattendu")
                }
            })

            log("référentiels à importer :" + System.lineSeparator() + String.join(System.lineSeparator(), data))

            List<Command> commands = newCommands(data)

            if (interactive) {
                String plan = commands.stream()
                        .map(Command::getDescription)
                        .collect(Collectors.joining(System.lineSeparator()))
                log("Plan :")
                log(plan)
                System.out.print("est-ce que le plan convient ? [O/n]")
                String planIsOkString = scanner.nextLine()
                boolean planIsOk = Set.of("o", "oui", "").contains(planIsOkString.toLowerCase())
                if (!planIsOk) {
                    fail("Abandon")
                }
            }

            for (Command command : commands) {
                log("va traiter " + command.getDescription())
                ClassicHttpRequest request = command.getRequest(uriFactory)
                httpclient.execute(request, command)
                log("a traité " + command.getDescription())
            }
        }

ClientConfiguration readConfiguration() throws IOException {
    File configurationFile = new File("openAdom-client-configuration.json")
    ClientConfiguration clientConfiguration = new ObjectMapper()
            .readValue(
                    configurationFile,
                    ClientConfiguration.class
            )
    return clientConfiguration
}

List<Command> newCommands(List<String> data/*, List<String> dataTypes*/) {
    List<Command> dataCommands = data.stream()
            .flatMap(refType -> getDataCommands(refType).stream())
            .toList()

    List<Command> commands = new LinkedList<>()
    commands.addAll(dataCommands)

    return commands
}

List<Command> getUploadDataCommands(String dataType) {
    Path dataDirectoryForDataType = Path.of(dataType)
    List<Command> commands
    if (dataDirectoryForDataType.toFile().exists()) {
        if (dataDirectoryForDataType.toFile().isDirectory()) {
            SortedSet<Path> csvFilePaths = findCsvFilePathsInDirectory(dataDirectoryForDataType)
            commands = csvFilePaths.stream()
                    .map(Path::toFile)
                    .map(dataFile -> newUploadDataCommand(dataType, dataFile))
                    .toList()
        } else {
            logError("le répertoire " + dataDirectoryForDataType + " est un fichier mais il devrait être un dossier. On l’ignore.")
            commands = Collections.emptyList()
        }
    } else {
        log("le répertoire " + dataDirectoryForDataType + " n’existe pas. Pas de données à importer pour " + dataType)
        commands = Collections.emptyList()
    }
    return commands
}


List<Command> getDataCommands(String dataName) {
    File dir = new File(dataName)
    File[] csvFiles = dir.listFiles((dir1, name) -> name.endsWith(".csv"))
    List<Command> commands = new LinkedList<>()
    if (csvFiles == null) {
        return List.of()
    }
    Arrays.stream(csvFiles).forEach(refFile -> {
        if (refFile.exists()) {
            Set<Path> csvFilePathsInDirectory
            if (refFile.isFile()) {
                csvFilePathsInDirectory = Collections.singleton(refFile.toPath())
            } else if (refFile.isDirectory()) {
                csvFilePathsInDirectory = findCsvFilePathsInDirectory(refFile.toPath())
            } else {
                throw new IllegalStateException("ne comprend pas de quel type est " + refFile)
            }
            commands.addAll(csvFilePathsInDirectory.stream()
                    .map(path -> newUploadDataCommand(dataName, path.toFile()))
                    .toList()
            )
        } else {
            logError("le fichier %s n’existe pas, on ignore l’import du référentiel %s".formatted(refFile, dataName))
            commands.addAll(Collections.emptyList())
        }
    })
    return commands

}

Command newUploadDataCommand(String dataName, File dataFile) {
    return new Command() {
        @Override
        String getDescription() {
            return "Téléversement de %s pour alimenter le référentiel %s".formatted(dataFile, dataName)
        }

        @Override
        ClassicHttpRequest getRequest(UriFactory uriFactory) {
            HttpPost httpPost = new HttpPost(uriFactory.forUploadingData(dataName))
            FileBody refFileBody = new FileBody(dataFile)
            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("file", refFileBody)
                    .addTextBody("params", """
                {
                               "fileid":null,
                               "topublish":true
                            }
            """)
                    .build()
            httpPost.setEntity(reqEntity)
            return httpPost
        }

        List<Map<String, Object>> parseJsonInResponseBodyForErrorMessagesAndParams(ClassicHttpResponse response) {
            try (InputStream inputStream = response.getEntity().getContent()) {
                List<Map<String, Object>> responseBody = new ObjectMapper().readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {
                })

                return responseBody.stream()
                        .map(record -> {
                            Map<String, Object> resultMap = new HashMap<>()
                            resultMap.put("message", ((Map<String, Object>) record.get("validationCheckResult")).get("message").toString())
                            resultMap.put("messageParams", (Map<String, Object>) ((Map<String, Object>) record.get("validationCheckResult")).get("messageParams"))
                            return resultMap
                        })
                        .collect(Collectors.toList())
            } catch (IOException e) {
                throw new RuntimeException(e)
            }
        }

        @Override
        Void handleResponse(ClassicHttpResponse response) {
            switch (response.getCode()) {
                case HttpURLConnection.HTTP_CREATED:
                    String message = "import de ${dataFile} terminé"
                    log(message)
                    break
                case HttpURLConnection.HTTP_BAD_REQUEST:
                    List<Map<String, Object>> errorMessagesAndParams = parseJsonInResponseBodyForErrorMessagesAndParams(response)
                    logError("Une erreur  est survenue dans le traitement")
                    errorMessagesAndParams.each { map ->
                        logError("->>>>>>>>>>")
                        logError(map.get("message").toString())
                        ((Map<String, Object>) map.get("messageParams")).each { entry ->
                            logError("${entry.key} : ${entry.value}")
                        }
                    }
                    break
                default:
                    fail("${response.getCode()} en code de retour HTTP inattendu à l’import du référentiel ${dataName} avec le fichier ${dataFile}")
                    break
            }
            null
        }
    }
}

SortedSet<Path> findCsvFilePathsInDirectory(Path directory) {
    try {
        AccumulatorPathVisitor accumulatorPathVisitor = new AccumulatorPathVisitor(Counters.longPathCounters())
        Files.walkFileTree(directory, accumulatorPathVisitor)
        SortedSet<Path> csvFilePathsInDirectory = accumulatorPathVisitor.getFileList().stream()
                .filter(path -> path.getFileName().toString().endsWith(".csv"))
                .collect(Collectors.toCollection(TreeSet::new))
        return Collections.unmodifiableSortedSet(csvFilePathsInDirectory)
    } catch (IOException e) {
        throw new RuntimeException(e)
    }
}

void fail(String message) {
    logError(message)
    System.exit(1)
}

static void logError(String string) {
    System.err.println(string)
}

static void log(String message) {
    System.out.println(message)
}

<T> T parseJsonInResponseBody(ClassicHttpResponse response, TypeReference<T> valueTypeRef) {
    try (InputStream inputStream = response.getEntity().getContent()) {
        return new ObjectMapper().readValue(inputStream, valueTypeRef)
    } catch (IOException e) {
        throw new RuntimeException(e)
    }
}

record CsvRowValidationCheckResult(
        int lineNumber,
        ValidationCheckResult validationCheckResult
) {
}

record ValidationCheckResult(
        ValidationLevel level,
        ValidationMessage message,
        Map<String, Object> messageParams,
        String target
) {
}

enum ValidationLevel {
    SUCCESS, WARN, ERROR;
}

enum ValidationMessage {
    unexpectedHeaderColumn,
    headerColumnPatternNotMatching,
    unexpectedTokenCount,
    invalidHeaders,
    duplicatedHeaders,
    emptyHeader
}
/**
 * Les routes disponibles sur le serveur.
 */
record UriFactory(URI instanceUrl, String applicationName) {


    URI newUri(String endpoint) {
        try {
            return new URI("%s/api/v1/%s".formatted(instanceUrl, endpoint))
        } catch (URISyntaxException e) {
            throw new RuntimeException("ne devrait pas arriver", e)
        }
    }

    URI forLogin() {
        return newUri("login")
    }

    URI forUploadingData(String dataType) {
        String endpoint = "applications/%s/data/%s".formatted(applicationName, dataType)
        return newUri(endpoint)
    }

    /*public URI forApplicationReferenceTypes(String applicationName) {
        String endpoint = "applications/%s/references".formatted(applicationName);
        return newUri(endpoint);
    }*/

    URI forApplicationDataTypes(String applicationName) {
        String endpoint = "applications/%s/data".formatted(applicationName)
        return newUri(endpoint)
    }
}

/**
 * Une étape du téléversement soit un fichier à téléverser, une requête HTTP et comment traiter la réponse.
 */
interface Command extends HttpClientResponseHandler<Void> {

    String getDescription();

    ClassicHttpRequest getRequest(UriFactory uriFactory);
}

/**
 * Le contenu du fichier de configuration du client.
 *
 * @param instanceUrl l’adresse du serveur au format "http://hote:port"
 * @param applicationName le nom de l’application
 */
record ClientConfiguration(URI instanceUrl, String applicationName) {

}