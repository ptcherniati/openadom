package fr.inra.oresing.model.jsontype;

import java.util.Set;

public interface SetValueThatCanBeStoreInDataBase<T> {
    T toTypedValue(String v);
    Set<T> getValue();
}