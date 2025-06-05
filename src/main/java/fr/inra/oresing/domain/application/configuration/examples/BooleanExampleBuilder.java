package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.type.BooleanType;

class BooleanExampleBuilder {
    private BooleanExampleBuilder() {
    }

    protected static final BooleanType TRUE = new BooleanType(true);
    protected static final BooleanType FALSE = new BooleanType(false);
}