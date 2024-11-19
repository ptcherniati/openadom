package fr.inra.oresing.domain.application.configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum SubmissionType {
    OA_VERSIONING,
    OA_INSERTION;
    public static final Set<String> VALUES = Arrays.stream(values()).map(SubmissionType::name).collect(Collectors.toSet());

}
