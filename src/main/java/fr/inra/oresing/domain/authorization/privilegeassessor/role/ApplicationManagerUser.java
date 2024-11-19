package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;

import java.util.List;
import java.util.regex.Pattern;

public record ApplicationManagerUser() implements ApplicationManager {

    @Override
    public boolean canUpdateApplication() {
        return false;
    }
}
