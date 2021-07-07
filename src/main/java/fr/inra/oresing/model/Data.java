package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class Data extends OreSiEntity {
    private UUID application;
    private String dataType;
    private String rowId;
    private String dataGroup;
    private Map<String, String> requiredAuthorizations;
    private LocalDateTimeRange timeScope;
    private Map<String, Map<String, UUID>> refsLinkedTo;
    private Map<String, Map<String, String>> dataValues;
    private UUID binaryFile;
}
