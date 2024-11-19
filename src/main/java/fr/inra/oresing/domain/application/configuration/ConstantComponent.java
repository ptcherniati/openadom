package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ConstantComponent(
        ComponentDescriptionType type,
        String componentKey,
        ComputationChecker defaultValue,
        Set<Tag> tags,
        boolean required,
        ComponentPresenceConstraint mandatory, CheckerDescription checker,
        ConstantImport constantImportHeader,
        String exportHeaderName,
        String submissionAuthorizationScope) implements ComponentDescription {
    @Override
    public List<Locale> langRestrictions() {
        return List.of();
    }

    public ComponentDescription withSubmission(final String submission) {
        return new ConstantComponent(type(), componentKey(), defaultValue(), tags(), required(), mandatory(), checker(), constantImportHeader(), exportHeaderName(), submission);
    }
    public int rowNumber(){
        return switch (constantImportHeader()){
            case ConstantImportHeader constantImportHeader -> constantImportHeader.rowNumber();
            case SubmissionConstantHeader submissionConstantHeader -> throw new IllegalArgumentException("no row number for submissionComponent");
        };
    }
}
