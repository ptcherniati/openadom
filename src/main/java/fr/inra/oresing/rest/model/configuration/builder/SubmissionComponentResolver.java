package fr.inra.oresing.rest.model.configuration.builder;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record SubmissionComponentResolver(RootBuilder rootBuilder) {
    static List<String> build(final List<String> dataKeys) {
        return dataKeys;
    }

    public Submission.SubmissionScope.ReferenceScope resolveComponent(String scopePath, Submission.SubmissionScope.ReferenceScope referenceScope, Map<String, ComponentDescription> componentDescriptions) {
        return verifyComponentBySubmission(scopePath, referenceScope, componentDescriptions);

    }

    Submission.SubmissionScope.ReferenceScope verifyComponentBySubmission(
            final String componentPath,
            final Submission.SubmissionScope.ReferenceScope referenceScope,
            final Map<String, ComponentDescription> componentDescriptions
    ) {
        final String reference = referenceScope.reference();
        final String componentName = referenceScope.component();
        final ComponentDescription componentDescription = componentDescriptions.get(componentName);
        try {
            Submission.SubmissionScope.ReferenceScope referenceScopeFromComponent = findReferenceScopeFromComponent(componentDescription, reference);
            if (referenceScopeFromComponent != null) {
                return referenceScopeFromComponent;
            }
            if (Strings.isNullOrEmpty(reference)) {
                throw new SiOreConfigurationFormatException(
                        ConfigurationException.MISSING_REFERENCE_NAME,//TODO
                        Map.of("componentName", componentName)
                );
            }
            SubmissionConstantHeader submissionConstantHeader = new SubmissionConstantHeader(
                    ConstantImportHeader.ConstantImportHeaderType.SubmissionComponent
            );
            ReferenceChecker referenceChecker = new ReferenceChecker(
                    CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                    componentName,
                    Multiplicity.ONE,
                    true,
                    reference,
                    false,
                    false
            );
            ComponentDescription submissionConstantHeaderComponentDescription = new ConstantComponent(
                    ComponentDescription.ComponentDescriptionType.ConstantComponent,
                    componentName,
                    null,
                    Set.of(),
                    true,
                    ComponentPresenceConstraint.MANDATORY,
                    referenceChecker,
                    submissionConstantHeader,
                    componentName,
                    reference
            );
            componentDescriptions.put(componentName, submissionConstantHeaderComponentDescription);
            return new Submission.SubmissionScope.ReferenceScope(reference, componentName);
        } catch (SiOreConfigurationFormatException configurationFormatException) {
            rootBuilder().buildError(configurationFormatException.getException(),
                    configurationFormatException.getParams(),
                    componentPath);
        }
        return referenceScope;
    }

    private Submission.SubmissionScope.ReferenceScope findReferenceScopeFromComponent(ComponentDescription componentDescription, String reference) {
        Optional<String> reftype = Optional.ofNullable(componentDescription)
                .flatMap(ComponentDescription::findReferenceCheckerType);
        if (reftype.isEmpty()) {
            return null;
        }
        if (reftype.get().equals(reference)) {
            return new Submission.SubmissionScope.ReferenceScope(reference, componentDescription.componentKey());
        }
        throw new SiOreConfigurationFormatException(
                ConfigurationException.INVALID_COMPONENT_REFERENCE_FOR_SUBMISSION_SCOPE_REFERENCE,
                Map.of(
                        "submissionReference", reftype.get(),
                        "componentReference", reference
                )
        );
    }
}