package fr.inra.oresing.domain.data.deposit;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.file.FileOrUUID;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


public record PublishContext(
        FileOrUUID fileOrUUID,
        HeaderInfos headerInfos,
        RowInfos rowInfos
) {
    public record HeaderInfos(
            List<List<String>> preHeaderRow,
            List<List<String>> postHeaderRow,
            List<String> headerRow
    ) {
    }

    public record RowInfos(List<String> currentRow, long currentRowNumber) {
        public RowInfos {
            Objects.requireNonNull(currentRow);
            if (currentRowNumber < 1) {
                throw new IllegalArgumentException("must be a positive line number");
            }
        }
    }

    public static class PublishContextBuilder {
        final FileOrUUID fileOrUUID;
        @Getter
        final Application application;
        @Getter
        final String dataName;
        final ConcurrentHashMap<String, List<DataValue>> dataValuesByReference = new ConcurrentHashMap<>();
        private final Function<String, List<DataValue>> getDatavaluesByReference;
        List<List<String>> preHeaderRow;
        List<List<String>> postHeaderRow;
        List<String> headerRow;
        RowInfos rowInfos;

        /**
         * Cache de la partie statique du contexte Groovy ( tout sauf
         * {@code currentRow} / {@code currentRowNumber} ) , calcule a la
         * premiere invocation de {@link #getGroovyContextForReferences} et
         * reutilise pour toutes les lignes suivantes.
         *
         * <p>Avant ce fix , la methode reconstruisait l'integralite de la
         * map ( references , referencesValues , application , dataI18n , ... )
         * pour chaque ligne du CSV - ~16 entries dont 2 maps construites
         * par parcours des references chargees. Sur 274k lignes c'etait
         * un des hot paths les plus couteux du pipeline transform.
         *
         * <p>Cle = identite Set ( {@code System.identityHashCode} ) du
         * groovyReferences passe en parametre - le caller passe le meme
         * set sur tous les appels d'un meme workflow , l'identityHashCode
         * suffit a invalider correctement si l'API change un jour.
         */
        private final AtomicReference<Map<String, Object>> cachedStaticContext = new AtomicReference<>();
        private volatile int                 cachedStaticContextKey;

        public PublishContextBuilder(Application application, String dataName, final FileOrUUID fileOrUUID, Function<String, List<DataValue>> getDatavaluesByReference) {
            super();
            this.application = application;
            this.dataName = dataName;
            this.fileOrUUID = fileOrUUID;
            this.getDatavaluesByReference = getDatavaluesByReference;
        }

        public BinaryFileDataset binaryFileDataset() {
            return Optional.ofNullable(fileOrUUID).map(FileOrUUID::binaryfiledataset).orElse(null);
        }

        public PublishContextBuilder withPreHeaderRow(final List<List<String>> preHeaderRow) {
            this.preHeaderRow = preHeaderRow;
            return this;
        }

        public PublishContextBuilder withHeaderRow(final List<String> headerRow) {
            this.headerRow = headerRow;
            return this;
        }

        public PublishContextBuilder withPostHeaderRow(final List<List<String>> postHeaderRow) {
            this.postHeaderRow = postHeaderRow;
            return this;
        }

        public PublishContextBuilder withRowInfos(final RowInfos rowInfos) {
            this.rowInfos = rowInfos;
            return this;
        }

        public PublishContext build() {
            final HeaderInfos headerInfos = new HeaderInfos(preHeaderRow, postHeaderRow, headerRow);
            return new PublishContext(
                    fileOrUUID,
                    headerInfos,
                    rowInfos
            );
        }

        public Map<String, Object> getGroovyContextForReferences(
                final Set<String> groovyReferences,
                RowInfos rowInfos
        ) {
            // Calcul / lookup du staticContext memorise pour ce builder + ce
            // groovyReferences set. La cle d'invalidation est l'identite du
            // set ( meme reference d'objet -> meme contenu , garanti par
            // l'appelant qui passe le set du context a chaque ligne ).
            final int cacheKey = (groovyReferences == null) ? 0 : System.identityHashCode(groovyReferences);
            Map<String, Object> staticCtx = cachedStaticContext.get();
            if (staticCtx == null || cachedStaticContextKey != cacheKey) {
                staticCtx = buildStaticGroovyContext(groovyReferences);
                cachedStaticContext.set(staticCtx);
                cachedStaticContextKey = cacheKey;
            }
            // Partie dynamique : currentRow + currentRowNumber par ligne.
            // Si rowInfos est null ( cas rare ) , on retourne le static tel
            // quel sans surcharger - mais on copie pour ne pas exposer une
            // map qui serait par erreur mutee par le caller.
            if (rowInfos == null) {
                return staticCtx;
            }
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            builder.putAll(staticCtx);
            Optional.ofNullable(rowInfos).map(PublishContext.RowInfos::currentRow).ifPresent(currentRow -> builder.put("currentRow", currentRow));
            Optional.ofNullable(rowInfos).map(PublishContext.RowInfos::currentRowNumber).ifPresent(currentRowNumber -> builder.put("currentRowNumber", currentRowNumber));
            return builder.build();
        }

        /**
         * Construit la partie statique ( par-workflow ) du contexte Groovy.
         * Appelee une seule fois par PublishContextBuilder ( memorise dans
         * {@link #cachedStaticContext} ) puis reutilisee a chaque ligne.
         */
        private Map<String, Object> buildStaticGroovyContext(final Set<String> groovyReferences) {
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            if (groovyReferences != null) {
                final Map<String, List<LineChecker.LineTransformer.ReferenceValueDecorator>> references = new HashMap<>();
                final Map<String, List<Map<String, Object>>> referencesValues = new HashMap<>();
                groovyReferences
                        .stream().filter(Objects::nonNull)
                        .forEach(reference -> {
                            final List<DataValue> allByReferenceType =
                                    dataValuesByReference.computeIfAbsent(reference, getDatavaluesByReference);
                            allByReferenceType.stream()
                                    .map(LineChecker.LineTransformer.ReferenceValueDecorator::new)
                                    .forEach(referenceValue -> references.computeIfAbsent(reference, k -> new LinkedList<>()).add(referenceValue));
                            allByReferenceType.stream()
                                    .map(DataValue::getRefValues)
                                    .forEach(values -> referencesValues.computeIfAbsent(reference, k -> new LinkedList<>()).add(values.toObjectsExposedInGroovyContext()));
                        });
                builder
                        .put("references", references)
                        .put("referencesValues", referencesValues);
            }
            Optional.of(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::fileOrUUID).map(FileOrUUID::binaryfiledataset).ifPresent(binaryFileDataset -> builder.put("binaryFile", binaryFileDataset));
            Optional.of(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::preHeaderRow).ifPresent(preHeaderRow -> builder.put("preHeaderRow", preHeaderRow));
            Optional.of(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::headerRow).ifPresent(headerRow -> builder.put("headerRow", headerRow));
            Optional.of(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::postHeaderRow).ifPresent(postHeaderRow -> builder.put("postHeaderRow", postHeaderRow));
            Optional.of(this).map(PublishContext.PublishContextBuilder::getDataName).ifPresent(dataName -> builder.put("dataName", dataName));
            Optional.of(this).map(PublishContext.PublishContextBuilder::getApplicationName).ifPresent(applicationName -> builder.put("applicationName", applicationName));
            Optional.of(this).map(PublishContext.PublishContextBuilder::getDataName)
                    .flatMap(application.getConfiguration()::findData)
                    .ifPresent(dataDescription -> builder.put("dataDescription", dataDescription));
            Optional.of(this).map(PublishContext.PublishContextBuilder::getDataName)
                    .flatMap(
                            dataName -> Optional.of(application)
                                    .map(Application::getConfiguration)
                                    .map(Configuration::i18n)
                                    .map(Internationalizations::getData)
                                    .map(m -> m.get(dataName)))
                    .ifPresent(dataI18n -> builder.put("dataI18n", dataI18n));
            builder.put("application", application);
            return builder.build();
        }

        public PublishContext build(List<String> currentrow, long lineNumber) {
            final HeaderInfos header = new HeaderInfos(preHeaderRow, postHeaderRow, headerRow);
            return new PublishContext(
                    fileOrUUID,
                    header,
                    new RowInfos(List.copyOf(currentrow), lineNumber)
            );
        }

        public PublishContext build(long lineNumber) {
            final HeaderInfos header = new HeaderInfos(preHeaderRow, postHeaderRow, headerRow);
            return new PublishContext(
                    fileOrUUID,
                    header,
                    rowInfos
            );
        }

        public Optional<BinaryFileDataset> getBinaryFile() {
            return Optional.ofNullable(fileOrUUID).map(FileOrUUID::binaryfiledataset);
        }

        public String getApplicationName() {
            return application.getName();
        }

    }
}