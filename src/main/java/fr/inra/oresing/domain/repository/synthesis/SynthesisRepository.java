package fr.inra.oresing.domain.repository.synthesis;

import java.util.UUID;

public interface SynthesisRepository {
    int removeSynthesisByApplicationDatatype(UUID id, String dataType);

    int removeSynthesisByApplicationDatatypeAndVariable(UUID id, String dataType, String component);
}
