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
    public final Application filterFields(List<ApplicationInformation> filters){
        final Application returnApp = new Application();
        returnApp.setComment(this.getComment());
        returnApp.setVersion(this.getVersion());
        returnApp.setName(this.getName());
        returnApp.setConfigFile(this.getConfigFile());
        returnApp.setCreationDate(this.getCreationDate());
        returnApp.setUpdateDate(this.getUpdateDate());
        if (filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.CONFIGURATION)) {
            returnApp.setConfiguration(this.getConfiguration());
        }
        if (filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.DATATYPE)) {
            returnApp.setDataType(this.getDataType());
        }
        if (filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.REFERENCETYPE)) {
            returnApp.setReferenceType(this.getReferenceType());
        }
        return returnApp;
    }
    private String name;
    private String comment;
    private Integer version;
    private List<String> referenceType;
    private List<String> dataType;
    private Configuration configuration;
    private UUID configFile; // lien vers un BinaryFile
}