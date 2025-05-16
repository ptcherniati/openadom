package fr.inra.oresing.domain.chart;

public record Chart(
        String value,
        String aggregationComponent,
        String unitComponent,
        String standardDeviationComponent,
        String gap,
        String title
) {
    public static final String VAR_SQL_TEMPLATE = """
            (
            \t   Array['%1$s'],-- aggrégation
            \t   Array['%2$s'], -- value
            \t   '%3$s',-- dataname
            \t   '%4$s'::interval -- gap
               )
            """;
    public static final String VAR_SQL_DEFAULT_TEMPLATE = """
             (
            \t   '%s' -- dataname
               )
            """;


    public static String toSQL(String dataName) {
        return String.format(
                VAR_SQL_DEFAULT_TEMPLATE,
                dataName
        );
    }

    public String toSQL(String componentName, String dataName) {
        return String.format(
                VAR_SQL_TEMPLATE,
                this.aggregationComponent(),
                this.value(),
                dataName,
                this.gap() == null ? "0" : gap
        );
    }

}