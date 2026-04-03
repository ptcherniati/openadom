package fr.inra.oresing.domain.application.configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class StandardDataDescriptionBuilder {

    private char separator = ';';
    private Integer headerLine = 1;
    private Integer firstRowLine = 2;
    private Boolean allowUnexpectedColumns = false;
    private Set<Tag> tags = Set.of();
    private LinkedHashSet<String> naturalKey = new LinkedHashSet<>();
    private Map<String, ComponentDescription> componentDescriptions = Map.of();
    private Submission submission;
    private Authorization authorization;
    private Map<String, ValidationDescription> validations = Map.of();
    private List<Depends> depends = List.of();
    private TreeMap<Integer, List<MigrationDescription>> migrations = new TreeMap<>();

    public StandardDataDescriptionBuilder separator(char separator) {
        this.separator = separator;
        return this;
    }

    public StandardDataDescriptionBuilder headerLine(Integer headerLine) {
        this.headerLine = headerLine;
        return this;
    }

    public StandardDataDescriptionBuilder firstRowLine(Integer firstRowLine) {
        this.firstRowLine = firstRowLine;
        return this;
    }

    public StandardDataDescriptionBuilder allowUnexpectedColumns(Boolean allowUnexpectedColumns) {
        this.allowUnexpectedColumns = allowUnexpectedColumns;
        return this;
    }

    public StandardDataDescriptionBuilder tags(Set<Tag> tags) {
        this.tags = tags;
        return this;
    }

    public StandardDataDescriptionBuilder naturalKey(LinkedHashSet<String> naturalKey) {
        this.naturalKey = naturalKey;
        return this;
    }

    public StandardDataDescriptionBuilder componentDescriptions(Map<String, ComponentDescription> componentDescriptions) {
        this.componentDescriptions = componentDescriptions;
        return this;
    }

    public StandardDataDescriptionBuilder submission(Submission submission) {
        this.submission = submission;
        return this;
    }

    public StandardDataDescriptionBuilder authorization(Authorization authorization) {
        this.authorization = authorization;
        return this;
    }

    public StandardDataDescriptionBuilder validations(Map<String, ValidationDescription> validations) {
        this.validations = validations;
        return this;
    }

    public StandardDataDescriptionBuilder depends(List<Depends> depends) {
        this.depends = depends;
        return this;
    }

    public StandardDataDescriptionBuilder migrations(TreeMap<Integer, List<MigrationDescription>> migrations) {
        this.migrations = migrations;
        return this;
    }

    public StandardDataDescription build() {
        return new StandardDataDescription(
                separator,
                headerLine,
                firstRowLine,
                allowUnexpectedColumns,
                tags,
                naturalKey,
                componentDescriptions,
                submission,
                authorization,
                validations,
                depends,
                migrations
        );
    }
}