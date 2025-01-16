package fr.inra.oresing.domain.checker;

public interface CheckerTarget {

    String getInternationalizedKey(String key);

    /**
     * @deprecated utilisé dans le front? On devrait plutôt utilisé l'héritage.
     */

    @Deprecated
    String toHumanReadableString();

    enum CheckerTargetType {
        PARAM_COMPONENT_KEY("componentKey"),PARAM_COLUMN("column");

        private final String type;

        CheckerTargetType(final String type) {
            this.type = type;
        }

        String getType() {
            return type;
        }

        @Override
        public String toString() {
            return type;
        }
    }
}
