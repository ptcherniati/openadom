package fr.inra.oresing.model.jsontype;

import lombok.Value;

@Value
public class FloatValueThatCanBeStoreInDataBase implements ValueThatCanBeStoreInDataBase<Float> {

    Float value;

    @Override
    public Float toTypedValue(String v) {
        return Float.parseFloat(v);
    }
}