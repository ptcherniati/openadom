package fr.inra.oresing.rest;

import fr.inra.oresing.model.Internationalization;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Value
public class ApplicationResult {
    String id;
    String name;
    String title;
    private Internationalization internationalization;
    Map<String, Reference> references;
    Map<String, DataType> dataTypes;

    @Value
    public static class Reference {
        String id;
        String label;
        Map<String, String> internationalizationName;
        Map<String, Internationalization> internationalizedColumns;
        Set<String> children;
        Map<String, Column> columns;

        @Value
        public static class Column {
            String id;
            String title;
            boolean key;
            String linkedTo;
        }
    }

    @Value
    public static class DataType {
        String id;
        String label;
        Map<String, String> internationalizationName;
        Map<String, Variable> variables;
        Map<String, String> repository;

        @Value
        public static class Variable {
            String id;
            String label;
            Map<String, Component> components;

            @Value
            public static class Component {
                String id;
                String label;
            }
        }
    }
}
