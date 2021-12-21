package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
abstract public class CheckerTarget<T>{
    private T target;
    @JsonIgnore
    private Application application;
    @JsonIgnore
    private OreSiRepository.RepositoryForApplication repository;
    private CheckerTargetType type;
    public static CheckerTarget getInstance(Object target, Application application, OreSiRepository.RepositoryForApplication repository){
        CheckerTarget checkerTarget = null;
        if(target instanceof VariableComponentKey){
            checkerTarget =  new VariableComponentKeyCheckerTarget((VariableComponentKey) target);
        } else if (target instanceof String){
            checkerTarget =  new ColumnCheckerTarget((String) target);
        }
        if(checkerTarget!=null){
            checkerTarget.application = application;
            checkerTarget.repository = repository;
        }
        return checkerTarget;
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

    public enum CheckerTargetType{
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckerTarget<?> that = (CheckerTarget<?>) o;
        return Objects.equals(target, that.target) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, type);
    }
}
