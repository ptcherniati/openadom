package fr.inra.oresing.model.jsontype;

import lombok.Value;

@Value
public class StringValueThatCanBeStoreInDataBase implements ValueThatCanBeStoreInDataBase<String> {

        String value;

        @Override
        public String toTypedValue(String v) {
                return v;
        }
}