package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.data.DataFile;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MultiYaml {

    public static InputStream parseConfigurationBytes(final DataFile file) throws IOException {
        final YAMLMapper mapper = new YAMLMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, Object> configuration = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.inputStream()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName().replaceFirst("multiyaml/", "");
                    processZipEntry(zis, entryName, mapper, configuration);
                }
            }
        }

        return serializeConfiguration(mapper, configuration);
    }

    private static void processZipEntry(ZipInputStream zis, String entryName, YAMLMapper mapper, Map<String, Object> configuration) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            zis.transferTo(baos);
            Map<String, Object> entryContent = mapper.readValue(baos.toByteArray(), Map.class);

            if (entryName.equals("configuration.yaml")) {
                configuration.putAll(entryContent);
            } else {
                addObjectToConfiguration(configuration, entryContent, entryName);
            }
        }
    }

    private static void addObjectToConfiguration(Map<String, Object> configuration, Map<String, Object> object, String path) {
        String[] pathParts = path.split("/");
        Map<String, Object> current = configuration;

        for (int i = 0; i < pathParts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(pathParts[i], k -> new HashMap<>());
        }

        String fileName = pathParts[pathParts.length - 1];
        String key = FilenameUtils.removeExtension(fileName);
        if (current.containsKey(key)) {
            ((Map) current.get(key)).putAll(object);
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