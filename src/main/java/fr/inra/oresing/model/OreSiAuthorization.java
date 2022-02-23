package fr.inra.oresing.model;

import fr.inra.oresing.persistence.OperationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class OreSiAuthorization extends OreSiEntity {
    private String name;
    private Set<UUID> oreSiUsers;
    private UUID application;
    private String dataType;
    private Map<OperationType,List<Authorization>> authorizations;
}