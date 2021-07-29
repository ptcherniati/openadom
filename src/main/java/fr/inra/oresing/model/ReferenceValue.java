package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class ReferenceValue extends OreSiEntity {
    private UUID application;
    private String referenceType;
    private String hierarchicalKey;
    private String naturalKey;
    private Map<String, String> refValues;
    private Map<String, UUID> refsLinkedTo;
    private UUID binaryFile;
}
