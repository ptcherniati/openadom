package fr.inra.oresing.rest.authentication;

import org.springframework.security.access.ConfigAttribute;

public record OreSiConfigAttribute(String attribute)implements ConfigAttribute {
    @Override
    public String getAttribute() {
        return attribute();
    }
}

