package fr.inra.oresing.domain.data.deposit.context.column;

import java.util.List;

public record ContextHeader(int columnIndex, String columnHeader, List<String> headersForRow) {
    public ContextHeader(String columnHeader, List<String> headersForRow) {
        this(headersForRow.indexOf(columnHeader), columnHeader, headersForRow);
    }
}