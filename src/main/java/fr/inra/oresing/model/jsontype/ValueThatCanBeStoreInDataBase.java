package fr.inra.oresing.model.jsontype;

public interface ValueThatCanBeStoreInDataBase<T> {
    T toTypedValue(String v);
    T getValue();
}