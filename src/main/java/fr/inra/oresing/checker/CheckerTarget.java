package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
abstract public class CheckerTarget<T>{
    private T target;
    private CheckerTargetType type;
    public static CheckerTarget getInstance(Object target){
        if(target instanceof VariableComponentKey){
            return new VariableComponentKeyCheckerTarget((VariableComponentKey) target);
        } else if (target instanceof String){
            return new ColumnCheckerTarget((String) target);
        }
        return null;
    }
    public String getInternationalizedKey(String key){
        if(CheckerTargetType.PARAM_COLUMN.equals(type)){
            return key+"WithColumn";
        }
        return key;
    }

    public CheckerTarget(CheckerTargetType type, T target) {
        this.type = type;
        this.target = target;
    }

    public static enum CheckerTargetType{
        PARAM_VARIABLE_COMPONENT_KEY("variableComponentKey"),PARAM_COLUMN("column");

        private final String type;

        CheckerTargetType(String type) {
            this.type = type;
        }

        String getType(){
            return this.type;
        }

        @Override
        public String toString() {
            return type;
        }
    }
    public static class ColumnCheckerTarget extends CheckerTarget<String>{

        private ColumnCheckerTarget(String column) {
            super(CheckerTargetType.PARAM_COLUMN, column);
        }
    }
    public static class VariableComponentKeyCheckerTarget extends CheckerTarget<VariableComponentKey>{

        private VariableComponentKeyCheckerTarget(VariableComponentKey variableComponentKey) {
            super(CheckerTargetType.PARAM_VARIABLE_COMPONENT_KEY, variableComponentKey);
        }
    }

}