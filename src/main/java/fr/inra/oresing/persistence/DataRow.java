package fr.inra.oresing.persistence;

import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class DataRow {
    String rowId;
    Map<String, Map<String, String>> values;
    Map<String, Map<String, Set<UUID>>> refsLinkedTo;
    Long totalRows = -1L;
    Long rowNumber = -1L;
}