package fr.inra.oresing.model;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@ToString
public class Configuration {

    public static Configuration read(byte[] file) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        Configuration result = mapper.readValue(file, Configuration.class);
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
        private String timeScopeColumn;
    }

    @Getter
    @Setter
    @ToString
    static public class ColumnDescription {
        private CheckerDescription checker;
    }

    @Getter
    @Setter
    @ToString
    static public class CheckerDescription {
        private String name;
        private Map<String, String> params;
    }

    @Getter
    @Setter
    @ToString
    static public class DataDescription extends ColumnDescription {
        private LinkedHashMap<String, ColumnDescription> accuracy = new LinkedHashMap<>();
    }
}
