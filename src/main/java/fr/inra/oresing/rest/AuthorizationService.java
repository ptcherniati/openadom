package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional
public class AuthorizationService {

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private OreSiRepository repository;

    public OreSiAuthorization addAuthorization(OreSiAuthorization authorization) {
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

        String dataSet = authorization.getDataset();
        String dataGroup = authorization.getDataGroup();

        Preconditions.checkArgument(application.getConfiguration().getDataset().containsKey(dataSet));
        Preconditions.checkArgument(application.getConfiguration().getDataset().get(dataSet).getDataGroups().containsKey(dataGroup));

        usingExpressionElements.add("application = '" + application.getId() + "'::uuid AND dataType = '" + dataSet + "' AND dataGroup = '" + dataGroup + "'");

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
