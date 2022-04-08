package fr.inra.oresing.rest;

import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.persistence.DataRow;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
public class GetDataResult {
    Set<String> variables;
    List<DataRow> rows;
    Long totalRows;
    Map<String, Map<String, LineChecker>> checkedFormatVariableComponents;
}