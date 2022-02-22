package fr.inra.oresing.rest;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class ApplicationResult {
    String id;
    String name;
    String title;
    String comment;
    InternationalizationMap internationalization;
    Map<String, Reference> references;
    Map<String, DataType> dataTypes;

    @Value
    public static class Reference {
        String id;
        String label;
        Set<String> children;
        Map<String, Column> columns;

        @Value
        public static class Column {
            String id;
            String title;
            boolean key;
            String linkedTo;
        }

        @Value
        public static class ReferenceUUIDAndDisplay {
            String display;
            UUID uuid;
            Map<String, String> values;
        }
    }

    @Value
    public static class DataType {
        String id;
        String label;
        Map<String, Variable> variables;
        Map<String, String> repository;

        @Value
        public static class Variable {
            String id;
            String label;
            Map<String, Component> components;
            Chart chartDescription;

            @Value
            public static class Component {
                String id;
                String label;
            }

            @Value
            public static class Chart {
                String value;
                String unit;
                String gap;
                String standardDeviation;
                VariableComponentKey aggregation;
            }
        }
    }
}