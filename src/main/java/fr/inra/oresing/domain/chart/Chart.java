package fr.inra.oresing.domain.chart;
public record Chart (
        String value,
        String aggregationComponent,
        String unitComponent,
        String standardDeviationComponent,
        String gap,
        String title
){
        public static String VAR_SQL_TEMPLATE = """
                (
                \t   Array['%1$s'],-- aggrégation
                \t   Array['%2$s'], -- value
                \t   '%3$s',-- dataname
                \t   '%4$s'::interval -- gap
                   )
                """;
        public static String VAR_SQL_DEFAULT_TEMPLATE = """
                 (
                \t   '%s' -- dataname
                   )
                """;


    public static String toSQL(String dataName) {
        String sql = String.format(
                VAR_SQL_DEFAULT_TEMPLATE,
                dataName
        );
        return sql;
    }

    public String toSQL(String componentName, String dataName) {
        String sql = String.format(
                VAR_SQL_TEMPLATE,
                aggregationComponent(),
                value(),
                dataName,
                gap() == null ? "0" : gap
        );
        return sql;
    }

}