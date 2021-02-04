package fr.inra.oresing.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Getter
@Setter
@ToString
public class Configuration {

    public static Configuration read(byte[] file) throws IOException {
        Preconditions.checkArgument(file.length > 0, "le fichier de configuration est vide");
        checkVersion(file);
        YAMLMapper mapper = new YAMLMapper();
        Configuration result = mapper.readValue(file, Configuration.class);
        return result;
    }

    private static void checkVersion(byte[] file) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Versioned versioned = mapper.readValue(file, Versioned.class);
        int actualVersion = versioned.getVersion();
        int expectedVersion = 0;
        Preconditions.checkArgument(actualVersion == expectedVersion, "Les fichiers YAML de version " + actualVersion + " ne sont pas géré, version attendue " + expectedVersion);
    }

    @Value
    private static class Versioned {
        int version;
    }

    private int version;
    private ApplicationDescription application;
    private LinkedHashMap<String, ReferenceDescription> references;
    private LinkedHashMap<String, DatasetDescription> dataset;

    @Getter
    @Setter
    @ToString
    static public class ReferenceDescription {
        private int headerLine = 1;
        private int firstRowLine = 2;
        private char separator = ';';
        private char quote = '"';
        private LinkedHashMap<String, ColumnDescription> columns;
    }

    @Getter
    @Setter
    @ToString
    static public class DatasetDescription {
        private FormatDescription format;
        private LinkedHashMap<String, DataGroupDescription> dataGroups;
        private TreeMap<Integer, List<MigrationDescription>> migrations;
        private VariableComponentReference timeScopeColumn;

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
    static public class FormatDescription {
        private int headerLine = 1;
        private int firstRowLine = 2;
        private char separator = ';';
        private List<ColumnBindingDescription> columns;
        private List<RepeatedColumnBindingDescription> repeatedColumns;

        public int getLineToSkip() {
            return getHeaderLine() - 1;
        }

        public int getLineToSkipAfterHeader() {
            return getFirstRowLine() - getHeaderLine() - 1;
        }
    }

    @Getter
    @Setter
    @ToString
    static public class ColumnBindingDescription {
        private String header;
        private VariableComponentReference reference;
    }

    @Getter
    @Setter
    @ToString
    static public class RepeatedColumnBindingDescription {
        private String headerPattern;
        private String exportHeader;
        private List<HeaderPatternToken> tokens;
        private VariableComponentReference reference;
    }

    @Getter
    @Setter
    @ToString
    static public class HeaderPatternToken {
        private VariableComponentReference reference;
        private String exportHeader;
    }

    @Getter
    @Setter
    @ToString
    static public class ColumnDescription {
        private LinkedHashMap<String, VariableComponentDescription> components;
    }

    @Getter
    @Setter
    @ToString
    static public class VariableComponentDescription {
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
    static public class DataGroupDescription {
        private String label;
        private LinkedHashMap<String, ColumnDescription> data;
    }

    @Value
    static public class ApplicationDescription {
        String name;
        int version;
    }

    @Value
    static public class MigrationDescription {
        MigrationStrategy strategy;
        String dataGroup;
        String variable;
        Map<String, AddVariableMigrationDescription> components;
    }

    @Value
    static public class AddVariableMigrationDescription {
        String defaultValue;
    }

    public enum MigrationStrategy {
        ADD_VARIABLE
    }
}
