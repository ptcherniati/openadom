package fr.inra.oresing.persistence;

import lombok.Value;

import java.util.Map;

@Value
public class DataRow {
    String rowId;
    Map<String, Map<String, String>> values;
}
