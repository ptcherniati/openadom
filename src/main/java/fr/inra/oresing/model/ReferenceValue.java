package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class ReferenceValue extends OreSiEntity {
    private UUID referenceType;
    private String label;
}
