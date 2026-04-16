package fr.inra.oresing.domain.application.configuration;

import java.util.Locale;

public class ApplicationDescriptionBuilder {

    private String name = "my_app";
    private Version version = new Version("1.0.0");
    private Locale defaultLanguage = Locale.FRENCH;
    private String comment = "A comment";

    public ApplicationDescriptionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ApplicationDescriptionBuilder version(Version version) {
        this.version = version;
        return this;
    }

    public ApplicationDescriptionBuilder defaultLanguage(Locale defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
        return this;
    }

    public ApplicationDescriptionBuilder comment(String comment) {
        this.comment = comment;
        return this;
    }

    public ApplicationDescription build() {
        return new ApplicationDescription(name, version, defaultLanguage, comment);
    }
}