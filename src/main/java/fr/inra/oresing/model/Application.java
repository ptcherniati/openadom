package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class Application extends OreSiEntity {
    private String name;
    private String comment;
    private List<String> referenceType;
    private List<String> dataType;
    private List<String> additionalFile;
    private Configuration configuration;
    private UUID configFile; // lien vers un BinaryFile
}