package fr.inra.oresing.rest;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.checker.*;
import fr.inra.oresing.groovy.Expression;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.groovy.StringSetGroovyExpression;
import fr.inra.oresing.model.*;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@Transactional
public class ReferenceService {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private OreSiRepository repo;

    void addReference(Application app, String refType, MultipartFile file, UUID fileId) throws IOException {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();
        final ReferenceImporterContext referenceImporterContext = getReferenceImporterContext(app, refType);
        ReferenceImporter referenceImporter = new ReferenceImporter(referenceImporterContext) {
            @Override
            void storeAll(Stream<ReferenceValue> stream) {
                final List<UUID> uuids = referenceValueRepository.storeAll(stream);
                referenceValueRepository.updateConstraintForeignReferences(uuids);
            }
        };
        referenceImporter.doImport(file, fileId);
    }

    private ReferenceImporterContext getReferenceImporterContext(Application app, String refType) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();
        Configuration conf = app.getConfiguration();
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getReferenceValidationLineCheckers(app, refType);
        final ImmutableMap<Ltree, UUID> storedReferences = referenceValueRepository.getReferenceIdPerKeys(refType);

        ImmutableMap<ReferenceColumn, Multiplicity> multiplicityReferencePerColumns = lineCheckers.stream()
                .filter(lineChecker -> lineChecker instanceof CheckerOnOneVariableComponentLineChecker)
                .map(lineChecker -> (CheckerOnOneVariableComponentLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(
                        referenceLineChecker -> (ReferenceColumn) referenceLineChecker.getTarget(),
                        referenceLineChecker -> referenceLineChecker.getConfiguration().getMultiplicity())
                );

        Configuration.ReferenceDescription referenceDescription = conf.getReferences().get(refType);
        boolean allowUnexpectedColumns = referenceDescription.isAllowUnexpectedColumns();

        ImmutableSet<ReferenceImporterContext.Column> staticColumns = referenceDescription.getColumns().entrySet().stream()
                .map(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Multiplicity multiplicity = multiplicityReferencePerColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                    Configuration.ReferenceStaticNotComputedColumnDescription referenceStaticNotComputedColumnDescription = MoreObjects.firstNonNull(entry.getValue(), new Configuration.ReferenceStaticNotComputedColumnDescription());
                    ColumnPresenceConstraint presenceConstraint = referenceStaticNotComputedColumnDescription.getPresenceConstraint();
                    ReferenceImporterContext.Column column = Optional.ofNullable(referenceStaticNotComputedColumnDescription.getDefaultValue())
                            .map(defaultValueConfiguration -> staticColumnDescriptionToColumn(referenceColumn, presenceConstraint, multiplicity, referenceValueRepository, defaultValueConfiguration))
                            .orElseGet(() -> staticColumnDescriptionToColumn(referenceColumn, presenceConstraint, multiplicity));
                    return column;
                }).collect(ImmutableSet.toImmutableSet());

        ImmutableSet<ReferenceImporterContext.Column> computedColumns = referenceDescription.getComputedColumns().entrySet().stream()
                .map(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Configuration.ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription = entry.getValue();
                    Multiplicity multiplicity = multiplicityReferencePerColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                    return computedColumnDescriptionToColumn(referenceValueRepository, referenceColumn, multiplicity, referenceStaticComputedColumnDescription);
                }).collect(ImmutableSet.toImmutableSet());

        ImmutableSet<ReferenceImporterContext.Column> dynamicColumns = referenceDescription.getDynamicColumns().entrySet().stream()
                .flatMap(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Configuration.ReferenceDynamicColumnDescription referenceDynamicColumnDescription = entry.getValue();
                    ImmutableSet<ReferenceImporterContext.Column> valuedDynamicColumns = dynamicColumnDescriptionToColumns(referenceValueRepository, referenceColumn, referenceDynamicColumnDescription);
                    return valuedDynamicColumns.stream();
                }).collect(ImmutableSet.toImmutableSet());

        ImmutableSet<ReferenceImporterContext.Column> columns = ImmutableSet.<ReferenceImporterContext.Column>builder()
                .addAll(staticColumns)
                .addAll(computedColumns)
                .addAll(dynamicColumns)
                .build();

        final ReferenceImporterContext.Constants constants = new ReferenceImporterContext.Constants(
                app.getId(),
                conf,
                refType,
                repo.getRepository(app).referenceValue());
        Set<String> patternColumns = constants.getPatternColumns()
                .map(m -> m.values().stream().flatMap(List::stream).collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        final Map<String, List<String>> referenceToColumnName = lineCheckers.stream()
                .filter(ReferenceLineChecker.class::isInstance)
                .map(ReferenceLineChecker.class::cast)
                .collect(Collectors.groupingBy(
                                ReferenceLineChecker::getRefType,
                                Collectors.mapping(referenceLineChecker -> ((ReferenceColumn) referenceLineChecker.getTarget()).getColumn(), Collectors.toList())
                        )
                );
        final Map<String, Map<String, Map<String, String>>> displayByReferenceAndNaturalKey =
                lineCheckers.stream()
                        .filter(ReferenceLineChecker.class::isInstance)
                        .map(ReferenceLineChecker.class::cast)
                        .map(ReferenceLineChecker::getRefType)
                        .filter(rt -> patternColumns.contains(rt))
                        .collect(Collectors.toMap(ref ->
                                Optional.ofNullable(referenceToColumnName.getOrDefault(ref, null))
                                        .map(l->l.get(0))
                                        .orElse(ref),
                                ref -> repo.getRepository(app).referenceValue().findDisplayByNaturalKey(ref)));
        final ReferenceImporterContext referenceImporterContext =
                new ReferenceImporterContext(
                        constants,
                        lineCheckers,
                        storedReferences,
                        columns,
                        displayByReferenceAndNaturalKey,
                        allowUnexpectedColumns
                );
        return referenceImporterContext;
    }

    private ImmutableSet<ReferenceImporterContext.Column> dynamicColumnDescriptionToColumns(ReferenceValueRepository referenceValueRepository, ReferenceColumn referenceColumn, Configuration.ReferenceDynamicColumnDescription referenceDynamicColumnDescription) {
        String reference = referenceDynamicColumnDescription.getReference();
        ReferenceColumn referenceColumnToLookForHeader = new ReferenceColumn(referenceDynamicColumnDescription.getReferenceColumnToLookForHeader());
        List<ReferenceValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(reference);
        ImmutableSet<ReferenceImporterContext.Column> valuedDynamicColumns = allByReferenceType.stream()
                .map(referenceValue -> {
                    ReferenceDatum referenceDatum = referenceValue.getRefValues();
                    Ltree hierarchicalKey = referenceValue.getHierarchicalKey();
                    ReferenceColumnSingleValue referenceColumnValue = (ReferenceColumnSingleValue) referenceDatum.get(referenceColumnToLookForHeader);
                    String header = referenceColumnValue.getValue();
                    String fullHeader = referenceDynamicColumnDescription.getHeaderPrefix() + header;
                    ColumnPresenceConstraint presenceConstraint = referenceDynamicColumnDescription.getPresenceConstraint();
                    return new ReferenceImporterContext.DynamicColumn(
                            referenceColumn,
                            presenceConstraint,
                            hierarchicalKey,
                            Map.entry(reference, Set.of(referenceValue.getId())),
                            ComputedValueUsage.NOT_COMPUTED
                    ) {
                        @Override
                        String getExpectedHeader() {
                            return fullHeader;
                        }

                        @Override
                        Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                            throw new UnsupportedOperationException("pas de valeur calculable pour " + referenceColumn);
                        }
                    };
                }).collect(ImmutableSet.toImmutableSet());
        return valuedDynamicColumns;
    }

    private ReferenceImporterContext.Column staticColumnDescriptionToColumn(ReferenceColumn referenceColumn, ColumnPresenceConstraint presenceConstraint, Multiplicity multiplicity) {
        ReferenceImporterContext.Column column;
        if (multiplicity == Multiplicity.ONE) {
            column = new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.NOT_COMPUTED) {
                @Override
                String getExpectedHeader() {
                    return referenceColumn.getColumn();
                }

                @Override
                Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                    throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
                }
            };
        } else if (multiplicity == Multiplicity.MANY) {
            column = new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.NOT_COMPUTED) {
                @Override
                String getExpectedHeader() {
                    return referenceColumn.getColumn();
                }

                @Override
                Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                    throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
                }
            };
        } else {
            throw Multiplicity.getError(multiplicity);
        }
        return column;
    }

    private ReferenceImporterContext.Column staticColumnDescriptionToColumn(ReferenceColumn referenceColumn, ColumnPresenceConstraint presenceConstraint, Multiplicity multiplicity, ReferenceValueRepository referenceValueRepository, Configuration.GroovyConfiguration defaultValueConfiguration) {
        ImmutableMap<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, defaultValueConfiguration);
        ReferenceImporterContext.Column column;
        if (multiplicity == Multiplicity.ONE) {
            Expression<String> computationExpression = StringGroovyExpression.forExpression(defaultValueConfiguration.getExpression());
            column = new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
                @Override
                String getExpectedHeader() {
                    return referenceColumn.getColumn();
                }

                @Override
                Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                    ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                            .putAll(contextForExpression)
                            .putAll(referenceDatum.getEvaluationContext())
                            .build();
                    String evaluate = computationExpression.evaluate(evaluationContext);
                    Optional<ReferenceColumnValue> computedValue = Optional.ofNullable(evaluate)
                            .filter(StringUtils::isNotEmpty)
                            .map(ReferenceColumnSingleValue::new);
                    return computedValue;
                }
            };
        } else if (multiplicity == Multiplicity.MANY) {
            Expression<Set<String>> computationExpression = StringSetGroovyExpression.forExpression(defaultValueConfiguration.getExpression());
            column = new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
                @Override
                String getExpectedHeader() {
                    return referenceColumn.getColumn();
                }

                @Override
                Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                    ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                            .putAll(contextForExpression)
                            .putAll(referenceDatum.getEvaluationContext())
                            .build();
                    Set<String> evaluate = computationExpression.evaluate(evaluationContext);
                    Optional<ReferenceColumnValue> computedValue = Optional.ofNullable(evaluate)
                            .map(ReferenceColumnMultipleValue::new);
                    return computedValue;
                }
            };
        } else {
            throw Multiplicity.getError(multiplicity);
        }
        return column;
    }

    private ReferenceImporterContext.Column computedColumnDescriptionToColumn(ReferenceValueRepository referenceValueRepository, ReferenceColumn referenceColumn, Multiplicity multiplicity, Configuration.ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription) {
        ReferenceImporterContext.Column column;
        if (multiplicity == Multiplicity.ONE) {
            column = newComputedColumn(referenceColumn, referenceStaticComputedColumnDescription, referenceValueRepository);
        } else if (multiplicity == Multiplicity.MANY) {
            column = newComputedManyColumn(referenceColumn, referenceStaticComputedColumnDescription, referenceValueRepository);
        } else {
            throw Multiplicity.getError(multiplicity);
        }
        return column;
    }

    private ReferenceImporterContext.Column newComputedManyColumn(ReferenceColumn referenceColumn, Configuration.ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, ReferenceValueRepository referenceValueRepository) {
        Configuration.GroovyConfiguration computation = referenceStaticComputedColumnDescription.getComputation();
        ImmutableMap<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        Expression<Set<String>> computationExpression = StringSetGroovyExpression.forExpression(computation.getExpression());
        return new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, ColumnPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE) {
            @Override
            String getExpectedHeader() {
                throw new UnsupportedOperationException("la colonne " + referenceColumn + " est calculée, il n'y a pas d'entête spécifié car elle ne doit pas être dans le CSV");
            }

            @Override
            Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(referenceDatum.getEvaluationContext())
                        .build();
                Set<String> evaluate = computationExpression.evaluate(evaluationContext);
                Optional<ReferenceColumnValue> computedValue = Optional.ofNullable(evaluate)
                        .map(ReferenceColumnMultipleValue::new);
                return computedValue;
            }
        };
    }

    private ReferenceImporterContext.Column newComputedColumn(ReferenceColumn referenceColumn, Configuration.ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, ReferenceValueRepository referenceValueRepository) {
        Configuration.GroovyConfiguration computation = referenceStaticComputedColumnDescription.getComputation();
        ImmutableMap<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        Expression<String> computationExpression = StringGroovyExpression.forExpression(computation.getExpression());
        return new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, ColumnPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE) {
            @Override
            String getExpectedHeader() {
                throw new UnsupportedOperationException("la colonne " + referenceColumn + " est calculée, il n'y a pas d'entête spécifié");
            }

            @Override
            Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(referenceDatum.getEvaluationContext())
                        .build();
                String evaluate = computationExpression.evaluate(evaluationContext);
                Optional<ReferenceColumnValue> computedValue = Optional.ofNullable(evaluate)
                        .filter(StringUtils::isNotEmpty)
                        .map(ReferenceColumnSingleValue::new);
                return computedValue;
            }
        };
    }

    private ImmutableMap<String, Object> computeGroovyContext(ReferenceValueRepository referenceValueRepository, GroovyDataInjectionConfiguration groovyDataInjectionConfiguration) {
        Set<String> configurationReferences = groovyDataInjectionConfiguration.getReferences();
        ImmutableMap<String, Object> contextForExpression = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences, null);
        Preconditions.checkState(groovyDataInjectionConfiguration.getDatatypes().isEmpty(), "à ce stade, on ne gère pas le chargement de données. Les référentiels ne doivent pas dépendre des données expérimentales.");
        return contextForExpression;
    }

    /**
     * @param nameOrId l'id de l'application
     * @param refType  le type du referenciel
     * @param params   les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    List<ReferenceValue> findReference(String nameOrId, String refType, MultiValueMap<String, String> params) {
        authenticationService.setRoleForClient();
        List<ReferenceValue> list = repo.getRepository(nameOrId).referenceValue().findAllByReferenceType(refType, params);
        return list;
    }

    List<ReferenceValue> findReferenceAccordingToRights(Application application, String refType, MultiValueMap<String, String> params) {
        authenticationService.setRoleForClient();
        List<ReferenceValue> list = repo.getRepository(application).referenceValue().findAllByReferenceType(refType, params);
        return list;
    }

    byte[] getReferenceValuesCsv(String applicationNameOrId, String referenceType, MultiValueMap<String, String> params) {
        final ReferenceImporterContext referenceImporterContext = getReferenceImporterContext(applicationNameOrId, referenceType);
        ReferenceValueRepository referenceValueRepository = repo.getRepository(applicationNameOrId).referenceValue();
        final List<ReferenceValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(referenceType, params);
        return referenceImporterContext.buildReferenceCSV(allByReferenceType);
    }

    ReferenceImporterContext getReferenceImporterContext(String applicationNameOrId, String referenceType) {
        Application application = getApplication(applicationNameOrId);
        ReferenceImporterContext referenceImporterContext = getReferenceImporterContext(application, referenceType);
        return referenceImporterContext;
    }

    private Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}