package fr.inra.oresing.domain.application.configuration.type;


import java.util.List;
import java.util.Map;

public class StaticMapType {
    final CollectionType type;

    StaticMapType(final CollectionType.MapType type) {
        super();
        this.type = type;
    }

    StaticMapType(final CollectionType.ArrayType type) {
        super();
        this.type = type;
    }

    public static final StaticMapType AUTHORIZATION_SCOPE() {
        return new StaticMapType(
                new CollectionType.MapType<IntegerType>(
                        Map.of(),
                        false,
                        false,
                        IntegerType.EMPTY_INSTANCE()
                )
        );
    }

    public static final StaticMapType I18N() {
        return new StaticMapType(
                new CollectionType.MapType<I18nType>(
                        Map.of(),
                        false,
                        false,
                        I18nType.EMPTY_INSTANCE()
                )
        );
    }
    public static final StaticMapType AUTHORIZATION_SCOPES() {
        return new StaticMapType(
                new CollectionType.ArrayType<StringType>(List.of(), false, false,
                        StringType.EMPTY_INSTANCE())
        );
    }



    public static final StaticMapType FILE_MATCH_PATTERN_SCOPES() {
        return new StaticMapType(
                new CollectionType.ArrayType<StringType>(
                        List.of(),
                        false,
                        false,
                        StringType.EMPTY_INSTANCE()
                )
        );
    }

    public static final StaticMapType REFERENCE_SCOPES() {
        return new StaticMapType(
                new CollectionType.ArrayType<ReferenceScopeType>(List.of(), false, false,
                        ReferenceScopeType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType REFERENCE_SCOPES_FOR_FILE() {
        return new StaticMapType(
                new CollectionType.ArrayType<StringType>(List.of(), false, false,
                        StringType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType VALIDATIONS() {
        return new StaticMapType(
                new CollectionType.MapType<ValidationType>(Map.of(), false, false,
                        ValidationType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType FORMATS() {
        return new StaticMapType(
                new CollectionType.MapType<FormatType>(Map.of(), false, false, FormatType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType ADDITIONAL_FILES() {
        return new StaticMapType(
                new CollectionType.MapType<AdditionalFileType>(Map.of(), false, false, AdditionalFileType.EMPTY_INSTANCE())
        );
    }
    public static final StaticMapType BASIC_COMPONENTS() {
        return new StaticMapType(new CollectionType.MapType<BasicComponentType>(Map.of(), false, false, BasicComponentType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType COMPUTED_COMPONENTS() {
        return new StaticMapType(
                new CollectionType.MapType<ComputedComponentType>(Map.of(), false, false, ComputedComponentType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType CONSTANT_COMPONENTS() {
        return new StaticMapType(
                new CollectionType.MapType<ConstantComponentType>(Map.of(), false, false, ConstantComponentType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType DYNAMIC_COMPONENTS() {
        return new StaticMapType(
                new CollectionType.MapType<DynamicComponentType>(Map.of(), false, false, DynamicComponentType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType PATTERN_COMPONENTS_QUALIFIERS() {
        return new StaticMapType(
                new CollectionType.ArrayType<>(List.of(), false, false, PatternComponentQualifierType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType PATTERN_COMPONENTS_ADJACENT() {
        return new StaticMapType(
                new CollectionType.ArrayType<>(List.of(), false, false, PatternComponentAdjacentType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType PATTERN_COMPONENTS() {
        return new StaticMapType(
                new CollectionType.MapType<PatternComponentType>(Map.of(), false, false, PatternComponentType.EMPTY_INSTANCE())
        );
    }

    public static final StaticMapType DATA() {
        return new StaticMapType(
                new CollectionType.MapType<DataType>(
                        Map.of(),
                        false,
                        false,
                        DataType.EMPTY_INSTANCE()
                )
        );
    }

    public CollectionType type() {
        return type;
    }
}