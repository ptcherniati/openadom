package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.application.configuration.AuthorizationScopeComponentData;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional(readOnly = true)
public class DenormalizedService {
    private final OreSiRepository repository;
    private final BeanFactory beanFactory;
    private final SqlService sqlService;
    @Setter
    private ServiceContainer serviceContainer;

    static enum SqlTypes {TEXT, INTEGER, FLOAT, BOOLEAN}

    ;

    public DenormalizedService(
            OreSiRepository repository,
            BeanFactory beanFactory,
            ServiceContainer serviceContainer, SqlService sqlService) {
        this.repository = repository;
        this.beanFactory = beanFactory;
        this.serviceContainer = serviceContainer;
        this.sqlService = sqlService;
    }

    public void buildDenormalizedSchema(Application application) {
        String collect = application.getConfiguration().dataDescription().entrySet().stream()
                .map(entry -> {
                    Sql sqls = new Sql();
                    String schemaName = application.getName();
                    String tableName = entry.getKey();
                    StandardDataDescription dataDescription = entry.getValue();
                    final Optional<Authorization> authorization = Optional.of(dataDescription)
                            .map(StandardDataDescription::authorization);
                    dataDescription.componentDescriptions().entrySet().stream()
                            .forEach(componentDescriptionEntry -> {
                                fr.inra.oresing.rest.services.Component component = fr.inra.oresing.rest.services.Component.of(
                                        componentDescriptionEntry.getKey(),
                                        componentDescriptionEntry.getValue(),
                                        authorization
                                                .map(Authorization::timeScope)
                                                .stream().anyMatch(componentDescriptionEntry.getKey()::equals),
                                        authorization
                                                .map(Authorization::authorizationScope)
                                                .stream()
                                                .flatMap(List::stream)
                                                .map(AuthorizationScopeComponentData::component)
                                                .anyMatch(componentDescriptionEntry.getKey()::equals)

                                );
                                if (component.isHidden()) {
                                    return;
                                }
                                if (component.type() == null) {
                                    sqlForSimpleField(component, sqls,SqlTypes.TEXT);

                                } else {
                                    switch (component.type()) {
                                        case DateChecker -> {
                                            String aggregate = "MAX";
                                            String aggregateType = "";
                                            if (Multiplicity.MANY.equals(component.multiplicity())) {
                                                aggregate = "ARRAY_AGG";
                                                aggregateType = "[]";
                                            }
                                            sqls.select().add(
                                                    """
                                                            %2$s(val.%1$s)::composite_date::timestamp%3$s ts_%1$s,
                                                            \t\t%2$s(val.%1$s)::composite_date::text%3$s %1$s """
                                                            .formatted(
                                                                    component.fieldName(),
                                                                    aggregate,
                                                                    aggregateType
                                                            )
                                            );
                                            sqls.refValuesTable().add("""
                                                    NESTED PATH '$.%1$s[*]' COLUMNS(%1$s TEXT PATH '$')"""
                                                    .formatted(component.fieldName())
                                            );
                                        }
                                        case FloatChecker -> {
                                            sqlForSimpleField(component, sqls,SqlTypes.FLOAT);
                                        }
                                        case IntegerChecker -> {
                                            sqlForSimpleField(component, sqls,SqlTypes.INTEGER);

                                        }
                                        case BooleanChecker -> {
                                            sqlForSimpleField(component, sqls,SqlTypes.BOOLEAN);

                                        }
                                        case ReferenceChecker -> {
                                            String aggregate = "MAX";
                                            String aggregateType = "";
                                            if (Multiplicity.MANY.equals(component.multiplicity())) {
                                                aggregate = "ARRAY_AGG";
                                                aggregateType = "[]";
                                            }
                                            final String refslinkedToTable = """
                                                    NESTED PATH '$.%2$s.%1$s.*.uuids[*]' COLUMNS(%1$s TEXT PATH '$')"""
                                                    .formatted(
                                                            component.fieldName(),
                                                            component.refType()
                                                    );
                                            final String join = "left join %1$s_dn.referenceDisplay %2$s on %2$s.id = refs.%2$s::uuid"
                                                    .formatted(
                                                            schemaName,
                                                            component.fieldName()
                                                    );
                                            sqls.select().add("%2$s(refs.%1$s)::uuid%3$s\t\t%1$s".formatted(component.fieldName(), aggregate, aggregateType));
                                            sqls.select().add("%2$s(%1$s.display_fr)::TEXT%3$s\t\t%1$s_fr".formatted(component.fieldName(), aggregate, aggregateType));
                                            sqls.select().add("%2$s(%1$s.display_en)::TEXT%3$s\t\t%1$s_en".formatted(component.fieldName(), aggregate, aggregateType));
                                            sqls.referenceJoin().add(
                                                    new ReferenceJoin(refslinkedToTable, join)
                                            );
                                        }
                                        default -> {
                                            sqlForSimpleField(component, sqls,SqlTypes.TEXT);

                                        }
                                    }
                                }

                            });
                    return """
                            --create table %2$s
                            create table %1$s_dn.%2$s as (
                                select
                                    %3$s
                            
                                FROM %4$s
                                WHERE referencetype = '%2$s'
                                GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
                            );
                            """
                            .formatted(
                                    schemaName,
                                    tableName,
                                    sqls.select().stream()
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.joining(",\n\t\t")),
                                    """
                                            
                                            \t\t%1$s.referencevalue,
                                            \t\t%2$s%3$s
                                            \t\t%4$s
                                            \t\t%5$s
                                            """.formatted(
                                            schemaName,
                                            """
                                                    -- add simple fields
                                                            JSON_TABLE(
                                                                        refvalues, 
                                                                        '$' COLUMNS (
                                                                            %1$s
                                                                        )
                                                                     ) AS val"""
                                                    .formatted(
                                                            sqls.refValuesTable().stream()
                                                                    .filter(Objects::nonNull)
                                                                    .collect(
                                                                            Collectors.joining(",\n\t\t\t\t\t\t")
                                                                    )
                                                    ),
                                            sqls.referenceJoin().isEmpty() ? "" : ",",
                                            sqls.referenceJoin().isEmpty() ? "" : """
                                                    -- add references fields
                                                            JSON_TABLE(
                                                                        refslinkedto, 
                                                                        '$' COLUMNS (
                                                                            %1$s
                                                                        )
                                                                    ) AS refs"""
                                                    .formatted(sqls.referenceJoin().stream()
                                                            .filter(Objects::nonNull)
                                                            .map(ReferenceJoin::refsLinkedToTable)
                                                            .collect(Collectors.joining(",\n\t\t\t\t")
                                                            )
                                                    ),
                                            sqls.referenceJoin().isEmpty() ? "" :
                                                    sqls.referenceJoin().stream()
                                                            .filter(Objects::nonNull)
                                                            .map(ReferenceJoin::referenceJoin)
                                                            .collect(Collectors.joining("\n\t\t"))
                                    )
                            );
                })
                .collect(Collectors.joining("\n\t"));
        collect = """
                drop schema if exists monsore_dn cascade;
                create schema monsore_dn;
                
                create table monsore_dn.referenceDisplay as
                    (select id,
                            hierarchicalkey,
                            COALESCE(
                                    NULLIF(refvalues ->> '__display_fr', ''),
                                    refvalues ->> '__display_default'
                            ) display_fr,
                            COALESCE(
                                    NULLIF(refvalues ->> '__display_en', ''),
                                    NULLIF(refvalues ->> '__display_fr', ''),
                                    refvalues ->> '__display_default'
                            ) display_en
                     from monsore.referencevalue);
                      %1$s
                      drop table monsore_dn.referenceDisplay; """.formatted(collect);
        System.out.println(collect);
    }

    private static void sqlForSimpleField(fr.inra.oresing.rest.services.Component component, Sql sqls, SqlTypes type) {
        String aggregate = "MAX";
        String aggregateType = "";
        if (Multiplicity.MANY.equals(component.multiplicity())) {
            aggregate = "ARRAY_AGG";
            aggregateType = "[]";
        }
        sqls.select().add(
                """
                        %3$s(val.%1$s)::%2$s%4$s  \t\t%1$s"""
                        .formatted(component.fieldName(),
                                type.name(),
                                aggregate,
                                aggregateType
                        )
        );
        sqls.refValuesTable().add("""
                NESTED PATH '$.%2$s[*]' COLUMNS(%2$s %1$s PATH '$') """
                .formatted(
                        Optional.ofNullable(type)
                                .map(Enum::name)
                                .orElse("TEXT"),
                        component.fieldName()
                )
        );
    }
}