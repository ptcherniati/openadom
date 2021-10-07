package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface LineChecker {

    ValidationCheckResult check(Map<VariableComponentKey, String> values);
    ValidationCheckResult checkReference(Map<String, String> values);
    default boolean instanceOf(Class clazz){
        if(this instanceof ILineCheckerDecorator){
            return clazz.isInstance (this) || ((ILineCheckerDecorator)this).getChecker().instanceOf(clazz);
        }
        return clazz.isInstance(this);
    }
    default <T extends LineChecker> T getInstance(Class<T> clazz){
        if(this instanceof ILineCheckerDecorator){
            return ((ILineCheckerDecorator)this).getChecker().getInstance(clazz);
        }else if(clazz.isInstance(this)) {
            return (T) this;
        }
        return null;
    }
}