package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MultiYaml {
    byte[] parseConfigurationBytes(MultipartFile file) throws IOException {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(file.getInputStream());
        ZipEntry zipEntry = zis.getNextEntry();
        YAMLMapper mapper = new YAMLMapper();
        Map<String, Object> configuration = new HashMap<>();
        configuration.putAll(Map.of("version",0));
        while (zipEntry != null) {
            if (!zipEntry.isDirectory()) {
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                final Map<String, Object> o = mapper.readValue(byteArrayOutputStream.toByteArray(), Map.class);
                addObjectToConfiguration(configuration, o, zipEntry.getName());
                byteArrayOutputStream.close();
            }
            zipEntry = zis.getNextEntry();

        }
        zis.closeEntry();
        zis.close();
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mapper.writeValue(byteArrayOutputStream, configuration);
        final byte[] bytes = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        return bytes;
    }

    private void addObjectToConfiguration(Map<String, Object> configuration, Map<String, Object> o, String name) {
        final String[] pathes = name.split("/");
        Map<String, Object> currentMap = configuration;
        for (int i = 0; i < pathes.length; i++) {
            final String path = pathes[i].replaceAll("\\..*", "");
            if ("configuration".equals(path)) {
                continue;
            }
            currentMap = (Map<String, Object>) currentMap.computeIfAbsent(path, k -> new HashMap<String, Object>());
        }
        for (Map.Entry<String, Object> mapEntry : o.entrySet()) {
            if(currentMap.containsKey(mapEntry.getKey()) && (mapEntry.getValue() instanceof Map) &&( currentMap.get(mapEntry.getKey()) instanceof Map)){
                ((Map<String, Object>) currentMap.get(mapEntry.getKey())).putAll((Map<? extends String, ?>) mapEntry.getValue());
            }else{
                currentMap.put(mapEntry.getKey(), mapEntry.getValue());
            }
        }
    }
}