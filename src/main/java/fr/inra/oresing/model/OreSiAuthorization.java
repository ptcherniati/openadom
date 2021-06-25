package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class OreSiAuthorization extends OreSiEntity {
    private UUID oreSiUser;
    private UUID application;
    private String dataType;
    private String dataGroup;
    private Map<String, String> authorizedScopes;
    private LocalDateTimeRange timeScope;
}
