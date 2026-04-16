package fr.inra.oresing.rest;

import fr.inra.oresing.persistence.JsonRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@Slf4j
public class JsonRequestParamArgumentResolver implements HandlerMethodArgumentResolver {

    private final JsonRowMapper jsonMapper;

    public JsonRequestParamArgumentResolver(JsonRowMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }


    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasAnnotation = parameter.hasParameterAnnotation(JsonParam.class);
        return hasAnnotation;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws Exception {
        JsonParam annotation = parameter.getParameterAnnotation(JsonParam.class);
        String paramName = annotation.value().isEmpty()
                ? parameter.getParameterName()
                : annotation.value();

        String jsonString = webRequest.getParameter(paramName);

        if (jsonString == null || "undefined".equals(jsonString)) {
            if (annotation.required()) {
                throw new IllegalArgumentException("Required parameter '" + paramName + "' is missing");
            }
            return null;
        }

        Class<?> targetType = parameter.getParameterType();
        Object result = jsonMapper.readValue(jsonString, targetType);

        log.info("Deserialized to: {}", result);

        return result;
    }
}