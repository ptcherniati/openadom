package fr.inra.oresing.groovy;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public enum CommonExpression implements Expression<String> {

    /**
     * Une {@link Expression} qui retourne toujours chaîne vide.
     */
    EMPTY_STRING() {
        @Override
        public String evaluate(Map<String, Object> context) {
            return StringUtils.EMPTY;
        }

        @Override
        public String toString() {
            return super.toString() + ", soit une expression qui retourne toujours une chaîne de caractères vide";
        }
    }
}
