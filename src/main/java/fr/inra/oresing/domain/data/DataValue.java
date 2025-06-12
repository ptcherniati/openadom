package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.application.configuration.Ltree;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class DataValue extends OreSiEntity {
    private UUID application;
    private String patternColumnName;
    private String ReferenceType;
    private Ltree hierarchicalKey;
    private Ltree naturalKey;
    private DataDatum refValues;
    private Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo;
    private UUID binaryFile;
    private Map referencingreferences;
    private LineIdentityPatternColumnName lineHierarchicalKeyPatternColumnName;
    private LineIdentityPatternColumnName lineNaturalKeyPatternColumnName;
    private Authorization authorization;

    public LineIdentityPatternColumnName buildLineIdentityPatternColumnName() {
        return new LineIdentityPatternColumnName(
                new LineIdentityColumnName(getNaturalKey(), getHierarchicalKey()),
                getPatternColumnName()
        );
    }

    public record LineIdentityColumnName(
            Ltree naturalKey,
            Ltree hierarchicalKey
    ) {
    }

    public record LineIdentityPatternColumnName(LineIdentityColumnName identity, String patternColumnName) {
    }
}