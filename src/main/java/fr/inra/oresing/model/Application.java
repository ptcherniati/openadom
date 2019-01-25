package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class Application extends OreSiEntity {
    private String name;
    private UUID config; // lien vers un BinaryFile
}
