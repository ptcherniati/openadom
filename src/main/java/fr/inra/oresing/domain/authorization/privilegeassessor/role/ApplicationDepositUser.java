package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public record ApplicationDepositUser() implements ApplicationManager {
    @Override
    public boolean canUpdateApplication() {
        return false;
    }
}
