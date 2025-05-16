package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Locale;
import java.util.Map;

@Setter
public class Internationalizations {
    public static final String TAGS = "tags";
    public static final String APPLICATION = "application";
    public static final String DATA = "data";
    public static final String FIELDS = "fields";
    public static final String RIGHT_REQUEST = "rightsrequest";
    public static final String ADDITIONAL_FILES = "additionalFiles";
    Map<String, Map<Locale, String>> tags = Map.of();
    @Getter
    InternationalizationTitle application;
    @Getter
    Map<String, InternationalizationData> data = Map.of();
    @Getter
    InternationalizationRightrequest rightsrequest = new InternationalizationRightrequest();
    @Getter
    Map<String, InternationalizationAdditionalFile> additionalFiles = Map.of();

    public Map getTags() {
        return tags;
    }

}