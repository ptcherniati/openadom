package fr.inra.oresing.domain.groovy.predefined.builder.naturalkey;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.groovy.GroovyDecorator;
import fr.inra.oresing.domain.groovy.exception.GroovyException;

import java.util.*;

/**
 * Constructeur de clés naturelles pour gérer les références hiérarchiques.
 */
public record NaturalKeyBuilder(
        Map<String, Object> datum,
        Map<String, List<GroovyDecorator>> references,
        List<Step> steps
) {

    public static final String DATUM = "datum";
    public static final String REFERENCES = "references";

    public NaturalKeyBuilder(Map<String, Object> context) {
        this(
                (Map<String, Object>) (context.containsKey(DATUM) ? context.get(DATUM) : new HashMap<>()),
                context.containsKey(REFERENCES) ? ((Map<String, List<GroovyDecorator>>) context.get(REFERENCES)) : new HashMap<>(),
                new ArrayList<>()
        );
    }

    public DatumStep forDatumField(String datumField) {
        return new DatumStep(this, datumField);
    }

    public String naturalKey() {
        StringBuilder key = new StringBuilder();
        for (Step step : steps) {
            if (!key.isEmpty()) {
                key.append("__");
            }
            String stepValue = step.build(datum, references, key.toString());
            key.append(stepValue);
        }
        return key.toString();
    }

    private record Step(String datumField, String dataType, String keyField, String exceptionCode) {
        String build(Map<String, Object> datum, Map<String, List<GroovyDecorator>> references, String partialKey) {
            String valueCodified;

            try {
                String value = datum.get(datumField).toString();
                valueCodified = Ltree.fromUnescapedString(value).getSql();
            } catch (Exception e) {
                // Gestion de l'exception lors de la codification
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("value", datumField);
                errorDetails.put("parents", Arrays.asList(partialKey.split("__")));

                // Récupération des valeurs connues même si l'erreur est dans la codification
                List<String> knownValues = references.get(dataType).stream()
                        .map(GroovyDecorator::getRefValues)
                        .map(ref -> ref.get(keyField).toString())
                        .distinct()
                        .toList();
                errorDetails.put("knownValues", knownValues);

                throw new GroovyException(exceptionCode, errorDetails);
            }

            List<String> parents = Arrays.asList(partialKey.split("__"));

            Optional<Map<String, Object>> found = references.get(dataType).stream()
                    .filter(decorator -> Optional.of(decorator)
                            .map(GroovyDecorator::getRefValues)
                            .map(rv -> rv.get(keyField))
                            .map(Object::toString)
                            .map(Ltree::escapeToLabel)
                            .filter(valueCodified::equals)
                            .map(nk -> partialKey.isEmpty() ? nk : partialKey + nk)
                            .filter(decorator.getNaturalKey()::equals)
                            .isPresent()
                    )
                    .map(GroovyDecorator::getRefValues)
                    .findFirst();

            if (found.isEmpty()) {
                List<String> knownValues = references.get(dataType).stream()
                        .map(GroovyDecorator::getRefValues)
                        .map(ref -> Optional.of(ref).map(r -> r.get(keyField)).orElse("").toString())
                        .distinct()
                        .toList();

                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("value", datumField);
                errorDetails.put("parents", parents);
                errorDetails.put("knownValues", knownValues);

                throw new GroovyException(exceptionCode, errorDetails);
            }

            return valueCodified;
        }
    }

    public record DatumStep(NaturalKeyBuilder builder, String datumField) {
        public DataTypeStep onDataName(String dataName) {
            return new DataTypeStep(builder, datumField, dataName);
        }
    }

    public record DataTypeStep(NaturalKeyBuilder builder, String datumField, String dataName) {
        public KeyStep forKey(String keyField) {
            return new KeyStep(builder, datumField, dataName, keyField);
        }
    }

    public record KeyStep(NaturalKeyBuilder builder, String datumField, String dataType, String keyField) {
        public NaturalKeyBuilder withException(String exceptionCode) {
            List<Step> newSteps = new ArrayList<>(builder.steps);
            newSteps.add(new Step(datumField, dataType, keyField, exceptionCode));
            return new NaturalKeyBuilder(builder.datum, builder.references, newSteps);
        }
    }
}