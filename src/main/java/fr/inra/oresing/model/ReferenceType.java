package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class ReferenceType extends OreSiEntity {
    private UUID application;
    private String description;
    private UUID binaryFile;
}
