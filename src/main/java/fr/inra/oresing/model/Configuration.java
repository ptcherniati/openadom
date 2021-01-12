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
        private int lineToSkipAfterHeader = 0;
        private char separator = ';';
        private char quote = '"';
        private LinkedHashMap<String, DataGroupDescription> dataGroups;
        private String timeScopeColumn;

        /**
         * @deprecated à supprimer, c'est pour la rétro-compatibilité avant la mise en place des groupes
         */
        @Deprecated
        public Map<String, ColumnDescription> getData() {
            LinkedHashMap<String, ColumnDescription> data = new LinkedHashMap<>();
            if (dataGroups != null) {
                for (DataGroupDescription value : dataGroups.values()) {
                    data.putAll(value.getData());
                }
            }
            return data;
        }
    }

    @Getter
    @Setter
    @ToString
    static public class ColumnDescription {
        private CheckerDescription checker;
        private LinkedHashMap<String, ColumnDescription> accuracy = new LinkedHashMap<>();
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
    static public class DataGroupDescription {
        private String label;
        private LinkedHashMap<String, ColumnDescription> data;
    }
}
