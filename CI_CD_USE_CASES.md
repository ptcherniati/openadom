# Configuration CI/CD pour les Tests Use Cases

## 🎯 Modifications apportées

### 1. Ajout du profil Maven `use-cases`

**Fichier**: `pom.xml`

```xml
<profile>
    <id>use-cases</id>
    <properties>
        <junit.jupiter.tags>use-cases</junit.jupiter.tags>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${maven-surefire-plugin.version}</version>
                <configuration>
                    <groups>${junit.jupiter.tags}</groups>
                    <argLine>-XX:+EnableDynamicAgentLoading</argLine>
                    <excludedGroups>
                        docker-required
                    </excludedGroups>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

### 2. Exclusion du tag dans le profil `no-tags`

Le tag `use-cases` a été ajouté à la liste des exclusions du profil `no-tags` :

```xml
<excludedGroups>
    SUITE,core.basic,core.config,core.auth,
    domain.model,domain.checker,domain.i18n,
    integration.rest,integration.persistence,
    app.monsoere,app.haute_frequence,app.acbb,
    app.olac,app.foret,app.pattern,app.recursivity,app.teledetection,
    use-cases,
    docker-required
</excludedGroups>
```

### 3. Ajout du job GitLab CI

**Fichier**: `.gitlab_test_mvn.yml`

```yaml
test_use-cases:
  <<: *test_template
```

Ce job exécutera automatiquement tous les tests taggés `@Tag("use-cases")` dans le pipeline GitLab CI.

## 📊 Résultats des tests

- **Nombre total de tests use-cases**: 55
- **Succès**: 55/55 ✅
- **Échecs**: 0
- **Ignorés**: 0

## 🔧 Utilisation

### En local

```bash
# Exécuter tous les tests use-cases
mvn test -Puse-cases

# Exécuter tous les tests use-cases avec une classe spécifique
mvn test -Puse-cases -Dtest=GetApplicationsUseCaseTest

# Compiler et tester
mvn clean test -Puse-cases
```

### Dans GitLab CI

Le job `test_use-cases` sera automatiquement exécuté lors de chaque push/MR selon la configuration du pipeline.

La conversion automatique du nom du job en profil Maven :
- Job name: `test_use-cases`
- Profil Maven utilisé: `use-cases`

## 📦 Tests couverts

Les 55 tests use-cases couvrent les domaines suivants :

### Application (5 tests)
- GetApplicationsUseCaseTest
- ValidateConfigurationUseCaseTest
- CreateApplicationUseCaseTest
- ChangeApplicationConfigurationUseCaseTest
- GetApplicationUseCaseTest
- GetApplicationOrAccordingToRightsUseCaseTest
- BuildOpenAdomUseCaseTest

### Data (10 tests)
- FindDataUseCaseTest
- FilterListUseCaseTest
- DeleteDataUseCaseTest
- GetCheckedFormatComponentsUseCaseTest
- BuildDataZipUseCaseTest
- SendZipLinkByMailUseCaseTest
- WriteUploadBundleUseCaseTest
- ReadEntryUseCaseTest
- GetDataCsvStreamUseCaseTest (2 tests)
- GetFormatCheckedUseCaseTest
- GetDataColumnUseCaseTest
- GetReferenceDisplaysByIdUseCaseTest
- FindReferenceUseCaseTest

### Authentication (1 test)
- GetCurrentUserUseCaseTest

### Email (1 test)
- SendUploadErrorsMailUseCaseTest

### Authorization (4 tests)
- GetAdminAuthorizationsUseCaseTest
- GetAuthorizationScopesUseCaseTest
- GetAllUsersUseCaseTest (2 tests)
- GetApplicationAuthorizationsUseCaseTest

### Binary Files (7 tests)
- RemoveFileUseCaseTest
- GetReferencedBinaryFilesUseCaseTest
- GetFilesOnRepositoryUseCaseTest (2 tests)
- GetFileUseCaseTest (2 tests)
- GetFileWithDataUseCaseTest

### Additional Files (6 tests)
- GetCharteUseCaseTest
- GetAdditionalFilesZipStreamUseCaseTest (2 tests)
- DeleteAdditionalFilesUseCaseTest
- CreateOrUpdateAdditionalFileUseCaseTest
- FindAdditionalFileUseCaseTest (2 tests)

### Synthesis (4 tests)
- BuildSynthesisUseCaseTest
- GetSynthesisUseCaseTest
- GetSynthesisWithVariableUseCaseTest (2 tests)

### Normalization (4 tests)
- BuildNormalizedSchemaUseCaseTest (2 tests)
- GetNormalizedSchemaUseCaseTest (2 tests)

### Versioning (3 tests)
- GetStoreFileUseCaseTest
- UnPublishVersionBeforeDeleteUseCaseTest
- CreateDataUseCaseTest

### Rights Request (2 tests)
- CreateOrUpdateRightsRequestUseCaseTest
- FindRightsRequestUseCaseTest

## ✅ Validation

Configuration validée avec succès :
- ✅ Profil Maven créé
- ✅ Job GitLab CI ajouté
- ✅ Tests exécutés avec succès (55/55)
- ✅ Exclusions configurées dans `no-tags`
- ✅ Build réussi

Date: 4 février 2026
