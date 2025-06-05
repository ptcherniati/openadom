package fr.inra.oresing.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import org.apache.commons.io.file.AccumulatorPathVisitor;
import org.apache.commons.io.file.Counters;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Cette classe n’est pas utile en soi, elle a vocation à être portée en Groovy pour
 * devenir le script qui est inclus dans le kit de téléversement.
 */
// à ajouter en tête du fichier Groovy
//
//@Grab(group = "commons-io", module = "commons-io", version = "2.8.0")
//@Grab(group = "org.apache.httpcomponents.client5", module = "httpclient5", version = "5.2.1")
public class Client {
    private static final String DEFAULT_PARAMS = """
                {
                               "fileid":null,
                               "topublish":true
                            }
            """;

    public static void main(String[] args) throws IOException {
        new Client().run();
    }

    private static void logError(String string) {
        System.err.println(string);
    }

    private static void log(String message) {
        System.out.println(message);
    }

    public void run() throws IOException {

        ClientConfiguration clientConfiguration = readConfiguration();

        String applicationName = clientConfiguration.applicationName();
        URI instanceUrl = clientConfiguration.instanceUrl();

        String login;
        String password;
        new Scanner(System.in);
        login = "poussin";
        password = "xxxx";

        CookieStore cookieStore = new BasicCookieStore();
        UriFactory uriFactory = new UriFactory(instanceUrl, applicationName);

        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build()) {

            ClassicHttpRequest loginRequest = ClassicRequestBuilder.post()
                    .setUri(uriFactory.forLogin())
                    .addParameter("login", login)
                    .addParameter("password", password)
                    .build();

            httpclient.execute(loginRequest, response -> {
                switch (response.getCode()) {
                    case HttpURLConnection.HTTP_OK -> {
                        EntityUtils.consume(response.getEntity());
                        switch (cookieStore.getCookies().size()) {
                            case 0 -> fail("authentification échouée : pas de cookie d’authentification retourné");
                            case 1 -> {
                                if (cookieStore.getCookies().getFirst().getName().equals("si-ore-jwt")) {
                                    log("authentification OK");
                                } else {
                                    fail("authentification échouée : pas de cookie d’authentification retourné");
                                }
                            }
                            default ->
                                    fail(cookieStore.getCookies().size() + " cookies retournés à l’authentification");
                        }
                    }
                    case HttpURLConnection.HTTP_UNAUTHORIZED ->
                            fail("authentification échouée : identifiant ou mot de passe faux ?");
                    default ->
                            fail("%d en code de retour HTTP inattendu à l’authentification".formatted(response.getCode()));
                }
                return null;
            });

            ClassicHttpRequest getApplicationDataTypesRequest = ClassicRequestBuilder
                    .get(uriFactory.forApplicationDataTypes(applicationName))
                    .build();

            List<String> data = httpclient.execute(getApplicationDataTypesRequest, response ->
                    switch (response.getCode()) {
                        case HttpURLConnection.HTTP_OK -> parseJsonInResponseBody(
                                response,
                                new TypeReference<>() {
                                }
                        );
                        case HttpURLConnection.HTTP_UNAUTHORIZED ->
                                throw new IllegalStateException("pas l’autorisation pour l’application " + applicationName);
                        default ->
                                throw new IllegalStateException("%d en code de retour HTTP inattendu".formatted(response.getCode()));
                    }
            );

            log("référentiels à importer :" + System.lineSeparator()
                    + String.join(System.lineSeparator(), data));

           /* ClassicHttpRequest getApplicationDataTypesRequest = ClassicRequestBuilder
                    .get(uriFactory.forApplicationDataTypes(applicationName))
                    .build();

            List<String> dataTypes = httpclient.execute(getApplicationDataTypesRequest, response ->
                    switch (response.getCode()) {
                        case HttpURLConnection.HTTP_OK ->
                                parseJsonInResponseBody(
                                        response,
                                        new TypeReference<List<String>>() {}
                                );
                        case HttpURLConnection.HTTP_UNAUTHORIZED ->
                                throw new IllegalStateException("pas l’autorisation pour l’application " + applicationName);
                        default ->
                                throw new IllegalStateException("%d en code de retour HTTP inattendu".formatted(response.getCode()));
                    });

            log("données expérimentales à importer :" + System.lineSeparator()
                    + String.join(System.lineSeparator(), dataTypes));*/

            List<Command> commands = newCommands(data);

            for (Command command : commands) {
                log("va traiter " + command.getDescription());
                ClassicHttpRequest request = command.getRequest(uriFactory);
                httpclient.execute(request, command);
                log("a traité " + command.getDescription());
            }
        } catch (IOException e) {
            fail("Erreur réseau HTTP : " + e.getMessage());
        }
    }

    private ClientConfiguration readConfiguration() throws IOException {
        File configurationFile = new File("openAdom-client-configuration.json");
        return new ObjectMapper()
                .readValue(
                        configurationFile,
                        ClientConfiguration.class
                );
    }

    private List<Command> newCommands(List<String> data/*, List<String> dataTypes*/) {
        List<Command> dataCommands = data.stream()
                .flatMap(refType -> getDataCommands(refType).stream())
                .toList();
        /*List<Command> dataCommands = dataTypes.stream()
                .flatMap(dataType -> getUploadDataCommands(dataType).stream())
                .toList();*/
        //commands.addAll(dataCommands);
        return new LinkedList<>(dataCommands);
    }

    private List<Command> getDataCommands(String dataName) {
        File dir = new File(dataName);
        File[] csvFiles = dir.listFiles((dir1, name) -> name.endsWith(".csv"));
        List<Command> commands = new LinkedList<>();
        if (csvFiles == null) {
            return List.of();
        }
        Arrays.stream(csvFiles).forEach(refFile -> {
            if (refFile.exists()) {
                Set<Path> csvFilePathsInDirectory;
                if (refFile.isFile()) {
                    csvFilePathsInDirectory = Collections.singleton(refFile.toPath());
                } else if (refFile.isDirectory()) {
                    csvFilePathsInDirectory = findCsvFilePathsInDirectory(refFile.toPath());
                } else {
                    throw new IllegalStateException("ne comprend pas de quel type est " + refFile);
                }
                commands.addAll(csvFilePathsInDirectory.stream()
                        .map(path -> newUploadDataCommand(dataName, path.toFile()))
                        .toList()
                );
            } else {
                logError("le fichier %s n’existe pas, on ignore l’import du référentiel %s".formatted(refFile, dataName));
                commands.addAll(Collections.emptyList());
            }
        });
        return commands;

    }

    private Command newUploadDataCommand(String dataName, File dataFile) {
        return new Command() {

            public static final String MESSAGE_PARAMS = "messageParams";

            @Override
            public String getDescription() {
                return "Téléversement de %s pour alimenter le référentiel %s".formatted(dataFile, dataName);
            }

            @Override
            public ClassicHttpRequest getRequest(UriFactory uriFactory) {
                HttpPost httpPost = new HttpPost(uriFactory.forUploadingData(dataName));
                FileBody refFileBody = new FileBody(dataFile);
                HttpEntity reqEntity = MultipartEntityBuilder.create()
                        .addPart("file", refFileBody)
                        .addTextBody("params", DEFAULT_PARAMS)
                        .build();
                httpPost.setEntity(reqEntity);
                return httpPost;
            }

            private List<Map<String, Object>> parseJsonInResponseBodyForErrorMessagesAndParams(ClassicHttpResponse response) {
                try (InputStream inputStream = response.getEntity().getContent()) {
                    List<Map<String, Object>> responseBody = new ObjectMapper().readValue(inputStream, new TypeReference<>() {
                    });

                    return responseBody.stream()
                            .map(record -> {
                                Map<String, Object> resultMap = new HashMap<>();
                                resultMap.put("message", ((Map<String, Object>) record.get("validationCheckResult")).get("message").toString());
                                resultMap.put(MESSAGE_PARAMS, ((Map<String, Object>) record.get("validationCheckResult")).get(MESSAGE_PARAMS));
                                return resultMap;
                            })
                            .toList();
                } catch (IOException e) {
                    throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                }
            }

            @Override
            public Void handleResponse(ClassicHttpResponse response) {
                switch (response.getCode()) {
                    case HttpURLConnection.HTTP_CREATED -> {
                        String message = "import de " + dataFile + " terminé";
                        log(message);
                    }
                    case HttpURLConnection.HTTP_BAD_REQUEST -> {
                        List<Map<String, Object>> errorMessagesAndParams = parseJsonInResponseBodyForErrorMessagesAndParams(response);
                        logError("Une erreur  est survenue dans le traitement");
                        errorMessagesAndParams
                                .forEach(map -> {
                                    logError("->>>>>>>>>>");
                                    logError(map.get("message").toString());
                                    ((Map<String, Object>) map.get(MESSAGE_PARAMS)).forEach((key, value) -> logError("%s : %s".formatted(key, value)));
                                });
                    }
                    default -> fail(
                            "%d en code de retour HTTP inattendu à l’import du référentiel %s avec le fichier %s"
                                    .formatted(response.getCode(), dataName, dataFile)
                    );
                }
                return null;
            }
        };
    }

    private SortedSet<Path> findCsvFilePathsInDirectory(Path directory) {
        try {
            AccumulatorPathVisitor accumulatorPathVisitor = new AccumulatorPathVisitor(Counters.longPathCounters());
            Files.walkFileTree(directory, accumulatorPathVisitor);
            SortedSet<Path> csvFilePathsInDirectory = accumulatorPathVisitor.getFileList().stream()
                    .filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .collect(Collectors.toCollection(TreeSet::new));
            return Collections.unmodifiableSortedSet(csvFilePathsInDirectory);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }
    }

    private void fail(String message) {
        logError(message);
        System.exit(1);
    }

    private <T> T parseJsonInResponseBody(ClassicHttpResponse response, TypeReference<T> valueTypeRef) {
        try (InputStream inputStream = response.getEntity().getContent()) {
            return new ObjectMapper().readValue(inputStream, valueTypeRef);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
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
     * Les routes disponibles sur le serveur.
     */
    record UriFactory(URI instanceUrl, String applicationName) {

        private URI newUri(String endpoint) {
            try {
                return new URI("%s/api/v1/%s".formatted(instanceUrl, endpoint));
            } catch (URISyntaxException e) {
                throw new OreSiTechnicalException("ne devrait pas arriver", e);
            }
        }

        public URI forLogin() {
            return newUri("login");
        }

        public URI forUploadingData(String dataType) {
            String endpoint = "applications/%s/data/%s".formatted(applicationName, dataType);
            return newUri(endpoint);
        }

        /*public URI forApplicationReferenceTypes(String applicationName) {
            String endpoint = "applications/%s/references".formatted(applicationName);
            return newUri(endpoint);
        }*/

        public URI forApplicationDataTypes(String applicationName) {
            String endpoint = "applications/%s/data".formatted(applicationName);
            return newUri(endpoint);
        }
    }

    /**
     * Le contenu du fichier de configuration du client.
     *
     * @param instanceUrl     l’adresse du serveur au format "<a href="http://hote:port">...</a>"
     * @param applicationName le nom de l’application
     */
    private record ClientConfiguration(URI instanceUrl, String applicationName) {

    }
}