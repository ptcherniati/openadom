package fr.inra.oresing.model;

import fr.inra.oresing.persistence.OperationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString(callSuper = true)
public class OreSiAuthorization extends OreSiEntity {
    private String name;
    private Set<UUID> oreSiUsers;
    private UUID application;
    private Map<String, Map<OperationType,List<Authorization>>> authorizations = new HashMap<>();
}