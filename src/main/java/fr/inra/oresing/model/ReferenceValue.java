package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class ReferenceValue extends OreSiEntity {
    private UUID application;
    private String referenceType;
    private String hierarchicalKey;
    private String hierarchicalReference;
    private String naturalKey;
    private Map<String, String> refValues;
    private Map<String, Set<UUID>> refsLinkedTo;
    private UUID binaryFile;
}