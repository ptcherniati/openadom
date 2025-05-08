package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationComponent;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.application.configuration.type.RootType;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

import static fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode.*;

public class RootBuilder {
    final Map<CheckerDescription.CheckerDescriptionType, Map<String, Map<String, List<CheckerDescription>>>> checkers = new HashMap<>();
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode rootNode;
    private final ApplicationdescriptionBuilder applicationdescriptionBuilder = new ApplicationdescriptionBuilder(this);
    @Getter
    protected final CheckerDescriptionBuilder checkerDescriptionBuilder = new CheckerDescriptionBuilder(this);
    private final TagsBuilder tagsBuilder = new TagsBuilder(this);
    @Getter
    private final ComputationBuilder computationBuilder = new ComputationBuilder(this);
    @Getter
    private final BasicComponentBuilder basicComponentBuilder = new BasicComponentBuilder(this);
    @Getter
    private final ComputedComponentBuilder computedComponentBuilder = new ComputedComponentBuilder(this);
    @Getter
    private final DynamicComponentsBuilder dynamicComponentsBuilder = new DynamicComponentsBuilder(this);
    @Getter
    private final ValidationsBuilder validationsBuilder = new ValidationsBuilder(this);
    @Getter
    private final PatternComponentQualifiersBuilder patternComponentQualifiersBuilder = new PatternComponentQualifiersBuilder(this);
    @Getter
    private final PatternComponentAdjacentsBuilder patternComponentAdjacentsBuilder = new PatternComponentAdjacentsBuilder(this);
    @Getter
    private final PatternComponentsBuilder patternComponentsBuilder = new PatternComponentsBuilder(this);
    @Getter
    private final ConstantComponentsBuilder constantComponentsBuilder = new ConstantComponentsBuilder(this);
    @Getter
    private final SubmissionFileNameBuilder submissionFileNameBuilder = new SubmissionFileNameBuilder(this);
    @Getter
    private final SubmissionBuilder submissionBuilder = new SubmissionBuilder(this);
    @Getter
    private final AuthorizationBuilder authorizationBuilder = new AuthorizationBuilder(this);
    private final DataBuilder dataBuilder = new DataBuilder(this);
    @Getter
    private final FieldBuilder fieldBuilder = new FieldBuilder(this);
    private final RightsRequestBuilder rightsRequestBuilder = new RightsRequestBuilder(this);
    private final AdditionalFilesBuilder additionalFilesBuilder = new AdditionalFilesBuilder(this);

    @Getter
    private final SubmissionComponentResolver submissionComponentResolver = new SubmissionComponentResolver(this);
    private final DataAndComponentTestDoublon dataAndComponentTestDoublon = new DataAndComponentTestDoublon(this);
    @Getter
    Set<Tag> domainTags = Set.of();
    private ReactiveProgression.Progression progression;
    private boolean hasErrors;
    @Getter
    final Consumer<ValidationParams> buildErrorWithValidationParams =
            validationParams -> buildError(validationParams.exception(), validationParams.params(), validationParams.path());

    public RootBuilder(final ReactiveProgression.Progression progression,
                       final JsonNode rootNode,
                       final DocumentContext documentContext) {
        super();
        this.progression = progression;
        this.rootNode = rootNode;
    }

    static ComponentPresenceConstraint isMandatory(final JsonNode node) {
        return Optional.ofNullable(node.get(OA_MANDATORY))
                .map(JsonNode::asBoolean)
                .orElse(false) ?
                ComponentPresenceConstraint.MANDATORY :
                ComponentPresenceConstraint.OPTIONAL;
    }

    public void buildError(final ConfigurationException exception, Map<String, Object> params, final String path) {
        hasErrors = true;
        Map<String, Object> params1 = new HashMap<>(params);
        params1.put("path", path);
        progression.pushError(exception, params1);
    }

    public void buildError(final ConfigurationException exception, final String path) {
        hasErrors = true;
        final Map<String, String> params = Map.of("path", path);
        progression.pushError(exception, params);
    }

    public Configuration build(final byte[] bytes, final String comment) {
        final JsonNode versionNode = rootNode.get(OA_VERSION);
        final Version version = getAndTestOpenAdomVersion(versionNode);
        if (version == null) return null;
        new NodeSchemaValidator(this).testSchema(RootType.EMPTY_INSTANCE(), rootNode, "").get();
        dataAndComponentTestDoublon.testUniqueComponentsForData(rootNode.findPath(OA_DATA));
        if (hasErrors || dataAndComponentTestDoublon.hasErrors()) {
            return null;
        }
        progression.pushMessage("Starting parsing of configuration", Map.of());
        progression = progression.incrementAndPush(operand -> 0D);
        progression.incrementAndPush(D -> 0D);
        I18n i18n = new I18n(new HashMap<>());
        Parsing<ApplicationDescription> applicationDescription;
        final Optional<JsonNode> oaApplication = Optional.ofNullable(rootNode.get(OA_APPLICATION));

        applicationDescription = applicationdescriptionBuilder.build(
                oaApplication.orElse(null),
                i18n,
                comment);
        i18n = Optional.ofNullable(applicationDescription).map(Parsing::i18n).orElse(i18n);
        final Parsing<Set<Tag>> tags = tagsBuilder.buildDomainTagsOfApplication(OA_TAGS, rootNode.get(OA_TAGS), i18n);
        i18n = tags.i18n();
        domainTags = tags.result();
        final Parsing<Map<String, StandardDataDescription>> data = buildAllData(OA_DATA, rootNode.findPath(OA_DATA), i18n);
        i18n = data.i18n();
        final Parsing<RightRequestDescription> rightRequest = rightsRequestBuilder.build(rootNode.findPath(OA_RIGHTS_REQUEST), i18n);
        i18n = rightRequest.i18n();
        final Parsing<Map<String, AdditionalFileDescription>> aditionnalFiles = additionalFilesBuilder.buildAdditionalFiles(rootNode.findPath(OA_ADDITIONAL_FILES), i18n);
        i18n = aditionnalFiles.i18n();
        SortedSet<Node> hierarchicalNodes = new TreeSet<>();
        try {
            hierarchicalNodes = HierarchicalDependancesBuilder.of(
                            checkers,
                            data.result(),
                            domainTags
                    )
                    .build(buildErrorWithValidationParams);
        } catch (BadApplicationConfigurationException badApplicationConfigurationException) {
            buildError(
                    badApplicationConfigurationException.getConfigurationException(),
                    Map.of("nodeName", badApplicationConfigurationException.getMessage()),
                    OA_DATA);
        }
        final Internationalizations internationalizations = mapper
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(
                        i18n.i18n(), Internationalizations.class);
        if (hasErrors) {
            return null;
        }
        return new Configuration(
                version,
                tags.result(),
                internationalizations,
                Objects.requireNonNull(applicationDescription).result(),
                data.result(),
                rightRequest.result(),
                aditionnalFiles.result(),
                hierarchicalNodes,
                SubmissionComponentResolver.build(getListDataKeys())
        );
    }

    @Nullable
    private Version getAndTestOpenAdomVersion(final JsonNode versionNode) {
        if (versionNode == null) {
            buildError(
                    ConfigurationException.MISSING_VERSION_APPLICATION,
                    Map.of("actualVersion", Configuration.OPEN_ADOM_VERSION_PATTERN),
                    ""
            );
            return null;
        }
        if (versionNode.isMissingNode() || versionNode.isNull()) {
            buildError(ConfigurationException.MISSING_VERSION_APPLICATION,
                    Map.of("expectedVersion", Configuration.OPEN_ADOM_VERSION.version()),
                    OA_VERSION);
            return null;
        }
        Version version;
        try {
            version = Optional.of(versionNode)
                    .filter(JsonNode::isTextual)
                    .map(JsonNode::asText)
                    .map(Version::new)
                    .orElse(Version.BAD_VERSION);
        } catch (final SiOreIllegalArgumentException e) {
            version = Version.BAD_VERSION;
        }
        if (!version.equals(Configuration.OPEN_ADOM_VERSION)) {
            buildError(ConfigurationException.UNSUPPORTED_OPENADOM_VERSION,
                    Map.of("actualVersion", versionNode.asText(),
                            "expectedVersion", Configuration.OPEN_ADOM_VERSION.version()),
                    OA_VERSION);
            return null;
        }
        return version;
    }

    Parsing<String> addExportHeaders(final String key, I18n i18n, final Map.Entry<String, JsonNode> componentEntry, final String componentSectionName) {
        final JsonNode exportHeaderNode = componentEntry.getValue().findPath(OA_EXPORT_HEADER);
        if (!exportHeaderNode.isMissingNode()) {
            final Map oaI18n = Optional.of(exportHeaderNode)
                    .map(eh -> getMapper().convertValue(eh, Map.class))
                    .orElse(null);
            if (oaI18n != null) {
                try {
                    i18n = i18n.add(
                            NodeSchemaValidator.joinI18nPath(
                                    Internationalizations.DATA,
                                    key,
                                    InternationalizationData.COMPONENTS,
                                    componentEntry.getKey(),
                                    InternationalizationComponent.EXPORT_HEADER
                            ), oaI18n);
                } catch (final IllegalArgumentException illegalArgumentException) {
                    buildError(
                            ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                            Map.of(),
                            NodeSchemaValidator.joinPath(
                                    OA_DATA,
                                    key,
                                    componentSectionName,
                                    componentEntry.getKey(),
                                    OA_EXPORT_HEADER,
                                    OA_I_18_N
                            )
                    );
                }
            }
            return new Parsing<>(i18n, Optional.ofNullable(exportHeaderNode.get(OA_HEADER_NAME)).map(JsonNode::asText).orElse(componentEntry.getKey()));
        }
        return new Parsing<>(i18n, null);
    }

    private Parsing<Map<String, StandardDataDescription>> buildAllData(final String path, final JsonNode oaData, I18n i18n) {
        final Iterator<Map.Entry<String, JsonNode>> componentIterator = oaData.fields();
        final Map<String, StandardDataDescription> result = new HashMap<>();
        while (componentIterator.hasNext()) {
            final Map.Entry<String, JsonNode> componentEntry = componentIterator.next();
            final String key = componentEntry.getKey();
            final Parsing<StandardDataDescription> component = dataBuilder.build(

                    NodeSchemaValidator.joinPath(
                            path,
                            key
                    ), key,
                    componentEntry.getValue(),
                    i18n);
            i18n = component.i18n();
            testNaturalKeyIsOnlyOne(component.result(), key);
            result.put(key, component.result());
        }
        return new Parsing<>(i18n, result);
    }

    private void testNaturalKeyIsOnlyOne(StandardDataDescription result, String dataName) {
        final List<String> manyComponentInNaturalKey = result.naturalKey().stream()
                .map(result.componentDescriptions()::get)
                .filter(componentDescription -> Optional.ofNullable(componentDescription).map(ComponentDescription::checker).isPresent())
                .filter(componentDescription -> componentDescription.checker().multiplicity() == Multiplicity.MANY)
                .map(ComponentDescription::componentKey)
                .toList();
        if(CollectionUtils.isNotEmpty(manyComponentInNaturalKey)){
            buildError(
                    ConfigurationException.MANY_COMPONENT_IN_NATURAL_KEY,
                    Map.of(
                            "dataName", dataName,
                            "manyComponents", manyComponentInNaturalKey
                    ),
                    NodeSchemaValidator.joinPath(
                            OA_DATA,
                            dataName
                    )
            );
        }
    }

    ReactiveProgression.Progression getProgression() {
        return progression;
    }

    ObjectMapper getMapper() {
        return mapper;
    }

    Map<CheckerDescription.CheckerDescriptionType, Map<String, Map<String, List<CheckerDescription>>>> getCheckers() {
        return checkers;
    }

    List<String> getListDataKeys() {
        return dataAndComponentTestDoublon.listDataKeys();
    }

    public List<String> getListComponentKeys(final String dataKey) {
        return dataAndComponentTestDoublon.listReferencableComponentKeysByDataKey().getOrDefault(dataKey, List.of());
    }

    public List<Locale> getLangRestrictions(String componentPath, JsonNode componentNodeValue) {
        return Optional.ofNullable(componentNodeValue)
                .map(node -> node.get(OA_LANG_RESTRICTIONS))
                .map(restrictionNode -> getMapper().convertValue(restrictionNode, List.class))
                .map(locales -> testLocales(
                        NodeSchemaValidator.joinPath(
                                componentPath,
                                OA_LANG_RESTRICTIONS
                        ),
                        locales
                ))
                .orElseGet(List::of);
    }

    private List<Locale> testLocales(String path, List list) {
        List<Locale> localesList = new ArrayList<>();
        for (Object o : list) {
            Locale locale = Locale.of(o.toString());
            if (locale == null) {
                buildError(
                        ConfigurationException.BAD_LOCALE,
                        Map.of("locale", o.toString()),
                        path
                );
                continue;
            }
            localesList.add(locale);
        }
        return localesList;
    }
}