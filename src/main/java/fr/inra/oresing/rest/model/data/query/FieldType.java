package fr.inra.oresing.rest.model.data.query;

import java.util.Set;

// TODO- rest ou persistance enum ?!! (=^_^=)
public enum FieldType {
    date, time, datetime, numeric, bool, reference;

    boolean isNumeric() {
        return this == numeric;
    }

    boolean isDate() {
        return Set.of(date, time, datetime).contains(this);
    }
}
