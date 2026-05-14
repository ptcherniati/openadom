package fr.inra.oresing.domain.application.configuration;

public record ChartDescription() {

    public static final String SQL_EXPRESSION = "";

    public String toSQL() {
        return SQL_EXPRESSION;
    }
}