package fr.inra.oresing.model.jsontype;

import lombok.Value;

import java.util.Set;

@Value
public class SetStringValueThatCanBeStoreInDataBase implements SetValueThatCanBeStoreInDataBase<String> {

        Set<String> value;

        @Override
        public String toTypedValue(String v) {
                return v;
        }
}