package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ReferenceColumn;
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

    public static CheckerTarget getInstance(VariableComponentKey target, Application application, OreSiRepository.RepositoryForApplication repository){
        CheckerTarget checkerTarget = new VariableComponentKeyCheckerTarget(target);
        checkerTarget.application = application;
        checkerTarget.repository = repository;
        return checkerTarget;
    }

    public static CheckerTarget getInstance(ReferenceColumn target, Application application, OreSiRepository.RepositoryForApplication repository){
        CheckerTarget checkerTarget = new ColumnCheckerTarget(target);
        checkerTarget.application = application;
        checkerTarget.repository = repository;
        return checkerTarget;
    }

    public abstract String getInternationalizedKey(String key);

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

    public static class ColumnCheckerTarget extends CheckerTarget<ReferenceColumn> {

        private ColumnCheckerTarget(ReferenceColumn column) {
            super(CheckerTargetType.PARAM_COLUMN, column);
        }

        @Override
        public String getInternationalizedKey(String key){
            return key+"WithColumn";
        }
    }
    public static class VariableComponentKeyCheckerTarget extends CheckerTarget<VariableComponentKey> {

        private VariableComponentKeyCheckerTarget(VariableComponentKey variableComponentKey) {
            super(CheckerTargetType.PARAM_VARIABLE_COMPONENT_KEY, variableComponentKey);
        }

        @Override
        public String getInternationalizedKey(String key){
            return key;
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
