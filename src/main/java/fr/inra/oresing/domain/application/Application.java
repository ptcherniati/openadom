package fr.inra.oresing.domain.application;

import com.google.common.collect.Maps;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationComponent;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString(callSuper = true)
public class Application extends OreSiEntity {
    public Timestamp getLastChartes() {
        return lastChartes==null?Timestamp.from(Instant.MIN):lastChartes;
    }

    private Timestamp lastChartes;
    private String name;
    private String version;
    private List<String> data;
    private List<String> additionalFiles;
    private Configuration configuration;
    private UUID configFile; // lien vers un BinaryFile


    public Application applicationAccordingToRights() {
        Configuration configuration = this.configuration;
        final Configuration configurationforNotAuthorized = configuration.configurationAccordingToRights();
        setConfiguration((configurationforNotAuthorized));
        return this;
    }

    public final Application filterFieldsAndHidden(final List<ApplicationInformation> filters) {
        Application returnApp = new Application();
        returnApp.setId(getId());
        returnApp.setVersion(version);
        returnApp.setName(name);
        returnApp.setConfigFile(configFile);
        returnApp.setCreationDate(getCreationDate());
        returnApp.setUpdateDate(getUpdateDate());
        if (filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.CONFIGURATION)) {
            returnApp.setConfiguration(configuration);
        }
        if (filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.DATA)) {
            List<String> references = getData()
                    .stream()
                    .filter(dataName -> !configuration.dataDescription().get(dataName).tags().contains(new Tag.HiddenTag()))
                    .collect(Collectors.toList());
            returnApp.setData(references);
        }
        if (filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.ADDITIONALFILE)) {
            returnApp.setAdditionalFiles(additionalFiles);
        }
        return returnApp;
    }

    public Optional<StandardDataDescription> findData(String dataName) {
        Function<Map<String, StandardDataDescription>, StandardDataDescription> getDataDescription = data -> data.get(dataName);
        return Optional.of(findData())
                .map(getDataDescription);
    }

    public Optional<ComponentDescription> findComponentOfData(String dataName, String componentName) {
        Function<Map<String, ComponentDescription>, ComponentDescription> getComponentDescription = components -> components.get(componentName);
        return findData(dataName)
                .map(StandardDataDescription::componentDescriptions)
                .map(getComponentDescription);
    }

    public Map<String, StandardDataDescription> findData() {
        return Optional.of(getConfiguration())
                .map(Configuration::dataDescription)
                .orElseGet(Map::of);
    }

    public Optional<RightRequestDescription> findRightRequest() {
        return Optional.of(getConfiguration())
                .map(Configuration::rightsRequest);
    }

    public Optional<Internationalizations> findInternationalizations() {
        return Optional.of(getConfiguration())
                .map(Configuration::i18n);
    }

    public Optional<ApplicationDescription> findApplicationDescription() {
        return Optional.of(getConfiguration())
                .map(Configuration::applicationDescription);
    }

    public boolean existsData(String dataName) {
        return findData(dataName).isPresent();
    }

    public Optional<Submission> findSubmission(String dataName) {
        return findData(dataName)
                .map(StandardDataDescription::submission);
    }

    public String internationalizeHeader(String dataName, String componentName, String language) {
        return Optional.ofNullable(getConfiguration().i18n())
                .map(Internationalizations::getData)
                .map(data -> data.get(dataName))
                .map(InternationalizationData::getComponents)
                .map(component -> component.get(componentName))
                .map(InternationalizationComponent::getExportHeader)
                .map(exportHeader -> exportHeader.getTitle().get(Locale.of(language)))
                .orElse(findComponentOfData(dataName, componentName)
                        .map(ComponentDescription::importHeader)
                        .orElse(componentName));
    }

    private Function<Node, Optional<Node>> findParentNodeForDataName(String dataName) {
        return node -> findParentNode(dataName, node);
    }

    public Optional<Node> findNode(String dataName) {
        Function<Node, Optional<Node>> findDataNameNodeInNode = node -> findNode(dataName, node);
        return getConfiguration().hierarchicalNodes().stream()
                .map(findDataNameNodeInNode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public Optional<Node> findParentNode(String dataName) {
        return getConfiguration().hierarchicalNodes().stream()
                .map(findParentNodeForDataName((dataName)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Optional<Node> findParentNode(String dataName, Node node) {
        boolean isParent = node.children()
                .stream()
                .anyMatch(child -> child.nodeName().equals(dataName));
        if (isParent) {
            return Optional.of(node);
        }
        return node.children().stream()
                .map(child -> findParentNode(dataName, child))
                .filter(Optional::isPresent)
                .findFirst()
                .orElseGet(Optional::empty);
    }

    private Optional<Node> findNode(String dataName, Node node) {
        if (node.nodeName().equals(dataName)) {
            return Optional.of(node);
        }
        return node.children().stream()
                .filter(n -> n.nodeName().equals(dataName))
                .findFirst();
    }

    public boolean isData(String dataName) {
        return findData(dataName)
                .map(StandardDataDescription::tags)
                .map(tags -> tags.stream().anyMatch(Tag.DataTag.instance()::equals))
                .orElse(false);
    }

    public List<String> getAllDataNames() {
        Map<Boolean, List<String>> nameByType = getConfiguration().orderedNodes()
                .stream()
                .sorted()
                .map(Node::nodeName)
                .collect(Collectors.partitioningBy(this::isData));
        nameByType.get(false).addAll(nameByType.get(true));
        return nameByType.get(false);
    }

    public Set<String> findDependentNodes(Set<String> dataName) {
        return dataName.stream()
                .map(this::findNode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Node::depends)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public Map<String, Set<String>> findDependantNodesByDataName() {
        return findData().keySet()
                .stream()
                .collect(Collectors.toMap(
                        dataName -> dataName,
                        dataname -> findDependentNodes(Set.of(dataname))
                ));
    }

    public long patternDefinitionCount(String dataName) {
        return findData(dataName)
                .map(StandardDataDescription::patternDefinitionCount)
                .orElse(0L);
    }

    public String getLocalizedLocalName(Locale locale) {
        String localizedApplicationName = Optional.ofNullable(getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getApplication)
                .map(InternationalizationTitle::getTitle)
                .map(title->title.get(locale) )
                .orElse(getConfiguration().applicationDescription().name());
        return localizedApplicationName == null ? getName() : localizedApplicationName;
    }

    public String getLocalizedDataName(Locale locale, String dataName) {
        return Optional.ofNullable(getConfiguration().i18n())
                .map(Internationalizations::getData)
                .map(data->data.get(dataName))
                .map(InternationalizationData::getI18n)
                .map(InternationalizationTitle::getTitle)
                .map(title->title.get(locale))
                .orElse(null);
    }
}