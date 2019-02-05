package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class Data extends OreSiEntity {
    private UUID binaryFile;
    private List<UUID> refs;
    private String jsonData;
    private String jsonAccuracy;
}
