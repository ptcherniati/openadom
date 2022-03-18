package fr.inra.oresing.checker;

public interface CheckerTarget {

    String getInternationalizedKey(String key);

    /**
     * @deprecated utilisé dans le front? On devrait plutôt utilisé l'héritage.
     */
    @Deprecated
    CheckerTargetType getType();

    enum CheckerTargetType {
        PARAM_VARIABLE_COMPONENT_KEY("variableComponentKey"),PARAM_COLUMN("column");

        private final String type;

        CheckerTargetType(String type) {
            this.type = type;
        }

        String getType() {
            return this.type;
        }

        @Override
        public String toString() {
            return type;
        }
    }
}
