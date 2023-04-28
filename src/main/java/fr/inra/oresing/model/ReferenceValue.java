package fr.inra.oresing.model;

import fr.inra.oresing.persistence.Ltree;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class ReferenceValue extends OreSiEntity {
    private UUID application;
    private String referenceType;
    private Ltree hierarchicalKey;
    private Ltree hierarchicalReference;
    private Ltree naturalKey;
    private ReferenceDatum refValues;
    private Map<String, Set<UUID>> refsLinkedTo;
    private UUID binaryFile;
    private Map<String, Map<String, List<String>>> referencingreferences;
}