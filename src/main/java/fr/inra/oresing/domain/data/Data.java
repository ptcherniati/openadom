package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.checker.type.FieldType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Data extends OreSiEntity {
    private UUID application;
    private String dataType;
    private String rowId;
    private Authorization authorization;
    private Map<String, Map<String, Set<UUID>>> refsLinkedTo;
    private Map<String, Map<String, FieldType>> dataValues;
    private UUID binaryFile;
    private List<String> uniqueness;

    public UUID getApplication() {
        return application;
    }

    public void setApplication(UUID application) {
        this.application = application;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getRowId() {
        return rowId;
    }

    public void setRowId(String rowId) {
        this.rowId = rowId;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }

    public Map<String, Map<String, Set<UUID>>> getRefsLinkedTo() {
        return refsLinkedTo;
    }

    public void setRefsLinkedTo(Map<String, Map<String, Set<UUID>>> refsLinkedTo) {
        this.refsLinkedTo = refsLinkedTo;
    }

    public Map<String, Map<String, FieldType>> getDataValues() {
        return dataValues;
    }

    public void setDataValues(Map<String, Map<String, FieldType>> dataValues) {
        this.dataValues = dataValues;
    }

    public UUID getBinaryFile() {
        return binaryFile;
    }

    public void setBinaryFile(UUID binaryFile) {
        this.binaryFile = binaryFile;
    }

    public List<String> getUniqueness() {
        return uniqueness;
    }

    public void setUniqueness(List<String> uniqueness) {
        this.uniqueness = uniqueness;
    }

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