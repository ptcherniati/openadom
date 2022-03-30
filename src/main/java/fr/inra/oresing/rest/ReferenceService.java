package fr.inra.oresing.rest;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.groovy.Expression;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.groovy.StringSetGroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ColumnPresenceConstraint;
import fr.inra.oresing.model.ComputedValueUsage;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceColumnMultipleValue;
import fr.inra.oresing.model.ReferenceColumnSingleValue;
import fr.inra.oresing.model.ReferenceColumnValue;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

        ImmutableMap<ReferenceColumn, Multiplicity> multiplicityPerColumns = lineCheckers.stream()
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker)
                .map(lineChecker -> (ReferenceLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(referenceLineChecker -> (ReferenceColumn) referenceLineChecker.getTarget(), referenceLineChecker -> referenceLineChecker.getConfiguration().getMultiplicity()));

        Configuration.ReferenceDescription referenceDescription = conf.getReferences().get(refType);

        ImmutableSet<ReferenceImporterContext.Column> staticColumns = referenceDescription.getColumns().entrySet().stream()
                .map(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Multiplicity multiplicity = multiplicityPerColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                    Configuration.ReferenceStaticNotComputedColumnDescription referenceStaticNotComputedColumnDescription = MoreObjects.firstNonNull(entry.getValue(), new Configuration.ReferenceStaticNotComputedColumnDescription());
                    return staticColumnDescriptionToColumn(referenceValueRepository, referenceColumn, multiplicity, referenceStaticNotComputedColumnDescription);
                }).collect(ImmutableSet.toImmutableSet());

        ImmutableSet<ReferenceImporterContext.Column> computedColumns = referenceDescription.getComputedColumns().entrySet().stream()
                .map(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Configuration.ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription = entry.getValue();
                    Multiplicity multiplicity = multiplicityPerColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                    return computedColumnDescriptionToColumn(referenceValueRepository, referenceColumn, referenceStaticComputedColumnDescription, multiplicity);
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
    /*    final Set<Object> patternColumns = constants.getPatternColumns()
                .map(pt -> pt.values())
                .flatMap(Collection::stream)
                .stream().collect(Collectors.toSet());*/
        Set<String> patternColumns = constants.getPatternColumns()
                .map(m->m.values().stream().flatMap(List::stream).collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        final Map<String, String> referenceToColumnName = lineCheckers.stream()
                .filter(ReferenceLineChecker.class::isInstance)
                .map(ReferenceLineChecker.class::cast)
                .collect(Collectors.toMap(ReferenceLineChecker::getRefType, referenceLineChecker -> ((ReferenceColumn) referenceLineChecker.getTarget()).getColumn()));
        final Map<String, Map<String, Map<String, String>>> displayByReferenceAndNaturalKey =
                lineCheckers.stream()
                        .filter(ReferenceLineChecker.class::isInstance)
                        .map(ReferenceLineChecker.class::cast)
                        .map(ReferenceLineChecker::getRefType)
                        .filter(rt -> patternColumns.contains(rt))
                        .collect(Collectors.toMap(ref -> referenceToColumnName.getOrDefault(ref, ref), ref -> repo.getRepository(app).referenceValue().findDisplayByNaturalKey(ref)));
        final ReferenceImporterContext referenceImporterContext =
                new ReferenceImporterContext(
                        constants,
                        lineCheckers,
                        storedReferences,
                        columns,
                        displayByReferenceAndNaturalKey
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
                            Map.entry(reference, referenceValue.getId()),
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

    private ReferenceImporterContext.Column staticColumnDescriptionToColumn(ReferenceValueRepository referenceValueRepository, ReferenceColumn referenceColumn, Multiplicity multiplicity, Configuration.ReferenceStaticNotComputedColumnDescription referenceStaticNotComputedColumnDescription) {
        ColumnPresenceConstraint presenceConstraint = referenceStaticNotComputedColumnDescription.getPresenceConstraint();
        ReferenceImporterContext.Column column;
        if (multiplicity == Multiplicity.ONE) {
            column = Optional.ofNullable(referenceStaticNotComputedColumnDescription.getDefaultValue()).map(defaultValueConfiguration -> {
                Expression<String> computationExpression = StringGroovyExpression.forExpression(defaultValueConfiguration.getExpression());
                Set<String> configurationReferences = defaultValueConfiguration.getReferences();
                ImmutableMap<String, Object> contextForExpression = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences);
                Preconditions.checkState(defaultValueConfiguration.getDatatypes().isEmpty(), "à ce stade, on ne gère pas la chargement de données");
                ReferenceImporterContext.Column oneValueStaticColumn = new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
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
                return oneValueStaticColumn;
            }).orElseGet(() -> new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.NOT_COMPUTED) {
                @Override
                String getExpectedHeader() {
                    return referenceColumn.getColumn();
                }

                @Override
                Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                    throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
                }
            });
        } else if (multiplicity == Multiplicity.MANY) {
            column = Optional.ofNullable(referenceStaticNotComputedColumnDescription.getDefaultValue()).map(defaultValueConfiguration -> {
                Expression<Set<String>> computationExpression = StringSetGroovyExpression.forExpression(defaultValueConfiguration.getExpression());
                Set<String> configurationReferences = defaultValueConfiguration.getReferences();
                ImmutableMap<String, Object> contextForExpression = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences);
                Preconditions.checkState(defaultValueConfiguration.getDatatypes().isEmpty(), "à ce stade, on ne gère pas la chargement de données");
                ReferenceImporterContext.Column manyValuesStaticColumn = new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
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
                return manyValuesStaticColumn;
            }).orElseGet(() -> new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.NOT_COMPUTED) {
                @Override
                String getExpectedHeader() {
                    return referenceColumn.getColumn();
                }

                @Override
                Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum) {
                    throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
                }
            });
        } else {
            throw new IllegalStateException("multiplicity = " + multiplicity);
        }
        return column;
    }

    private ReferenceImporterContext.Column computedColumnDescriptionToColumn(ReferenceValueRepository referenceValueRepository, ReferenceColumn referenceColumn, Configuration.ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, Multiplicity multiplicity) {
        Configuration.GroovyConfiguration computation = referenceStaticComputedColumnDescription.getComputation();
        ColumnPresenceConstraint presenceConstraint = ColumnPresenceConstraint.ABSENT;
        ReferenceImporterContext.Column column;
        Set<String> configurationReferences = computation.getReferences();
        ImmutableMap<String, Object> contextForExpression = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences);
        Preconditions.checkState(computation.getDatatypes().isEmpty(), "à ce stade, on ne gère pas la chargement de données");
        if (multiplicity == Multiplicity.ONE) {
            Expression<String> computationExpression = StringGroovyExpression.forExpression(computation.getExpression());
            column = new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.USE_COMPUTED_VALUE) {
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
        } else if (multiplicity == Multiplicity.MANY) {
            Expression<Set<String>> computationExpression = StringSetGroovyExpression.forExpression(computation.getExpression());
            column = new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, presenceConstraint, ComputedValueUsage.USE_COMPUTED_VALUE) {
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
        } else {
            throw new IllegalStateException("multiplicity = " + multiplicity);
        }
        return column;
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

    String getReferenceValuesCsv(String applicationNameOrId, String referenceType, MultiValueMap<String, String> params) {
        Application application = getApplication(applicationNameOrId);
        ReferenceImporterContext referenceImporterContext = getReferenceImporterContext(application, referenceType);
        ReferenceValueRepository referenceValueRepository = repo.getRepository(applicationNameOrId).referenceValue();
        Stream<ImmutableList<String>> recordsStream = referenceValueRepository.findAllByReferenceType(referenceType, params).stream()
                .map(ReferenceValue::getRefValues)
                .map(referenceDatum -> {
                    ImmutableList<String> rowAsRecord = referenceImporterContext.getExpectedHeaders().stream()
                            .map(header -> referenceImporterContext.getCsvCellContent(referenceDatum, header))
                            .collect(ImmutableList.toImmutableList());
                    return rowAsRecord;
                });
        ImmutableSet<String> headers = referenceImporterContext.getExpectedHeaders();
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(referenceImporterContext.getCsvSeparator())
                .withSkipHeaderRecord();
        StringWriter out = new StringWriter();
        try {
            CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
            csvPrinter.printRecord(headers);
            recordsStream.forEach(record -> {
                try {
                    csvPrinter.printRecord(record);
                } catch (IOException e) {
                    throw new OreSiTechnicalException("erreur lors de la génération du fichier CSV", e);
                }
            });
        } catch (IOException e) {
            throw new OreSiTechnicalException("erreur lors de la génération du fichier CSV", e);
        }
        String csv = out.toString();
        return csv;
    }

    private Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }
}