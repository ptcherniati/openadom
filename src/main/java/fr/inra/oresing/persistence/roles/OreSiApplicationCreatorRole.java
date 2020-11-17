package fr.inra.oresing.persistence.roles;

public enum OreSiApplicationCreatorRole implements OreSiRoleToBeGranted {

    APPLICATION_CREATOR;

    @Override
    public String getAsSqlRole() {
        return "applicationCreator";
    }
}
