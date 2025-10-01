package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.denormalized.Sql;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@Transactional(readOnly = true)
public class DenormalizedService {
    public static final String CANT_CREATE_DENORMALIZED_TABLE = "CANT_CREATE_DENORMALIZED_TABLE";
    private final OreSiRepository repository;
    private final BeanFactory beanFactory;
    private final SqlService sqlService;
    @Setter
    private ServiceContainer serviceContainer;

    public DenormalizedService(
            OreSiRepository repository,
            BeanFactory beanFactory,
            ServiceContainer serviceContainer, SqlService sqlService) {
        this.repository = repository;
        this.beanFactory = beanFactory;
        this.serviceContainer = serviceContainer;
        this.sqlService = sqlService;
    }

    @Transactional
    public String buildDenormalizedSchema(Application application) {
        List<Sql> buildedSqls = application.getConfiguration().dataDescription().entrySet().stream()
                .map(entry -> {
                    String schemaName = application.getName();
                    String tableName = entry.getKey();
                    Sql sqls = new Sql(schemaName, tableName, application.getId());
                    StandardDataDescription dataDescription = entry.getValue();
                    final Optional<Authorization> authorization = Optional.of(dataDescription)
                            .map(StandardDataDescription::authorization);
                    dataDescription.componentDescriptions().entrySet().stream()
                            .sorted((a, b) -> {
                                if (a.getKey().equals(b.getKey())) {
                                    return 0;
                                }
                                return (b.getValue() instanceof PatternComponent) || (b.getValue() instanceof DynamicComponent) ? -1 : 1;
                            })
                            .flatMap(componentDescriptionEntry -> {
                                fr.inra.oresing.domain.application.denormalized.Component component = fr.inra.oresing.domain.application.denormalized.Component.of(
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
                                                .anyMatch(componentDescriptionEntry.getKey()::equals),
                                        componentDescriptionEntry.getKey()
                                );
                                return switch (componentDescriptionEntry.getValue()) {
                                    case PatternComponentAdjacents _ -> Stream.empty();
                                    case PatternComponentQualifiers _ -> Stream.empty();
                                    case PatternComponent patternComponent -> {
                                        List<fr.inra.oresing.domain.application.denormalized.Component> components = new ArrayList<>();
                                        components.add(component);
                                        patternComponent.patternComponentQualifiers().entrySet().stream()
                                                .map(qualifierComponentEntry -> fr.inra.oresing.domain.application.denormalized.Component.of(
                                                                qualifierComponentEntry.getKey(),
                                                                qualifierComponentEntry.getValue(),
                                                                authorization
                                                                        .map(Authorization::timeScope)
                                                                        .stream().anyMatch(qualifierComponentEntry.getKey()::equals),
                                                                authorization
                                                                        .map(Authorization::authorizationScope)
                                                                        .stream()
                                                                        .flatMap(List::stream)
                                                                        .map(AuthorizationScopeComponentData::component)
                                                                        .anyMatch(qualifierComponentEntry.getKey()::equals),
                                                                component.fieldName()
                                                        )
                                                )
                                                .forEach(qualifierComponent -> components.add(qualifierComponent));
                                        patternComponent.patternComponentAdjacents().entrySet().stream()
                                                .map(adjacentComponentEntry -> fr.inra.oresing.domain.application.denormalized.Component.of(
                                                        adjacentComponentEntry.getKey(),
                                                        adjacentComponentEntry.getValue(),
                                                        authorization
                                                                .map(Authorization::timeScope)
                                                                .stream().anyMatch(adjacentComponentEntry.getKey()::equals),
                                                        authorization
                                                                .map(Authorization::authorizationScope)
                                                                .stream()
                                                                .flatMap(List::stream)
                                                                .map(AuthorizationScopeComponentData::component)
                                                                .anyMatch(adjacentComponentEntry.getKey()::equals),
                                                        component.fieldName())
                                                )
                                                .forEach(adjacentComponent -> components.add(adjacentComponent));
                                        yield components.stream();
                                    }
                                    default -> Stream.of(component);
                                };
                            })
                            .forEach(component -> component.buildRequests(sqls));
                    return sqls;
                })
                .toList();
        buildedSqls = Sql.sortSqlsByForeignKeyDependency(buildedSqls);
        final String tablesCreationSchema = buildedSqls.stream()
                .map(Sql::createTable)
                .collect(Collectors.joining("\n\t"));
        String tableSql = """
                drop schema if exists %2$s_dn cascade;
                create schema %2$s_dn;
                ALTER SCHEMA monsore_dn
                 OWNER TO "%3$s_applicationManager";
                
                GRANT USAGE ON SCHEMA %2$s_dn TO PUBLIC;
                
                create table %2$s_dn.referenceDisplay as
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
                      from %2$s.referencevalue);
                       %1$s
                       drop table %2$s_dn.referenceDisplay; """
                .formatted(
                        tablesCreationSchema,
                        application.getName(),
                        application.getId().toString()
                );
        try {
            if(!sqlService.createDenormalizedTable(tableSql)){
                return CANT_CREATE_DENORMALIZED_TABLE;
            };
        } catch (Exception e) {
            log.error(CANT_CREATE_DENORMALIZED_TABLE, e);
            return CANT_CREATE_DENORMALIZED_TABLE;
        }
        return tableSql;
    }
}