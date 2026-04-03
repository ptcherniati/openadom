package fr.inra.oresing.domain.groovy;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;

import java.util.*;

public class GroovyContextHelper {

    public static ImmutableMap<String, Object> getGroovyContextForReferences(final DataRepository referenceValueRepository, final Set<String> refs, final PublishContext.PublishContextBuilder publishContextBuilder) {
        final Map<String, List<ReferenceValueDecorator>> references = new HashMap<>();
        final Map<String, List<Map<String, Object>>> referencesValues = new HashMap<>();
        refs.forEach(ref -> {
            final List<DataValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(ref);
            allByReferenceType.stream()
                    .map(ReferenceValueDecorator::new)
                    .forEach(referenceValue -> references.computeIfAbsent(ref, k -> new LinkedList<>()).add(referenceValue));
            allByReferenceType.stream()
                    .map(DataValue::getRefValues)
                    .forEach(values -> referencesValues.computeIfAbsent(ref, k -> new LinkedList<>()).add(values.toObjectsExposedInGroovyContext()));
        });
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        builder
                .put("references", references)
                .put("referencesValues", referencesValues);
        Optional.ofNullable(publishContextBuilder).map(PublishContext.PublishContextBuilder::build).map(PublishContext::fileOrUUID).map(FileOrUUID::binaryfiledataset).ifPresent(binaryFileDataset -> builder.put("binaryFile", binaryFileDataset));
        Optional.ofNullable(publishContextBuilder).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::preHeaderRow).ifPresent(binaryFileDataset -> builder.put("preHeaderRow", binaryFileDataset));
        Optional.ofNullable(publishContextBuilder).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::headerRow).ifPresent(binaryFileDataset -> builder.put("headerRow", binaryFileDataset));
        Optional.ofNullable(publishContextBuilder).map(PublishContext.PublishContextBuilder::build).map(PublishContext::headerInfos).map(PublishContext.HeaderInfos::postHeaderRow).ifPresent(binaryFileDataset -> builder.put("postHeaderRow", binaryFileDataset));
        Optional.ofNullable(publishContextBuilder).map(PublishContext.PublishContextBuilder::build).map(PublishContext::rowInfos).map(PublishContext.RowInfos::currentRow).ifPresent(binaryFileDataset -> builder.put("currentRow", binaryFileDataset));
        Optional.ofNullable(publishContextBuilder).map(PublishContext.PublishContextBuilder::build).map(PublishContext::rowInfos).map(PublishContext.RowInfos::currentRowNumber).ifPresent(binaryFileDataset -> builder.put("currentRowNumber", binaryFileDataset));
        return builder.build();
    }

    /**
     * On expose pas directement les entités dans le contexte Groovy mais on contrôle un peu les types
     */
    public record ReferenceValueDecorator(DataValue decorated) implements GroovyDecorator {

        @Override
        public String getHierarchicalKey() {
            return decorated.getHierarchicalKey().getSql();
        }

        @Override
        public String getNaturalKey() {
            return decorated.getNaturalKey().getSql();
        }

        @Override
        public Map<String, Object> getRefValues() {
            return decorated.getRefValues().toObjectsExposedInGroovyContext();
        }
    }
}