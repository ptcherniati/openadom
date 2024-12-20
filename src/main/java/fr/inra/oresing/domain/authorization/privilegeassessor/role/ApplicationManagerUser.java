package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public record ApplicationManagerUser() implements ApplicationManager {

    @Override
    public boolean canUpdateApplication() {
        return false;
    }
}
