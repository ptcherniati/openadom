package fr.inra.oresing.model.jsontype;

import lombok.Value;

import java.util.Set;

@Value
public class SetIntegerValueThatCanBeStoreInDataBase implements SetValueThatCanBeStoreInDataBase<Integer> {

    Set<Integer> value;

    @Override
    public Integer toTypedValue(String v) {
        return Integer.parseInt(v);
    }
}