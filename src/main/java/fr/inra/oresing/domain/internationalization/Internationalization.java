package fr.inra.oresing.domain.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Locale;

@Getter
@Setter
public class Internationalization extends LinkedHashMap<Locale, String> {
}