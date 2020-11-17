package fr.inra.oresing;

import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString
public class OreSiUserRequestClient implements OreSiRequestClient {

    private UUID id;

    private OreSiUserRole role;

}
