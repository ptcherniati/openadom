package fr.inra.oresing.domain.data.deposit;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.file.FileOrUUID;

import java.util.*;
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
        private final Function<String, List<DataValue>> getDatavaluesByReference;
        Application application;
        String dataName;
        List<List<String>> preHeaderRow;
        List<List<String>> postHeaderRow;
        List<String> headerRow;
        RowInfos rowInfos;
        Map<String, List<DataValue>> dataValuesByReference = new HashMap<>();

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
            final HeaderInfos header = new HeaderInfos(preHeaderRow, postHeaderRow, headerRow);
            return new PublishContext(
                    fileOrUUID,
                    header,
                    rowInfos
            );
        }

        public Map<String, Object> getGroovyContextForReferences(
                final Set<String> groovyReferences,
                RowInfos rowInfos
        ) {
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
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::fileOrUUID).map(FileOrUUID::binaryfiledataset).ifPresent(binaryFileDataset -> builder.put("binaryFile", binaryFileDataset));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::preHeaderRow).ifPresent(preHeaderRow -> builder.put("preHeaderRow", preHeaderRow));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::headerRow).ifPresent(headerRow -> builder.put("headerRow", headerRow));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::postHeaderRow).ifPresent(postHeaderRow -> builder.put("postHeaderRow", postHeaderRow));
            Optional.ofNullable(rowInfos).map(PublishContext.RowInfos::currentRow).ifPresent(currentRow -> builder.put("currentRow", currentRow));
            Optional.ofNullable(rowInfos).map(PublishContext.RowInfos::currentRowNumber).ifPresent(currentRowNumber -> builder.put("currentRowNumber", currentRowNumber));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::getDataName).ifPresent(dataName -> builder.put("dataName", dataName));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::getApplicationName).ifPresent(applicationName -> builder.put("applicationName", applicationName));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::getDataName)
                    .flatMap(application.getConfiguration()::findData)
                    .ifPresent(dataDescription -> builder.put("dataDescription", dataDescription));
            Optional.ofNullable(this).map(PublishContext.PublishContextBuilder::getDataName)
                    .flatMap(
                            dataName->Optional.of(application)
                                    .map(Application::getConfiguration)
                                    .map(Configuration::i18n)
                                    .map(Internationalizations::getData)
                                    .map(m->m.get(dataName)))
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

        public Application getApplication() {
            return application;
        }

        public String getApplicationName() {
            return application.getName();
        }

        public String getDataName() {
            return dataName;
        }
    }
}
