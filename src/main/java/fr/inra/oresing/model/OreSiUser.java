package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
public class OreSiUser extends OreSiEntity {
    private String login;
    private String password;
    private List<String> authorizations= new LinkedList<>();
}