package fr.inra.oresing.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.IOException;
import java.util.LinkedHashMap;

@Getter
@Setter
@ToString
public class Configuration {

    public static Configuration read(byte[] file) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        Configuration result = mapper.readValue(file, Configuration.class);
        return result;
    }

    public static Configuration fromJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Configuration result = mapper.readValue(json, Configuration.class);
        return result;
    }

    private LinkedHashMap<String, ReferenceDescription> references;
    private LinkedHashMap<String, DatasetDescription> dataset;

    @Getter
    @Setter
    @ToString
    static public class ReferenceDescription {
        private int lineToSkip = 0;
        private char separator = ';';
        private char quote = '"';
        private LinkedHashMap<String, ColumnDescription> columns;
    }

    @Getter
    @Setter
    @ToString
    static public class DatasetDescription {
        private int lineToSkip = 0;
        private char separator = ';';
        private char quote = '"';
        private LinkedHashMap<String, ColumnDescription> references;
        private LinkedHashMap<String, DataDescription> data;
    }

    @Getter
    @Setter
    @ToString
    static public class ColumnDescription {
        private String linkedTo;
        private Type type;
        private String pattern;
    }

    @Getter
    @Setter
    @ToString
    static public class DataDescription {
        private Type type;
        private String pattern;
        /** par defaut si vide il faut utiliser le meme nom que la data pour la colonne */
        private LinkedHashMap<String, ColumnDescription> dataColumns;
        private LinkedHashMap<String, ColumnDescription> accuracyColumns;
    }
}
