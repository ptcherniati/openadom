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

    public void setCompositeKey(String compositeKey) {
        this.compositeKey = compositeKey;
    }

    private String compositeKey;
    private Map<String, String> refValues;
    private UUID binaryFile;
}
