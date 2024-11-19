package fr.inra.oresing.domain.repository.authorization.role;

public enum OreSiApplicationCreatorRole implements OreSiRoleToBeGranted {

    APPLICATION_CREATOR;

    @Override
    public String getAsSqlRole() {
        return "applicationCreator";
    }
}
