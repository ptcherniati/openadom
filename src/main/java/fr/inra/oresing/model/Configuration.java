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
import java.util.Set;
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
    private LinkedHashMap<String, DataTypeDescription> dataTypes;

    @Getter
    @Setter
    @ToString
    public static class ReferenceDescription {
        private int headerLine = 1;
        private int firstRowLine = 2;
        private char separator = ';';
        private LinkedHashMap<String, ColumnDescription> columns;
    }

    @Value
    public static class DataTypeDescription {
        FormatDescription format;
        LinkedHashMap<String, ColumnDescription> data;
        TreeMap<Integer, List<MigrationDescription>> migrations;
        AuthorizationDescription authorization;
    }

    @Value
    public static class AuthorizationDescription {
        VariableComponentKey timeScope;
        LinkedHashMap<String, DataGroupDescription> dataGroups;
    }

    @Getter
    @Setter
    @ToString
    public static class FormatDescription {
        private int headerLine = 1;
        private int firstRowLine = 2;
        private char separator = ';';
        private List<ColumnBindingDescription> columns;
        private List<RepeatedColumnBindingDescription> repeatedColumns;
    }

    @Value
    public static class ColumnBindingDescription {
        String header;
        VariableComponentKey boundTo;
    }

    @Value
    public static class RepeatedColumnBindingDescription {
        String headerPattern;
        String exportHeader;
        List<HeaderPatternToken> tokens;
        VariableComponentKey boundTo;
    }

    @Value
    public static class HeaderPatternToken {
        VariableComponentKey boundTo;
        String exportHeader;
    }

    @Value
    public static class ColumnDescription {
        LinkedHashMap<String, VariableComponentDescription> components;
    }

    @Value
    public static class VariableComponentDescription {
        CheckerDescription checker;
    }

    @Value
    public static class CheckerDescription {
        String name;
        Map<String, String> params;
    }

    @Value
    public static class DataGroupDescription {
        String label;
        Set<String> data;
    }

    @Value
    public static class ApplicationDescription {
        String name;
        int version;
    }

    @Value
    public static class MigrationDescription {
        MigrationStrategy strategy;
        String dataGroup;
        String variable;
        Map<String, AddVariableMigrationDescription> components;
    }

    @Value
    public static class AddVariableMigrationDescription {
        String defaultValue;
    }

    public enum MigrationStrategy {
        ADD_VARIABLE
    }
}
