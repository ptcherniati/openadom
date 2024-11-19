package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.file.FileBomResolver;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MultiYaml {
    static InputStream parseConfigurationBytes(final MultipartFile file) throws IOException {
        final byte[] buffer = new byte[1024];
        final YAMLMapper mapper = new YAMLMapper();
        final Map<String, Object> configuration = new HashMap<>(Map.of("version", 0));
        try (ZipInputStream zis = new ZipInputStream(FileBomResolver.of(file.getInputStream()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        byteArrayOutputStream.write(buffer, 0, len);
                    }
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    Map<String, Object> o = mapper.readValue(byteArrayOutputStream.toByteArray(), Map.class);
                    addObjectToConfiguration(configuration, o, zipEntry.getName());
                    byteArrayOutputStream.close();
                }
                zipEntry = zis.getNextEntry();

            }
            zis.closeEntry();
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mapper.writeValue(byteArrayOutputStream, configuration);
        byteArrayOutputStream.close();
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    private static void addObjectToConfiguration(final Map<String, Object> configuration, final Map<String, Object> o, final String name) {
        String[] pathes = name.split("/");
        Map<String, Object> currentMap = configuration;
        for (int i = 0; i < pathes.length; i++) {
            String path = pathes[i].replaceAll("\\..*", "");
            if ("configuration".equals(path)) {
                continue;
            }
            currentMap = (Map<String, Object>) currentMap.computeIfAbsent(path, k -> new HashMap<String, Object>());
        }
        for (final Map.Entry<String, Object> mapEntry : o.entrySet()) {
            if (currentMap.containsKey(mapEntry.getKey()) && (mapEntry.getValue() instanceof Map) && (currentMap.get(mapEntry.getKey()) instanceof Map)) {
                ((Map<String, Object>) currentMap.get(mapEntry.getKey())).putAll((Map<? extends String, ?>) mapEntry.getValue());
            } else {
                currentMap.put(mapEntry.getKey(), mapEntry.getValue());
            }
        }
    }
}