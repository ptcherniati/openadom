package fr.inra.oresing.rest.filesenderclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.DatatypeConverter;
import lombok.extern.java.Log;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author jrobert
 */
@Repository
@Log
public class FileSenderRepository implements fr.inra.oresing.rest.filesenderclient.FileRepository {
    public static final int DEFAULT_TRANSFER_DAYS_VALID = 2;
    private static final int UPLOAD_CHUNK_SIZE =
            5242880; // https://filesender.renater.fr/rest.php/info
    private static final int NUMBER_OF_DAYS_BEFORE_EXPIRATION = 15;
    private static CookieStore cookieStore;
  
  /*private static final Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();*/
    @Value("${filesender.baseurl}")
    private String BASE_URL;
    @Value("${filesender.username}")
    private String USERNAME;
    @Value("${filesender.apikey}")
    private String APIKEY;
    private int uploadChunkSize = -1;

    private static String sanitizeFileName(String input) {
        // Remplacer les caractères interdits par un tiret bas
        return input.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static byte[] concatByteArrays(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static String bytesToHex(byte[] hashBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @PostConstruct
    public void init() {
        cookieStore = new BasicCookieStore();
    }

    private int getUploadChunkSize() {
        try {
            JSONObject info = call("get", "/info", new HashMap<>(), null, null, new HashMap<>());
            return info.getInt("upload_chunk_size");
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String postTransfer(FileInfos fileInfos) throws Exception {
        // Obtenir l'URL de la ressource
        URL resource = fileInfos.fileName().toUri().toURL();

        // Convertir l'URL en Path
        Path path = Paths.get(resource.toURI());

        // Récupérer la taille du fichier
        long fileSize = Files.size(path);

        // Récupérer le type MIME du fichier
        String mimeType = Files.probeContentType(path);

        // Créer un flux de données à partir du fichier
        try (InputStream inputStream = Files.newInputStream(path)) {
            JSONObject fileInfo = new JSONObject();

            fileInfo.put("name",fileInfos.fileName().toString().replaceAll(".*/", "") );
            fileInfo.put("size", fileSize);
            fileInfo.put("mime_type", mimeType);

            JSONArray filesInfo = new JSONArray();
            filesInfo.put(fileInfo);

            JSONObject options = new JSONObject();
            options.put("get_a_link", true);

            JSONObject transfer = postTransfer(USERNAME, USERNAME, filesInfo, fileInfos.recipient(), fileInfos.subject(), fileInfos.message(), null, options);

            JSONObject file = transfer.getJSONArray("files").getJSONObject(0);
            byte[] buffer = new byte[getChunkSize()];
            //byte[] buffer = new byte[4];
            int bytesRead;
            long offset = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                putChunk(file, chunk, offset);
                offset += bytesRead;
            }

            fileComplete(file);
            transferComplete(transfer);
            return transfer.getJSONArray("recipients").getJSONObject(0).getString("download_url");
        }
    }

    private int getChunkSize() {
        if (uploadChunkSize < 0) {
            uploadChunkSize = getUploadChunkSize();
        }
        return uploadChunkSize;
    }

    private JSONObject postTransfer(String userId, String from, JSONArray files, String recipient, String subject, String message, Long expires, JSONObject options) throws Exception {
        if (expires == null) {
            expires = System.currentTimeMillis() / 1000 + (DEFAULT_TRANSFER_DAYS_VALID * 24 * 3600);
        }

        JSONObject content = new JSONObject();
        //content.put("from", from);
        content.put("from", "openadom@inrae.fr");
        content.put("files", files);
        content.put("recipients", new JSONArray(List.of(recipient))); // Ensure it's a JSON array
        content.put("subject", subject);
        content.put("message", message);
        content.put("expires", expires);
        content.put("aup_checked", true);
        options.put("email_me_on_expire", false);
        options.put("email_report_on_closing", false);
        options.put("email_daily_statistics", false);
        options.put("email_download_complete", false);
        options.put("email_me_copies", false);
        options.put("email_upload_complete", false);
        options.put("add_me_to_recipients", false);
        options.put("enable_recipient_email_download_complete", true);
        options.put("get_a_link", false);

        content.put("options", options);

        Map<String, String> params = new HashMap<>();
        params.put("remote_user", userId);

        return call("post", "/transfer", params, content, null, new HashMap<>());
    }

    private void putChunk(JSONObject file, byte[] chunk, long offset) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("key", file.getString("uid"));
        int fileSize = file.getInt("size");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/octet-stream");
        headers.put("X-Filesender-File-Size", String.valueOf(fileSize));
        headers.put("X-Filesender-Chunk-Offset", String.valueOf(offset));
        headers.put("X-Filesender-Chunk-Size", String.valueOf(chunk.length));

        // Generate signature for the chunk
        //String signature = generateSignature("PUT", "/file/" + file.getInt("id") + "/chunk/" + offset, params, chunk);

        // You might want to add the signature to your headers or params as per your server's logic
        int fileId = file.getInt("id");
        JSONObject puchunck = call("put", "/file/" + fileId + "/chunk/" + offset, params, null, chunk, headers);
        call("get", "/file/%d".formatted(fileId), new HashMap<>(), null, null, new HashMap<>());

    }

    private void fileComplete(JSONObject file) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("key", file.getString("uid"));

        JSONObject content = new JSONObject();
        content.put("complete", true);

        call("put", "/file/" + file.getInt("id"), params, content, null, new HashMap<>());
    }

    private void transferComplete(JSONObject transfer) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("key", transfer.getJSONArray("files").getJSONObject(0).getString("uid"));

        JSONObject content = new JSONObject();
        content.put("complete", true);

        call("put", "/transfer/" + transfer.getInt("id"), params, content, null, new HashMap<>());
    }

    public JSONObject call(String method, String path, Map<String, String> params, JSONObject content, byte[] rawContent, Map<String, String> headers) throws Exception {
        params.put("remote_user", USERNAME);
        params.put("timestamp", String.valueOf(Math.round(System.currentTimeMillis() / 1000.0)));

        String signature = generateSignature(method, path, params, content, rawContent);
        params.put("signature", signature);

        String url = BASE_URL + path + "?" + flattenParams(params);

        log.info("URL: %s%n Signature: %s".formatted(url, signature));

        try (CloseableHttpClient client = HttpClients.custom().setDefaultCookieStore(cookieStore).build()) {
            HttpUriRequest request;

            switch (method.toLowerCase()) {
                case "get" -> request = new HttpGet(url);
                case "post" -> request = new HttpPost(url);
                case "put" -> request = new HttpPut(url);
                case "delete" -> request = new HttpDelete(url);
                default -> throw new IllegalArgumentException("Méthode HTTP non supportée: " + method);
            }

            request.setHeader("Accept", "application/json");
            request.setHeader("Content-Type", headers.getOrDefault("Content-Type", "application/json"));

            // Set custom headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.setHeader(header.getKey(), header.getValue());
            }

            // Ajouter le contenu à la requête, si présent
            if (content != null) {
                ((HttpUriRequestBase) request).setEntity(new StringEntity(content.toString(), StandardCharsets.UTF_8));
            } else if (rawContent != null) {
                // Utilisation de ContentType pour spécifier le type des données
                ((HttpUriRequestBase) request).setEntity(new ByteArrayEntity(rawContent, ContentType.APPLICATION_OCTET_STREAM));
            }

            // Exécution de la requête
            try (CloseableHttpResponse response = client.execute(request)) {
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);

                int statusCode = response.getCode();  // Utilise response.getCode() au lieu de getStatusLine().getStatusCode()
                if (statusCode != 200 && (method.equals("post") && statusCode != 201)) {
                    throw new Exception("Erreur HTTP " + statusCode + ": " + responseBody);
                }

                if (responseBody.isEmpty()) {
                    throw new Exception("Erreur HTTP " + statusCode + " Réponse vide");
                }

                log.info("Response: " + responseBody);

                return responseBody.startsWith("{") ? new JSONObject(responseBody) : new JSONObject(String.format("{\"success\": %s}", responseBody));
            }
        }
    }

    private String flattenParams(Map<String, String> params) {
        List<String> flatParams = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            flatParams.add(entry.getKey() + "=" + entry.getValue());
        }
        Collections.sort(flatParams);
        return String.join("&", flatParams);
    }

    private String generateSignature(String method, String path, Map<String, String> params,
                                     JSONObject content, byte[] rawContent) throws NoSuchAlgorithmException, InvalidKeyException {
        var charset = StandardCharsets.UTF_8;

        /*HttpHeaders headers = new HttpHeaders();
        headers.setContentType(headerMediaType);*/
        byte[] signed;
        Map<String, String> copyOfParams = new TreeMap<>(params);
        String flatArguments = flattenParams(copyOfParams);


        /*String signedString = String.format("%s&%s%s%s%s",
                method,
                baseUrlWithoutProtocol,
                path,
                copyOfParams.isEmpty() ? "" : "?",
                flatParams);*/
        signed =
                (method
                        + "&"
                        + BASE_URL.replaceFirst("https://", "").replaceFirst("http://", "")
                        + path
                        + (copyOfParams.isEmpty() ? "" : "?")
                        + flatArguments)
                        .getBytes(charset);
        ObjectMapper mapper = new ObjectMapper();
        if (content != null) {
            signed = concatByteArrays(signed, "&".getBytes(charset));
            signed = concatByteArrays(signed, content.toString().getBytes());
        }
        if (rawContent != null) {
            signed = concatByteArrays(signed, "&".getBytes(charset));
            signed = concatByteArrays(signed, rawContent);
        }
        SecretKeySpec secretKeySpec = new SecretKeySpec(APIKEY.getBytes(charset), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(secretKeySpec);
        return DatatypeConverter.printHexBinary((mac.doFinal(signed))).toLowerCase();

        /*// Don't change the original map.
        Map<String, String> copyOfParams = new TreeMap<>(params);
        copyOfParams.put("timestamp", String.valueOf(Math.round(System.currentTimeMillis() / 1000.0)));

        String flatParams = String.join("&", copyOfParams.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList());

        String baseUrlWithoutProtocol = BASE_URL.replaceFirst("https?://", "");
        String signedString = String.format("%s&%s%s%s%s",
                method,
                baseUrlWithoutProtocol,
                path,
                copyOfParams.isEmpty() ? "" : "?",
                flatParams);

        if (content != null) {
            signedString += "&" + content.toString();
        } else if (rawContent != null) {
            signedString += "&" + new String(rawContent, StandardCharsets.UTF_8);
        }

        SecretKeySpec secretKey = new SecretKeySpec(apikey.getBytes(StandardCharsets.US_ASCII), "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(secretKey);

        byte[] hashBytes = mac.doFinal(signedString.getBytes(StandardCharsets.US_ASCII));
        return bytesToHex(hashBytes);*/
    }
}