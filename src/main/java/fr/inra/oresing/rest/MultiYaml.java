package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.data.DataFile;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MultiYaml {

    /** Classe utilitaire — constructeur privé. */
    private MultiYaml() {
    }

    /** Nombre maximal d'entrées dans le ZIP. */
    private static final int MAX_ENTRIES = 500;

    /** Taille du buffer de copie bornée. */
    private static final int BUFFER_SIZE = 8192;

    /**
     * Analyse un ZIP multi-YAML en contrôlant la consommation de ressources.
     * <p>
     * Protections anti-zip-bomb :
     * <ul>
     *   <li>taille décompressée totale limitée à {@code maxBytesAllowed}
     *       (= {@code spring.servlet.multipart.max-file-size})</li>
     *   <li>nombre d'entrées limité à {@value #MAX_ENTRIES}</li>
     *   <li>copie bornée octet par octet via {@link #copyBounded}</li>
     * </ul>
     *
     * @param maxBytesAllowed taille maximale décompressée en octets, alignée sur
     *                        {@code spring.servlet.multipart.max-file-size}
     */
    @SuppressWarnings("java:S5042")
    public static InputStream parseConfigurationBytes(final DataFile file, final long maxBytesAllowed) throws IOException {
        final YAMLMapper mapper = new YAMLMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, Object> configuration = new HashMap<>();
        long totalBytesRead = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.inputStream()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException(
                            "Le ZIP contient trop d'entrées (max " + MAX_ENTRIES + "). "
                            + "Le contenu doit être équivalent à un seul fichier YAML.");
                }
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName().replaceFirst("multiyaml/", "");
                    long entryBytes = processZipEntry(zis, entryName, mapper, configuration,
                            maxBytesAllowed - totalBytesRead);
                    totalBytesRead += entryBytes;
                }
            }
        }

        return serializeConfiguration(mapper, configuration);
    }

    /**
     * Lit l'entrée ZIP courante en limitant le nombre d'octets lus à {@code remainingBytes}.
     *
     * @return le nombre d'octets réellement lus pour cette entrée
     * @throws IOException si la limite est dépassée ou en cas d'erreur I/O
     */
    private static long processZipEntry(ZipInputStream zis, String entryName, YAMLMapper mapper,
                                        Map<String, Object> configuration, long remainingBytes) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            long bytesRead = copyBounded(zis, baos, remainingBytes);
            Map<String, Object> entryContent = mapper.readValue(baos.toByteArray(),
                    new TypeReference<>() {});

            if (entryName.equals("configuration.yaml")) {
                configuration.putAll(entryContent);
            } else {
                addObjectToConfiguration(configuration, entryContent, entryName);
            }
            return bytesRead;
        }
    }

    /**
     * Copie {@code in} vers {@code out} en levant une exception si {@code maxBytes} est dépassé.
     * Le flux {@code in} (ZipInputStream) n'est pas fermé — c'est l'appelant qui gère le cycle de vie.
     */
    private static long copyBounded(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException(
                        "Le contenu décompressé dépasse la taille maximale autorisée ("
                        + maxBytes + " octets). "
                        + "Le ZIP ne doit pas excéder la limite d'upload configurée.");
            }
            out.write(buffer, 0, read);
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static void addObjectToConfiguration(Map<String, Object> configuration, Map<String, Object> object, String path) {
        String[] pathParts = path.split("/");
        Map<String, Object> current = configuration;

        for (int i = 0; i < pathParts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(pathParts[i], ignored -> new HashMap<>());
        }

        String fileName = pathParts[pathParts.length - 1];
        String key = FilenameUtils.removeExtension(fileName);
        if (current.containsKey(key)) {
            ((Map<String, Object>) current.get(key)).putAll(object);
        } else {
            current.put(key, object);
        }
    }

    private static InputStream serializeConfiguration(YAMLMapper mapper, Map<String, Object> configuration) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            mapper.writeValue(baos, configuration);
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }

}