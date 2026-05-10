package fr.inra.oresing.domain.additionalfiles;

import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.additionalfiles.OperationAdditionalFileType;
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
public class OreSiAdditionalFileAuthorization extends OreSiEntity {
    private String name;
    private Set<UUID> oreSiUsers;
    private UUID application;
    private Map<OperationAdditionalFileType, List<String>> additionalFiles;
}