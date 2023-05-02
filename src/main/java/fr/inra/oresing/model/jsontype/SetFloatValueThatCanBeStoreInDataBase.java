package fr.inra.oresing.model.jsontype;

import lombok.Value;

import java.util.Set;

@Value
public class SetFloatValueThatCanBeStoreInDataBase implements SetValueThatCanBeStoreInDataBase<Float> {

    Set<Float> value;

    @Override
    public Float toTypedValue(String v) {
        return Float.parseFloat(v);
    }
}