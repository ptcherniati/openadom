package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.ViewStrategy;
import fr.inra.oresing.rest.exceptions.views.FieldNameTooLongForSqlFieldException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional()
public class RelationalService implements InitializingBean, DisposableBean {
    private static final String IDENTIFIER_PATTERN = "[a-z][a-z_0-9]{%d,%d}";
    @Value("${viewStrategy:DISABLED}")
    private ViewStrategy viewStrategy;

    public static Predicate<String> getIsValidIdentifierPattern(int min, int max) {
        int min1 = min > 0 ? min : 1;
        int max1 = max < 64 ? max : 63;
        return Pattern.compile(String.format(IDENTIFIER_PATTERN, min1 - 1, max1 - 1)).asMatchPredicate();
    }

    /**
     * No-op stub — la logique de construction des vues relationnelles est désormais
     * gérée par l'endpoint de normalisation ({@code /api/v1/applications/{nameOrId}/normalized}).
     */
    public void createViews(final String appName) {
        createViews(appName, viewStrategy);
    }

    /** @see #createViews(String) */
    public void createViews(final String appName, final ViewStrategy viewStrategy) {
    }

    public void dropViews(final String appName) {
        dropViews(appName, viewStrategy);
    }

    void dropViews(final String appName, final ViewStrategy viewStrategy) {
    }

    @Override
    public void afterPropertiesSet() {
        if (viewStrategy.isEnabled()) {
            if (log.isInfoEnabled()) {
                log.info("création des vues relationnelles pour les applications existantes");
            }
        }
    }

    @Override
    public void destroy() {
        log.info("""
                
                \u001B[32mextinction des feux good night\u001B[0m
                
                """);
    }

    sealed interface SQLVariable permits SQLVariableForData, SQLVariableForRefsLinkedTo {
        String name();

        List<SQLComponent> sqlComponents();

        default String getType() {
            return switch (this) {
                case final SQLVariableForData sqlVariableForData -> "datavalues";
                case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo -> "refslinkedto";
            };
        }

        default String getSelect(final SQLComponent component) {

            return switch (this) {
                case final SQLVariableForData sqlVariableForData -> """
                        "%1$s_datavalues_row"."%2$s"::%3$s "%4$s_%5$s\"""".formatted(
                        name().replace("'", "''"),
                        component.name(),
                        component.sqlType().cast(),
                        name().toLowerCase().replaceAll("[' ]", "_"),
                        component.name().toLowerCase().replaceAll("[' ]", "_")
                );
                case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo -> "refslinkedto";
            };
        }

        default String getSelectForRef(final SQLComponent component) {
            return switch (this) {
                case final SQLVariableForData sqlVariableForData -> """
                        "%1$s_refslinkedto_row"."%2$s"::%3$s "%4$s_%5$s_technicalid\"""".formatted(
                        name().replace("'", "''"),
                        component.name(),
                        "UUID[]",
                        name().toLowerCase().replaceAll("[' ]", "_"),
                        component.name().toLowerCase().replaceAll("[' ]", "_")
                );
                case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo -> "refslinkedto";
            };
        }

        default String buildLateralForWithVariable() {
            return """
                    LATERAL (
                                  SELECT "%1$s" as "%1$s_%2$s"
                                  FROM jsonb_to_record(%2$s) AS "%1$s"("%1$s" jsonb)
                                ) "%1$s_%2$s_row\""""
                    .formatted(
                            name().replace("'", "''"),
                            getType()
                    );
        }

        default String getSqlSelectForWithVariable() {
            return """
                    "%1$s_%2$s_row".*""".formatted(name().replace("'", "''"), getType());
        }

        default String buildLateralForSelect() {
            return """
                    LATERAL (
                           SELECT *
                           FROM jsonb_to_record("%1$s_%2$s")
                           AS "%1$s" %3$s
                          ) "%1$s_%2$s_row\""""
                    .formatted(
                            name().replace("'", "''"),
                            getType(),
                            sqlComponents()
                                    .stream()
                                    .map(sqlComponent -> switch (this) {
                                        case final SQLVariableForData sqlVariableForData ->
                                                sqlComponent.toRecordDefinition();
                                        case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo ->
                                                sqlComponent.toRecordDefinitionForRef();
                                    })
                                    .collect(Collectors.joining(
                                                    """
                                                            ,\s
                                                                          \s""",
                                                    """
                                                            (
                                                                          \s""",
                                                    """
                                                            )"""
                                            )
                                    )
                    );
        }
    }

    record ViewCreationCommand(SqlTable view, String sql) {

    }

    public static class IdentifierTest {
        private String identifier;

        private IdentifierTest(final String label) {
            super();
            identifier = Optional.ofNullable(label).orElse("");
            testlabelLength();
        }

        public static IdentifierTest forReference(final DataColumn reference) {
            return forStringIdentifier(reference.column());
        }

        public static boolean identifierForApplicationName(final String applicationName) {
            return getIsValidIdentifierPattern(2, 40).test(applicationName);
        }

        public static boolean identifierForObject(final String objectName) {
            return getIsValidIdentifierPattern(1, 50).test(objectName);
        }

        public static IdentifierTest forStringIdentifier(final String identifier) {
            Optional.ofNullable(identifier)
                    .map(IdentifierTest::new)
                    .orElseThrow(() -> new FieldNameTooLongForSqlFieldException(identifier));
            return new IdentifierTest(identifier);
        }

        @Deprecated
        private String toQuotedIdentifier() {
            return WithSqlIdentifier.escapeSqlIdentifier(identifier);
        }

        public IdentifierTest testlabelLength() {
            return Optional.of(identifier)
                    .filter(l -> l.length() <= 63)
                    .map(l -> this)
                    .orElseThrow(() -> new FieldNameTooLongForSqlFieldException(identifier));
        }

        public String testAndQuote() {
            final String quotedIdentifier = toQuotedIdentifier();
            return (quotedIdentifier.startsWith("\"") && quotedIdentifier.endsWith("\"")) ?
                    quotedIdentifier :
                    String.format("\"%s\"", quotedIdentifier);
        }

        public String testAndQuoteForRefsLinkedTo() {
            return testAndQuote().replaceAll("^\"", "\"refs_linked_to_");
        }

        public String testAndReturnIdentifier() {
            return identifier;
        }

        public IdentifierTest forHierachicalKey() {
            identifier = identifier + "_hierachicakkey";
            return testlabelLength();
        }

        public IdentifierTest forNaturalKey() {
            identifier = identifier + "_naturalkey";
            return testlabelLength();
        }

        public IdentifierTest forOneValueFromTheManyArray() {
            identifier = identifier + "_value";
            return testlabelLength();
        }

        public IdentifierTest forId() {
            identifier = identifier + "_id";
            return testlabelLength();
        }

        public IdentifierTest forDynamicReferenceHierachicakKey(final int count) {
            identifier = Pattern.compile(".{1," + 64 + "}")
                    .matcher(String.format("_%d%s_hierachicakKey", count, identifier))
                    .results()
                    .map(MatchResult::group)
                    .findFirst()
                    .orElse("");
            return this;
        }
    }

    record MultiplicitySelect(String select, String lateral) {
        public static final String SIMPLE_SELECT_PATTERN = """
                (%1$s.dataValues #>>  '{%2$s,%3$s}')::%4$s as %5$s""";
        public static final String SIMPLE_SELECT_PATTERN_WITH_NULL = """
                (NULLIF(%1$s.dataValues #>>  '{%2$s,%3$s}', ''))::%4$s as %5$s""";
        static String SELECT_PATTERN_MANY = """
                array_agg(%1$s.v) over(partition by my_data.rowid) %1$s""";
        static String SELECT_PATTERN_MANY_WITH_NULL = """
                array_agg(NULLIF(%1$s.v, '')) over(partition by my_data.rowid) %1$s""";
        static String SELECT_LATERAL_MANY = """ 
                LEFT JOIN LATERAL(select ((jsonb_array_elements(%1$s.dataValues #> '{%2$s, %3$s}')) #>> '{}')::%4$s as v, rowid ) as %5$s using(rowid)""";
        static String SELECT_PATTERN_ID = """ 
                %1$s.v::uuid %2$s""";
        static String SELECT_LATERAL_ID = """ 
                LEFT JOIN LATERAL (SELECT jsonb_array_elements_text(coalesce(%1$s.refsLinkedTo #> '{%2$s,%3$s}', '[null]'::jsonb)) as v, rowid) %4$s using(rowid)""";
    }

    record SQLComponent(String name, SqlViewPrimitiveType sqlType) {
        SQLComponent {
            if (!Strings.isNotEmpty(name)) {
                throw new IllegalArgumentException("name can't be null");
            }
            Objects.requireNonNull(sqlType, "sqlType can't be empty");
        }

        String toRecordDefinition() {
            final String cast = sqlType().cast().contains("DATE") ? "TEXT" : sqlType().cast();
            return """
                    "%1$s" %2$s%3$s"""
                    .formatted(
                            name().replace("[, ]", "''"),
                            cast,
                            sqlType().multiplicity()
                    );
        }

        String toRecordDefinitionForRef() {
            return """
                    "%1$s" UUID%2$s"""
                    .formatted(
                            name().replace("[, ]", "''"),
                            sqlType().multiplicity()
                    );
        }

        record SqlViewPrimitiveType(String cast, String multiplicity) {

            static final SqlViewPrimitiveType DEFAULT = new SqlViewPrimitiveType("TEXT", "");
        }
    }

    record SQLVariableForData(String name, List<SQLComponent> sqlComponents) implements SQLVariable {
        SQLVariableForData {
            if (Strings.isEmpty(name)) {
                throw new IllegalArgumentException("name can't be null");
            }
            if (CollectionUtils.isEmpty(sqlComponents)) {
                throw new IllegalArgumentException("sqlComponents can't be empty");
            }
        }

        public List<String> getSqlSelectForWithVariableJoiningRefsLinkedTo(final List<SQLVariableForRefsLinkedTo> sqlVariablesForRefsLinkedTos) {
            return sqlComponents().stream()
                    .map(component -> {
                                final LinkedList<String> sqlVariables = new LinkedList<>();
                                sqlVariables.add(getSelect(component));
                                sqlVariables.addAll(sqlVariablesForRefsLinkedTos.stream()
                                        .filter(sqlVariableForRefsLinkedTo -> sqlVariableForRefsLinkedTo.name().equals(name()))
                                        .map(SQLVariableForRefsLinkedTo::sqlComponents)
                                        .map(sqlComponents -> sqlComponents.stream()
                                                .filter(componentRefs -> componentRefs.name().equals(component.name()))
                                                .map(this::getSelectForRef)
                                                .toList()
                                        )
                                        .flatMap(List::stream)
                                        .toList()
                                );
                                return sqlVariables;
                            }
                    )
                    .flatMap(List::stream)
                    .toList();
        }
    }

    record SQLVariableForRefsLinkedTo(String name, List<SQLComponent> sqlComponents) implements SQLVariable {
        SQLVariableForRefsLinkedTo {
            if (Strings.isEmpty(name)) {
                throw new IllegalArgumentException("name can't be null");
            }
            if (CollectionUtils.isEmpty(sqlComponents)) {
                throw new IllegalArgumentException("sqlComponents can't be empty");
            }
        }
    }
}