package fr.inra.oresing.model.jsontype;

import lombok.Value;

@Value
public class IntegerValueThatCanBeStoreInDataBase implements ValueThatCanBeStoreInDataBase<Integer> {

    Integer value;

    @Override
    public Integer toTypedValue(String v) {
        return Integer.parseInt(v);
    }
}