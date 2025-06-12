package fr.inra.oresing.domain.application.configuration.type;

public sealed interface ApplicationType<T>
        extends IntermediaryType<T>
        permits AdditionalFileType, ApplicationDescriptionType, ApplicationType.ComponentType, AuthorizationType, CheckerType, ConstantImportHeaderType, DataType, DatagroupType, FileNameType, FormatType, I18nType, ReferenceScopeType, ReferenceType, RightRequestType, RootType, SubmissionScopeType, SubmissionTimeScopeType, SubmissionType, TagType, TimeScopeType, TitleType, ValidationType {

    sealed interface ComponentType<T> extends ApplicationType<T> permits BasicComponentType, ComputedComponentType, ConstantComponentType, DynamicComponentType, PatternComponentAdjacentType, PatternComponentQualifierType, PatternComponentType {

    }
}