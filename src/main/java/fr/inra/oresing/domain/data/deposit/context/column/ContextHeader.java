package fr.inra.oresing.domain.data.deposit.context.column;

import com.google.common.collect.ImmutableList;

public record ContextHeader(int columnIndex, String columnHeader, ImmutableList<String> headersForRow) {
    public ContextHeader(String columnHeader, ImmutableList<String> headersForRow) {
        this(headersForRow.indexOf(columnHeader), columnHeader,headersForRow);
    }
}
