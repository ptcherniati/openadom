package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;

@Value
public class ApplicationResult {
    String id;
    String name;
    String title;
    Map<String, Reference> references;

    @Value
    public static class Reference {
        Map<String, Column> columns;

        @Value
        public static class Column {
            String title;
        }
    }
}
