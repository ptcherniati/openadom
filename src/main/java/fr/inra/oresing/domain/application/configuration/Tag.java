package fr.inra.oresing.domain.application.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public sealed interface Tag {
    String RESERVED_TAG_NAME = "RESERVED_TAG_NAME";
    String BAD_TAG_NAME = "BAD_TAG_NAME";
    String BAD_TAGS_FORMAT = "BAD_TAGS_FORMAT";


    static Tag buildTag(final String tagName) {
        return Arrays.stream(TagDefinitions.values())
                .filter(tagDefinition -> tagDefinition.isA.test(tagName))//y'a t-il un tagDefinition acceptable
                .map(tagDefinition -> tagDefinition.build.apply(tagName))
                .findFirst()
                .orElseThrow(() -> new SiOreConfigurationFormatException(ConfigurationException.BAD_DOMAIN_TAG_PATTERN,
                        Map.of("tagName", tagName,
                                "domainPatternTags", DomainTag.DOMAIN_PATTERN
                        )));
    }

    static LinkedHashSet<Tag> buildTags(final Set<String> tagNames, final Validation validation) {
        assert validation != null;
        try {
            if (CollectionUtils.isEmpty(tagNames)) {
                return new LinkedHashSet<>(Set.of(NoTag.instance()));
            }
            return tagNames.stream()
                    .map(Tag::buildTag)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (final SiOreConfigurationFormatException e) {
            final boolean onlyDomainTags = Optional.ofNullable(validation)
                    .map(Validation::params)
                    .map(p -> (Boolean) p.getOrDefault("onlyDomainTags", false))
                    .orElse(false);
            if (onlyDomainTags) {
                validation.buildError(ConfigurationException.BAD_DOMAIN_TAG_PATTERN, Map.of("domainTagPattern", DomainTag.DOMAIN_PATTERN));
                return buildTags(Set.of(), validation);
            }
            final Set<String> acceptedTagPatterns = new HashSet<>(TagDefinitions.getReservedTagPatterns());
            Optional.ofNullable(validation)
                    .map(Validation::params)
                    .filter(MapUtils::isNotEmpty)
                    .map(map -> (Set<String>) map.get("domainTags"))
                    .ifPresent(acceptedTagPatterns::addAll);
            Objects.requireNonNull(validation).buildError(ConfigurationException.BAD_TAGS_PATTERNS, Map.of("acceptedTagPatterns", acceptedTagPatterns));
            return buildTags(Set.of(), validation);

        } catch (final Exception e) {
            validation.buildError(ConfigurationException.BAD_TAGS_PATTERNS, Map.of("format", "[a-z][a-z_0-9]{3,30}"));
            return buildTags(Set.of(), validation);
        }
    }

    @JsonProperty("tagDefinition")
    TagDefinitions tagDefinition();

    enum TagDefinitions {
        HIDDEN_TAG(HiddenTag.HIDDEN_TAG, w -> HiddenTag.instance(), HiddenTag.getTagPattern()),
        DATA_TAG(DataTag.DATA_TAG, w -> DataTag.instance(), DataTag.getTagPattern()),
        REFFERENCE_TAG(ReferenceTag.REFFERENCE_TAG, w -> ReferenceTag.instance(), ReferenceTag.getTagPattern()),
        ORDER_TAG(OrderTag.ORDER_TAG, OrderTag::buildOrderTag, OrderTag.getTagPattern()),
        FILTER_TEXT_TAG(FilterTextTag.FILTER_TEXT_TAG, w -> FilterTextTag.instance(), FilterTextTag.getTagPattern()),
        FILTER_LIST_TAG(FilterListTag.FILTER_LIST_TAG, w -> FilterListTag.instance(), FilterListTag.getTagPattern()),
        ORDER_STRICT_TAG(OrderStrictTag.ORDER_STRICT_TAG, w -> OrderStrictTag.instance(), OrderStrictTag.getTagPattern()),
        NO_TAG(NoTag.NO_TAG, w -> NoTag.instance(), NoTag.getTagPattern()),
        DOMAIN_TAG(DomainTag.DOMAIN_TAG, DomainTag::buildDomainTag, DomainTag.getTagPattern());
        final Predicate<String> isA;
        final Function<String, Tag> build;
        private final String tagPattern;

        TagDefinitions(final Predicate<String> isA, final Function<String, Tag> build, final String tagPattern) {
            this.isA = isA;
            this.build = build;
            this.tagPattern = tagPattern;
        }

        static Set<String> getAllTagPatterns() {
            return Arrays.stream(values())
                    .map(TagDefinitions::getTagPattern)
                    .collect(Collectors.toSet());
        }

        static Set<String> getReservedTagPatterns() {
            return Arrays.stream(values())
                    .filter(tagDefinitions -> tagDefinitions != DOMAIN_TAG)
                    .map(TagDefinitions::getTagPattern)
                    .collect(Collectors.toSet());
        }

        private String getTagPattern() {
            return tagPattern;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    sealed interface DefinedTag extends Tag permits DataTag, FilterTag, HiddenTag, NoTag, OrderTag, OrderStrictTag, ReferenceTag {

    }

    /**
     * Marqueur commun aux deux tags de filtrage utilisateur ( {@link FilterTextTag} et
     * {@link FilterListTag} ). Permet aux callers de tester "est-ce un filtre quel que
     * soit son mode" sans énumérer chaque sous-type ; les détails du mode ( texte vs
     * liste de valeurs distinctes ) restent portés par chaque record concret.
     */
    sealed interface FilterTag extends DefinedTag permits FilterTextTag, FilterListTag {
    }

    record DomainTag(TagDefinitions tagDefinition, String tagName) implements Tag {
        public static final String DOMAIN_PATTERN = "^[a-z][a-z_0-9]*[a-z0-9]$";
        public static final Predicate<String> DOMAIN_TAG = w -> Pattern.compile(DOMAIN_PATTERN).matcher(w).matches();

        public DomainTag(final String tagName) {
            this(TagDefinitions.DOMAIN_TAG, tagName);
        }

        public static DomainTag buildDomainTag(final String tagName) {
            final Matcher matcher = Pattern.compile(DOMAIN_PATTERN).matcher(tagName);
            if (matcher.matches()) {
                return new DomainTag(tagName);
            } else {
                throw new SiOreIllegalArgumentException("unExpected error", Map.of("comment", "on ne doit jamais arriver ici (a cause du filter)"));
            }
        }

        public static String getTagPattern() {
            return DOMAIN_PATTERN;
        }
    }

    record DataTag(TagDefinitions tagDefinition) implements DefinedTag {
        public static final String DATA_PATTERN = "__DATA__";
        public static final Predicate<String> DATA_TAG = DATA_PATTERN::equals;

        public DataTag() {
            this(TagDefinitions.DATA_TAG);
        }

        public static DataTag instance() {
            return new DataTag();
        }

        public static String getTagPattern() {
            return DATA_PATTERN;
        }

    }

    record ReferenceTag(TagDefinitions tagDefinition) implements DefinedTag {
        public static final String REFERENCE_PATTERN = "__REFERENCE__";
        public static final Predicate<String> REFFERENCE_TAG = REFERENCE_PATTERN::equals;

        public ReferenceTag() {
            this(TagDefinitions.REFFERENCE_TAG);
        }

        public static ReferenceTag instance() {
            return new ReferenceTag();
        }

        public static String getTagPattern() {
            return REFERENCE_PATTERN;
        }
    }

    /**
     * Tag opt-in posé sur une colonne dans {@code OA_tags} pour activer un filtre
     * de recherche **texte libre** côté frontend ( recherche LIKE insensible à la
     * casse via {@code @ like_regex ... flag "i"} en JSONPath PostgreSQL ).
     *
     * <p>Cible : colonnes texte ouvertes ( commentaires , codes libres , identifiants
     * externes... ) où l'utilisateur ne connait pas la valeur exacte mais une portion.
     *
     * <p>Ignoré silencieusement ( log INFO ) si la colonne porte aussi {@link HiddenTag}
     * ou un checker disposant déjà d'un filtre natif ( Date , Integer , Float , Boolean ,
     * Reference ). Voir
     * {@link ComponentDescription#isFilterableAsText()} pour la règle exacte.
     *
     * <p>Sérialisation : le frontend teste {@code tagDefinition === "FILTER_TEXT_TAG"}
     * sur l'objet {@link Tag} déjà exposé dans la {@link Configuration} JSON.
     */
    record FilterTextTag(TagDefinitions tagDefinition) implements FilterTag {
        public static final String FILTER_TEXT_PATTERN = "__FILTER_TEXT__";
        public static final Predicate<String> FILTER_TEXT_TAG = FILTER_TEXT_PATTERN::equals;

        public FilterTextTag() {
            this(TagDefinitions.FILTER_TEXT_TAG);
        }

        public static FilterTextTag instance() {
            return new FilterTextTag();
        }

        public static String getTagPattern() {
            return FILTER_TEXT_PATTERN;
        }
    }

    /**
     * Tag opt-in posé sur une colonne dans {@code OA_tags} pour activer un filtre
     * **dropdown searchable** côté frontend , alimenté par les valeurs distinctes
     * effectivement présentes dans la colonne ( cf. {@code ColumnDistinctValues} ).
     *
     * <p>Cible : colonnes à vocabulaire fini ( modalités , types , traitements... ) où
     * l'utilisateur sélectionne dans une liste fermée plutôt que de saisir librement.
     *
     * <p>Mêmes règles d'exclusion que {@link FilterTextTag} : ignoré si {@link HiddenTag}
     * ou checker à filtre natif. Voir {@link ComponentDescription#isFilterableAsList()}.
     *
     * <p>Limite côté backend : la liste des valeurs distinctes est tronquée à 10 000
     * entrées pour borner le coût de la dropdown. Au-delà , un drapeau
     * {@code truncated: true} est remonté au frontend qui affiche un bandeau.
     *
     * <p>Sérialisation : le frontend teste {@code tagDefinition === "FILTER_LIST_TAG"}.
     */
    record FilterListTag(TagDefinitions tagDefinition) implements FilterTag {
        public static final String FILTER_LIST_PATTERN = "__FILTER_LIST__";
        public static final Predicate<String> FILTER_LIST_TAG = FILTER_LIST_PATTERN::equals;

        public FilterListTag() {
            this(TagDefinitions.FILTER_LIST_TAG);
        }

        public static FilterListTag instance() {
            return new FilterListTag();
        }

        public static String getTagPattern() {
            return FILTER_LIST_PATTERN;
        }
    }

    record HiddenTag(TagDefinitions tagDefinition) implements DefinedTag {
        public static final Predicate<Collection<Tag>> HAS_HIDDEN_TAG_PREDICATE = tags -> tags.stream().anyMatch(Tag.HiddenTag.class::isInstance);
        public static final String HIDDEN_PATTERN = "__HIDDEN__";
        public static final Predicate<String> HIDDEN_TAG = HIDDEN_PATTERN::equals;

        public HiddenTag() {
            this(TagDefinitions.HIDDEN_TAG);
        }

        public static HiddenTag instance() {
            return new HiddenTag();
        }

        public static String getTagPattern() {
            return HIDDEN_PATTERN;
        }
    }

    record NoTag(TagDefinitions tagDefinition, String tagName) implements DefinedTag {
        public static final String NO_TAG_PATTERN = "no-tag";
        public static final Predicate<String> NO_TAG = NO_TAG_PATTERN::equals;

        public static NoTag instance() {
            return new NoTag(TagDefinitions.NO_TAG, "no_tag");
        }

        public static String getTagPattern() {
            return NO_TAG_PATTERN;
        }
    }

    record OrderTag(TagDefinitions tagDefinition, int tagOrder) implements DefinedTag {
        public static final Pattern ORDER_TAG_PATTERN = Pattern.compile("__ORDER_(\\d*)__");
        public static final Predicate<String> ORDER_TAG = w -> ORDER_TAG_PATTERN.matcher(w).matches();
        public static final OrderTag ORDER_TAG_NOUGHT = new OrderTag(0);
        public static final OrderTag ORDER_TAG_ONE = new OrderTag(1);
        public static final OrderTag ORDER_TAG_TWO = new OrderTag(2);
        public static final OrderTag ORDER_TAG_THREE = new OrderTag(3);
        public static final OrderTag ORDER_TAG_FOUR = new OrderTag(4);
        public static final OrderTag ORDER_TAG_FIVE = new OrderTag(5);

        public OrderTag(final int tagOrder) {
            this(TagDefinitions.ORDER_TAG, tagOrder);
        }

        public static OrderTag buildOrderTag(final String tagName) {
            final Matcher matcher = ORDER_TAG_PATTERN.matcher(tagName);
            if (matcher.matches()) {
                return Optional.ofNullable(matcher.group(1))
                        .map(Integer::parseInt)
                        .map(OrderTag::new)
                        .orElse(ORDER_TAG_NOUGHT);
            } else {
                throw new SiOreIllegalArgumentException("unExcpectedError", Map.of("Comment", "On ne doit pas arriver ici a cause du filter"));
            }
        }

        public static String getTagPattern() {
            return ORDER_TAG_PATTERN.pattern();
        }
    }

    /**
     * Tag système {@code __ORDER_STRICT__} indiquant que le fichier CSV est garanti
     * ordonné (parents avant enfants), ce qui permet d'activer le mode récursion
     * ordonnée sans configuration Spring Boot globale.
     *
     * <p>Exemple YAML :
     * <pre>{@code
     * OA_validations:
     *   nomDuTaxonSuperieur:
     *     OA_tags: [ __ORDER_STRICT__ ]
     *     OA_components: [ taxon_superieur ]
     *     OA_checker:
     *       OA_name: OA_reference
     *       OA_params:
     *         OA_reference:
     *           OA_name: taxon
     *           OA_isRecursive: true
     * }</pre>
     *
     * <p>Peut aussi être activé globalement via la propriété Spring
     * {@code cascade.import.ordered-recursion-mode=true}.
     */
    record OrderStrictTag(TagDefinitions tagDefinition) implements DefinedTag {
        public static final String ORDER_STRICT_PATTERN = "__ORDER_STRICT__";
        public static final Predicate<String> ORDER_STRICT_TAG = ORDER_STRICT_PATTERN::equals;

        public OrderStrictTag() {
            this(TagDefinitions.ORDER_STRICT_TAG);
        }

        public static OrderStrictTag instance() {
            return new OrderStrictTag();
        }

        public static String getTagPattern() {
            return ORDER_STRICT_PATTERN;
        }
    }

}