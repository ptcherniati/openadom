package fr.inra.oresing.domain.data.menu;

public enum MenuType {
    authorization("authorization"),
    submission("submission");

    public String getType() {
        return type;
    }

    private final String type;

    MenuType(String type) {
        this.type= type;
    }
}
