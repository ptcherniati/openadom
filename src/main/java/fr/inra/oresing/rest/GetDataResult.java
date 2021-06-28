package fr.inra.oresing.rest;

import fr.inra.oresing.persistence.DataRow;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
public class GetDataResult {
    Set<String> variables;
    List<DataRow> rows;
}
