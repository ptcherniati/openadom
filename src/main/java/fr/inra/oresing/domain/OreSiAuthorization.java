package fr.inra.oresing.domain;

import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.persistence.SqlPolicy;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString(callSuper = true)
public class OreSiAuthorization extends OreSiEntity {
    private String name;
    private String description;
    private Set<UUID> oreSiUsers;
    private UUID application;
    private Map<String, AuthorizationForScope> authorizations = new HashMap<>();

    public String toIdForReference(SqlPolicy.Statement statement, String datatype) {
        return  OreSiAuthorization.class.getSimpleName() +
                "_" +
                getId().toString().substring(0, 7) +
                "_data_" +
                datatype +
                "_" +
                statement.name().substring(0, 3);
    }
}