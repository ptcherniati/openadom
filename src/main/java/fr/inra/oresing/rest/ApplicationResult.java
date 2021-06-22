package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;
import java.util.Set;

@Value
public class ApplicationResult {
    String id;
    String name;
    String title;
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
    }

    @Value
    public static class DataType {
        String id;
        String label;
        Map<String, Variable> variables;

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