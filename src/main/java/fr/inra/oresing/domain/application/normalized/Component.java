package fr.inra.oresing.domain.application.normalized;

import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.LinkedList;
import java.util.Optional;

public record Component(
        String fieldName,
        String fieldPath,
        ComponentDescription fieldDescription,
        boolean isHidden,
        CheckerDescription.CheckerDescriptionType type,
        boolean isDynamic,
        Multiplicity multiplicity,
        String refType,
        boolean isAuthorizationTimeScopeField,
        boolean isAuthorizationAuthorizationScopeField
) {
    public static Component of(
            String fieldName,
            ComponentDescription fieldDescription,
            boolean isAuthorizationTimeScopeField,
            boolean isAuthorizationAuthorizationScopeField,
            String innerName) {
        String componentFieldPath = "";
        String componentFieldName = "";
        boolean isDynamic = false;
        switch (fieldDescription) {
            case DynamicComponent dynamicComponent -> {
                componentFieldName = fieldName;
                componentFieldPath = fieldName;
                isDynamic = true;
            }
            case PatternComponent patternComponent -> {
                componentFieldName = patternComponent.componentKey();
                componentFieldPath = "%s.__VALUE__".formatted(patternComponent.componentKey());
            }
            case PatternComponentAdjacents patternComponentAdjacents -> {
                componentFieldName = "%s__%s".formatted(innerName, fieldName);
                componentFieldPath = "\"%s::%s\"".formatted(innerName, fieldName);
            }
            case PatternComponentQualifiers patternComponentQualifiers -> {
                componentFieldName = "%s__%s".formatted(innerName, fieldName);
                componentFieldPath = "\"%s::%s\"".formatted(innerName, fieldName);
            }
            default -> {
                componentFieldName = fieldName;
                componentFieldPath = fieldName;
            }
        }
        final Optional<CheckerDescription> checkerDescription = Optional.of(fieldDescription)
                .map(ComponentDescription::checker);
        final CheckerDescription.CheckerDescriptionType type = checkerDescription
                .map(CheckerDescription::type)
                .orElse(null);
        final Multiplicity multiplicity = checkerDescription
                .map(CheckerDescription::multiplicity)
                .orElse(Multiplicity.ONE);

        final String refType = checkerDescription
                .filter(ReferenceChecker.class::isInstance)
                .map(ReferenceChecker.class::cast)
                .map(ReferenceChecker::refType)
                .orElse(null);

        return new Component(
                componentFieldName,
                componentFieldPath,
                fieldDescription,
                fieldDescription.isHidden(),
                type,
                isDynamic,
                multiplicity,
                refType,
                isAuthorizationTimeScopeField,
                isAuthorizationAuthorizationScopeField
        );
    }

    public void buildRequests(Sql sqls) {
        if (isHidden()) {
            return;
        }
        if (type() == null) {
            sqlForSimpleField(sqls, SqlTypes.TEXT);
            return;
        }
        switch (type()) {
            case DateChecker    -> sqlForDateField(sqls);
            case FloatChecker   -> sqlForSimpleField(sqls, SqlTypes.FLOAT);
            case IntegerChecker -> sqlForSimpleField(sqls, SqlTypes.INTEGER);
            case BooleanChecker -> sqlForSimpleField(sqls, SqlTypes.BOOLEAN);
            case ReferenceChecker -> sqlForReferenceField(sqls);
            default             -> sqlForSimpleField(sqls, SqlTypes.TEXT);
        }
    }

    private void sqlForReferenceField(Sql sqls) {
        final String refslinkedToTable = """
                NESTED PATH '$.%2$s.%3$s.*.uuids[*]' COLUMNS(
                "%1$s_id" FOR ORDINALITY,
                "%1$s" TEXT PATH '$'
                )"""
                .formatted(
                        fieldName(),
                        refType(),
                        fieldPath()
                );
        final String join = "left join %1$s_dn.referenceDisplay \"%2$s\" on \"%2$s\".id = refs.\"%2$s\"::uuid"
                .formatted(
                        sqls.schemaName(),
                        fieldName()
                );
        sqls.referenceJoin().add(
                new ReferenceJoin(refslinkedToTable, join)
        );
        if (Multiplicity.MANY.equals(multiplicity())) {
            sqlForManyReferenceField(sqls);
        } else {
            sqlForOneReferenceField(sqls);
        }
    }

    private void sqlForOneReferenceField(Sql sqls) {
        sqls.select().add("MAX(refs.\"%1$s\")::UUID\t\t\"%1$s_id\"".formatted(fieldName()));
        if (isAuthorizationAuthorizationScopeField()) {
            sqls.select().add("MAX(\"%1$s\".hierarchicalkey::TEXT)::LTREE\t\t\"%1$s_hk\"".formatted(fieldName()));
            addIndex(sqls, "%1$s_hk".formatted(fieldName()), sqls.schemaName(), sqls.tableName());
            sqls.authorizationScopes().put(refType(), "%1$s".formatted(escapedFieldName()));
        }
        sqls.select().add("MAX(\"%1$s\".display_fr)::TEXT\t\t\"%1$s_fr\"".formatted(fieldName()));
        sqls.select().add("MAX(\"%1$s\".display_en)::TEXT\t\t\"%1$s_en\"".formatted(fieldName()));
        addIndex(sqls, "\"%1$s_id\"".formatted(escapedFieldName()), sqls.schemaName(), sqls.tableName());
        sqls.foreignKeys()
                .computeIfAbsent(refType(), _ -> new LinkedList<>())
                .add("\"%1$s_id\"".formatted(escapedFieldName()));
    }

    private void sqlForManyReferenceField(Sql sqls) {
        sqls.select().add("ARRAY_AGG(refs.\"%1$s\" ORDER BY \"%1$s_id\") FILTER (WHERE \"%1$s_id\" IS NOT NULL)::uuid[]\t\t\"%1$s_id\"".formatted(fieldName()));
        if (isAuthorizationAuthorizationScopeField()) {
            sqls.select().add("ARRAY_AGG(\"%1$s\".hierarchicalkey::TEXT ORDER BY \"%1$s_id\") FILTER (WHERE \"%1$s_id\" IS NOT NULL)::UUID[]\t\t\"%1$s_hk\"".formatted(fieldName()));
            addIndex(sqls, "%1$s_hk".formatted(fieldName()), sqls.schemaName(), sqls.tableName());
            sqls.authorizationScopes().put(refType(), "%1$s".formatted(escapedFieldName()));
        }
        sqls.select().add("ARRAY_AGG(\"%1$s\".display_fr ORDER BY \"%1$s_id\") FILTER (WHERE \"%1$s_id\" IS NOT NULL)::TEXT[]\t\t\"%1$s_fr\"".formatted(fieldName()));
        sqls.select().add("ARRAY_AGG(\"%1$s\".display_en ORDER BY \"%1$s_id\")FILTER (WHERE \"%1$s_id\" IS NOT NULL)::TEXT[]\t\t\"%1$s_en\"".formatted(fieldName()));
        sqls.normalizedJoinManyToManies()
                .computeIfAbsent(refType(), _ -> new LinkedList<>())
                .add(fieldName());
    }

    private void sqlForDateField(Sql sqls) {
        String defaut = "''";
        String aggregateType = "";
        if (Multiplicity.MANY.equals(multiplicity())) {
            defaut = "'{}'";
            aggregateType = "[]";
        }
        sqls.select().add(
                """
                        MAX(NULLIF(val."%1$s", %3$s))::composite_date%2$s::timestamp%2$s "ts_%1$s",
                        \t\tMAX(NULLIF(val."%1$s", %3$s))::composite_date%2$s::text%2$s "%1$s" """
                        .formatted(
                                fieldName(),
                                aggregateType,
                                defaut
                        )
        );
        sqls.refValuesTable().add("""
                "%1$s" TEXT%2$s PATH '$."%3$s"'"""
                .formatted(
                        fieldName(),
                        aggregateType,
                        fieldPath()
                )
        );
        if (isAuthorizationTimeScopeField()) {
            sqls.indexes().add("""
                    CREATE INDEX IF NOT EXISTS "ts_%1$s_idx"
                        ON %2$s_dn."%3$s" USING brin
                            (ts_date timestamp_minmax_multi_ops)
                        WITH (pages_per_range =128, autosummarize= False)
                        TABLESPACE pg_default;""".formatted(escapedFieldName(), sqls.schemaName(), sqls.tableName())
            );
            sqls.timescopes().add("ts_%1$s".formatted(escapedFieldName()));
        }
    }

    private void sqlForDynamicComponent(Sql sqls) {
        sqls.select().add("ARRAY_AGG(refs.\"%1$s\" ORDER BY \"%1$s_id\") FILTER (WHERE \"%1$s_id\" IS NOT NULL)::uuid[]\t\t\"%1$s_id\"".formatted(fieldName()));

        final String foreignTableName = Optional.of(fieldDescription()).map(DynamicComponent.class::cast).map(DynamicComponent::reference).orElse("");
        final String refslinkedToTable = """
                NESTED PATH '$.%2$s.*.*.uuids[*]' COLUMNS (
                 "%1$s_id" FOR ORDINALITY,
                 "%1$s" TEXT PATH '$'
                )"""
                .formatted(
                        fieldName(),
                        foreignTableName
                );
        final String join = "left join %1$s_dn.referenceDisplay \"%2$s\" on \"%2$s\".id = refs.\"%2$s\"::uuid"
                .formatted(
                        sqls.schemaName(),
                        fieldName()
                );
        sqls.referenceJoin().add(
                new ReferenceJoin(refslinkedToTable, join)
        );
        sqls.normalizedJoinManyToManies()
                .computeIfAbsent(foreignTableName, _ -> new LinkedList<>())
                .add(fieldName());
    }

    private String escapedFieldName() {
        return fieldName().replace("\"", "");
    }

    private void addIndex(Sql sqls, String fieldName, String schemaName, String tableName) {
        sqls.indexes().add("""
                CREATE INDEX IF NOT EXISTS "%1$s_idx"
                    ON %2$s_dn."%3$s" USING btree ("%1$s" ASC NULLS LAST);"""
                .formatted(fieldName.replace("\"", ""), schemaName, tableName)
        );
    }


    @SuppressWarnings("java:S1172") // type conservé pour lisibilité des appels (DateChecker→FLOAT, etc.)
    private void sqlForSimpleField(Sql sqls, SqlTypes type) {
        if (isDynamic()) {
            sqlForDynamicComponent(sqls);
        } else {
            sqlForTextField(sqls);
        }
    }

    private void sqlForTextField(Sql sqls) {
        String aggregateType = "";
        if (Multiplicity.MANY.equals(multiplicity())) {
            aggregateType = "[]";
        }
        sqls.select().add(
                """
                        MAX(val."%1$s")::TEXT%2$s  \t\t"%1$s\""""
                        .formatted(fieldName(),
                                aggregateType
                        )
        );
        sqls.refValuesTable().add("""
                "%1$s" TEXT%2$s PATH '$.%3$s'"""
                .formatted(
                        fieldName(),
                        aggregateType,
                        fieldPath().replace("::", "\".\"")
                )
        );
    }
}