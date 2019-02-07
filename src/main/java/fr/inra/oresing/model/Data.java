package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class Data extends OreSiEntity {
    private UUID application;
    private String dataType;
    private List<UUID> refsLinkedTo;
    private Map<String, String> dataValues;
    private UUID binaryFile;
}
