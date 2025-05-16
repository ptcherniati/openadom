package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.Map;

class FormatExampleBuilder {
    protected static final FormatType PROJET = buildFormFieldsSchema(
            TitleExampleBuilder.PROJET,
            true,
            ReferenceCheckerExampleBuilder.buildReferenceChecker("tr_projet_pro", Multiplicity.MANY, false, false)
    );
    protected static final FormatType NOM = buildFormFieldsSchema(
            TitleExampleBuilder.NOM,
            true,
            StringCheckerExampleBuilder.buildStringChecker("\"[a-z]*\"", Multiplicity.ONE)
    );
    protected static final FormatType START_DATE = buildFormFieldsSchema(
            TitleExampleBuilder.START_DATE,
            true,
            DateCheckerExampleBuilder.DDMMYYYY
    );
    protected static final FormatType ORGANISME = buildFormFieldsSchema(
            TitleExampleBuilder.ORGANISME,
            false,
            StringCheckerExampleBuilder.ALL
    );

    protected static FormatType buildFormFieldsSchema(final TitleType title, final boolean required, final CheckerType checker) {
        final Map<String, ConfigurationSchemaNodeType<?>> children = new HashMap<>();
        children.put(ConfigurationSchemaNode.OA_I_18_N, title);
        if (required) {
            children.put(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(true));
        }
        children.put(ConfigurationSchemaNode.OA_CHECKER, checker);
        return new FormatType(children);
    }
}