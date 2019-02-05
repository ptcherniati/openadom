package fr.inra.oresing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OreSiUtils {

    private static Pattern patternField = Pattern.compile("^(?:get|set|is)?(.)(.*)");
    private static ObjectMapper json = new ObjectMapper();


    public static String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException eee) {
            throw new IllegalArgumentException("Can't convert argument to json: " + o, eee);
        }
    }


    public static Map<String, Object> mapOf(SerializableSupplier... getter) {
        return Stream.of(getter)
                .collect(HashMap::new, (m, v)->m.put(OreSiUtils.fieldOf(v), v.get()), HashMap::putAll);
    }

    public static <T> String[] fieldsOf(SerializableSupplier<T> ...getters) {
        return Stream.of(getters)
                .map(getter -> extractFieldName(getter.method().getName()))
                .toArray(String[]::new);
    }

    public static <T> String fieldOf(SerializableSupplier<T> getter) {
        return extractFieldName(getter.method().getName());
    }

    public static <T> String fieldOf(SerializableConsumer<T> setter) {
        return extractFieldName(setter.method().getName());
    }

    private static String extractFieldName(String methodName) {
        String result = methodName;
        Matcher matcher = patternField.matcher(methodName);
        if (matcher.find()) {
            result = matcher.group(1).toLowerCase() + matcher.group(2);
        }
        return result;
    }

    public interface SerializableConsumer<T> extends Consumer<T>, Serializable, MethodReferenceReflection {}
    public interface SerializableSupplier<T> extends Supplier<T>, Serializable, MethodReferenceReflection {}

    interface MethodReferenceReflection {

        //inspired by: http://benjiweber.co.uk/blog/2015/08/17/lambda-parameter-names-with-reflection/

        default SerializedLambda serialized() {
            try {
                Method replaceMethod = getClass().getDeclaredMethod("writeReplace");
                replaceMethod.setAccessible(true);
                return (SerializedLambda) replaceMethod.invoke(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        default Class getContainingClass() {
            try {
                String className = serialized().getImplClass().replaceAll("/", ".");
                return Class.forName(className);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        default Method method() {
            SerializedLambda lambda = serialized();
            Class containingClass = getContainingClass();
            return Arrays.stream(containingClass.getDeclaredMethods())
                    .filter(method -> Objects.equals(method.getName(), lambda.getImplMethodName()))
                    .findFirst()
                    .orElseThrow(UnableToGuessMethodException::new);
        }

        class UnableToGuessMethodException extends RuntimeException {}
    }

}
