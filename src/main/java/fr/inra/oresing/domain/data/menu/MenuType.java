package fr.inra.oresing.domain.data.menu;

import lombok.Getter;

@Getter
public enum MenuType {
    authorization("authorization"),
    submission("submission");

    private final String type;

    MenuType(String type) {
        this.type= type;
    }
}