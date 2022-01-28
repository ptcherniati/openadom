package fr.inra.oresing.checker;

public interface GroovyLineCheckerConfiguration extends LineCheckerConfiguration {
    String getExpression();

    String getReferences();

    String getDatatypes();

    String getVariable();
    String getCodeVariable();
    String getComponent();
    String getDatatype();
}
