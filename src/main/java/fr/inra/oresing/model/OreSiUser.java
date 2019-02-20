package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OreSiUser extends OreSiEntity {
    private String login;
    private String password;
}
