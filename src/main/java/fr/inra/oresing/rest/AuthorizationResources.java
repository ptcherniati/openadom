package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationResources {

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private OreSiApiRequestContext request;

    @Autowired
    private OreSiRepository repository;

    @PostMapping(value = "/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public OreSiAuthorization addAuthorization(@RequestBody OreSiAuthorization authorization) {
        OreSiUserRole userRole = authRepository.getUserRole(authorization.getUserId());
        Set<String> usingExpressionElements = new LinkedHashSet<>();

        Application application = repository.findApplication(authorization.getApplicationNameOrId());

        authorization.getTimeScope().ifPresent(timeScope -> {
            String timeScopeSqlExpression = timeScope.toSqlExpression();
            usingExpressionElements.add("timeScope @> '" + timeScopeSqlExpression + "'");
        });

        if (authorization.isRestrictedOnReference()) {
            String referenceIdsArray = authorization.getReferenceIds().stream()
                    .map(uuid -> "'" + uuid + "'::uuid")
                    .collect(Collectors.joining(",", "ARRAY[", "]"));

            // String usingExpression = "refsLinkedTo <@ " + referenceIdsArray;
        }

        String usingExpression = usingExpressionElements.stream()
                .map(statement -> "(" + statement + ")")
                .collect(Collectors.joining(" AND "));

        SqlPolicy sqlPolicy = new SqlPolicy(
                SqlSchema.forApplication(application).data(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.SELECT,
                userRole,
                usingExpression
        );

        authRepository.addUserInRole(userRole, OreSiRightOnApplicationRole.readerOn(application));
        authRepository.createPolicy(sqlPolicy);

        return authorization;
    }
}