package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.checker.type.FieldType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Setter
@Getter
public class Data extends OreSiEntity {
    private UUID application;
    private String dataType;
    private String rowId;
    private Authorization authorization;
    private Map<String, Map<String, Set<UUID>>> refsLinkedTo;
    private Map<String, Map<String, FieldType<?>>> dataValues;
    private UUID binaryFile;
    private List<String> uniqueness;

    @Override
    public String toString() {
        return "Data{" +
                "application=" + application +
                ", dataType='" + dataType + '\'' +
                ", rowId='" + rowId + '\'' +
                ", authorization=" + authorization +
                ", refsLinkedTo=" + refsLinkedTo +
                ", dataValues=" + dataValues +
                ", binaryFile=" + binaryFile +
                ", uniqueness=" + uniqueness +
                '}';
    }
}