package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.BasicComponent;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.persistence.DataSynthesisRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour SynthesisService (sans Spring / sans Docker).
 * Toutes les dépendances sont mockées via Mockito.
 */
@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("domain.model")
@DisplayName("SynthesisService – tests unitaires")
class SynthesisServiceTest {

    @Mock
    private OreSiRepository repository;

    @Mock
    private ServiceContainer serviceContainer;

    @InjectMocks
    private SynthesisService service;

    // ---- helpers ----

    private static Application buildApplication(UUID id) {
        Application app = new Application();
        app.setId(id);
        return app;
    }

    private static OreSiSynthesis synthWith(String variable) {
        OreSiSynthesis s = new OreSiSynthesis();
        s.setVariable(variable);
        return s;
    }

    // =========================================================================
    //  deleteSynthesis(nameOrId, dataType, variable)
    // =========================================================================

    @Nested
    @DisplayName("deleteSynthesis(nameOrId, dataType, variable)")
    class DeleteSynthesisByVariableTest {

        @Test
        @DisplayName("délègue au repository et retourne le nombre de lignes supprimées")
        void delegatesToRepository() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            ApplicationService appService = mock(ApplicationService.class);
            OreSiRepository.RepositoryForApplication repoForApp = mock(OreSiRepository.RepositoryForApplication.class);
            DataSynthesisRepository synthRepo = mock(DataSynthesisRepository.class);

            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplication("myApp")).thenReturn(app);
            when(repository.getRepository(app)).thenReturn(repoForApp);
            when(repoForApp.synthesisRepository()).thenReturn(synthRepo);
            when(synthRepo.removeSynthesisByApplicationDatatypeAndVariable(appId, "temperature", "avg")).thenReturn(3);

            int result = service.deleteSynthesis("myApp", "temperature", "avg");

            assertThat(result).isEqualTo(3);
            verify(synthRepo).removeSynthesisByApplicationDatatypeAndVariable(appId, "temperature", "avg");
        }
    }

    // =========================================================================
    //  deleteSynthesis(nameOrId, dataType)
    // =========================================================================

    @Nested
    @DisplayName("deleteSynthesis(nameOrId, dataType)")
    class DeleteSynthesisByDatatypeTest {

        @Test
        @DisplayName("délègue au repository et retourne le nombre de lignes supprimées")
        void delegatesToRepository() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            ApplicationService appService = mock(ApplicationService.class);
            OreSiRepository.RepositoryForApplication repoForApp = mock(OreSiRepository.RepositoryForApplication.class);
            DataSynthesisRepository synthRepo = mock(DataSynthesisRepository.class);

            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplication("myApp")).thenReturn(app);
            when(repository.getRepository(app)).thenReturn(repoForApp);
            when(repoForApp.synthesisRepository()).thenReturn(synthRepo);
            when(synthRepo.removeSynthesisByApplicationDatatype(appId, "temperature")).thenReturn(5);

            int result = service.deleteSynthesis("myApp", "temperature");

            assertThat(result).isEqualTo(5);
            verify(synthRepo).removeSynthesisByApplicationDatatype(appId, "temperature");
        }
    }

    // =========================================================================
    //  getSynthesis(nameOrId, dataType)
    // =========================================================================

    @Nested
    @DisplayName("getSynthesis(nameOrId, dataType)")
    class GetSynthesisByDatatypeTest {

        @Test
        @DisplayName("retourne les synthèses groupées par variable quand le datatype n'est pas 'hidden'")
        void returnsGroupedSynthesesWhenNotHidden() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            // Configuration avec tag non-hidden : on crée un StandardDataDescription
            // avec un Set de tags ne contenant pas HiddenTag
            StandardDataDescription desc = mock(StandardDataDescription.class);
            when(desc.tags()).thenReturn(Set.of(fr.inra.oresing.domain.application.configuration.Tag.DataTag.instance()));

            Configuration config = mock(Configuration.class);
            Map<String, StandardDataDescription> dataDescMap = Map.of("temperature", desc);
            when(config.dataDescription()).thenReturn(dataDescMap);
            app.setConfiguration(config);

            ApplicationService appService = mock(ApplicationService.class);
            OreSiRepository.RepositoryForApplication repoForApp = mock(OreSiRepository.RepositoryForApplication.class);
            DataSynthesisRepository synthRepo = mock(DataSynthesisRepository.class);

            OreSiSynthesis s1 = synthWith("avg");
            OreSiSynthesis s2 = synthWith("max");

            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplicationOrApplicationAccordingToRights("myApp")).thenReturn(app);
            when(repository.getRepository(app)).thenReturn(repoForApp);
            when(repoForApp.synthesisRepository()).thenReturn(synthRepo);
            when(synthRepo.selectSynthesisDatatype(appId, "temperature")).thenReturn(List.of(s1, s2));

            Map<String, List<OreSiSynthesis>> result = service.getSynthesis("myApp", "temperature");

            assertNotNull(result);
            assertThat(result).containsKey("avg");
            assertThat(result).containsKey("max");
        }

        @Test
        @DisplayName("retourne null quand le datatype a le tag HiddenTag")
        void returnsNullWhenHidden() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            StandardDataDescription desc = mock(StandardDataDescription.class);
            when(desc.tags()).thenReturn(Set.of(fr.inra.oresing.domain.application.configuration.Tag.HiddenTag.instance()));

            Configuration config = mock(Configuration.class);
            Map<String, StandardDataDescription> dataDescMap = Map.of("secretDataType", desc);
            when(config.dataDescription()).thenReturn(dataDescMap);
            app.setConfiguration(config);

            ApplicationService appService = mock(ApplicationService.class);
            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplicationOrApplicationAccordingToRights("myApp")).thenReturn(app);

            Map<String, List<OreSiSynthesis>> result = service.getSynthesis("myApp", "secretDataType");

            assertNull(result);
        }

        @Test
        @DisplayName("retourne null quand le datatype est absent de la configuration")
        void returnsNullWhenDatatypeNotFound() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            Configuration config = mock(Configuration.class);
            when(config.dataDescription()).thenReturn(Map.of()); // aucun datatype
            app.setConfiguration(config);

            ApplicationService appService = mock(ApplicationService.class);
            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplicationOrApplicationAccordingToRights("myApp")).thenReturn(app);

            Map<String, List<OreSiSynthesis>> result = service.getSynthesis("myApp", "unknownType");

            assertNull(result);
        }
    }

    // =========================================================================
    //  getSynthesis(nameOrId, dataName, componentName)
    // =========================================================================

    @Nested
    @DisplayName("getSynthesis(nameOrId, dataName, componentName)")
    class GetSynthesisByComponentTest {

        @Test
        @DisplayName("retourne les synthèses du composant quand le composant n'est pas hidden")
        void returnsGroupedSynthesesWhenComponentNotHidden() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            // BasicComponent (concrete record) avec tag non-hidden
            ComponentDescription compDesc = new fr.inra.oresing.domain.application.configuration.ComponentDescriptionBuilder()
                    .basicComponent()
                    .componentKey("avg")
                    .tags(Set.of(fr.inra.oresing.domain.application.configuration.Tag.DataTag.instance()))
                    .build();

            StandardDataDescription desc = mock(StandardDataDescription.class);
            when(desc.componentDescriptions()).thenReturn(Map.of("avg", compDesc));

            Configuration config = mock(Configuration.class);
            when(config.dataDescription()).thenReturn(Map.of("temperature", desc));
            app.setConfiguration(config);

            ApplicationService appService = mock(ApplicationService.class);
            OreSiRepository.RepositoryForApplication repoForApp = mock(OreSiRepository.RepositoryForApplication.class);
            DataSynthesisRepository synthRepo = mock(DataSynthesisRepository.class);

            OreSiSynthesis s = synthWith("avg");
            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplication("myApp")).thenReturn(app);
            when(repository.getRepository(app)).thenReturn(repoForApp);
            when(repoForApp.synthesisRepository()).thenReturn(synthRepo);
            when(synthRepo.selectSynthesisDatatypeAndVariable(appId, "temperature", "avg")).thenReturn(List.of(s));

            Map<String, List<OreSiSynthesis>> result = service.getSynthesis("myApp", "temperature", "avg");

            assertNotNull(result);
            assertThat(result).containsKey("avg");
        }

        @Test
        @DisplayName("retourne null quand le composant a le tag HiddenTag")
        void returnsNullWhenComponentHidden() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            // BasicComponent avec tag HiddenTag
            ComponentDescription compDesc = new fr.inra.oresing.domain.application.configuration.ComponentDescriptionBuilder()
                    .basicComponent()
                    .componentKey("secret")
                    .tags(Set.of(fr.inra.oresing.domain.application.configuration.Tag.HiddenTag.instance()))
                    .build();

            StandardDataDescription desc = mock(StandardDataDescription.class);
            when(desc.componentDescriptions()).thenReturn(Map.of("secret", compDesc));

            Configuration config = mock(Configuration.class);
            when(config.dataDescription()).thenReturn(Map.of("temperature", desc));
            app.setConfiguration(config);

            ApplicationService appService = mock(ApplicationService.class);
            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplication("myApp")).thenReturn(app);

            Map<String, List<OreSiSynthesis>> result = service.getSynthesis("myApp", "temperature", "secret");

            assertNull(result);
        }

        @Test
        @DisplayName("retourne null quand le datatype est absent de la configuration")
        void returnsNullWhenDatatypeMissing() {
            UUID appId = UUID.randomUUID();
            Application app = buildApplication(appId);

            Configuration config = mock(Configuration.class);
            when(config.dataDescription()).thenReturn(Map.of());
            app.setConfiguration(config);

            ApplicationService appService = mock(ApplicationService.class);
            when(serviceContainer.applicationService()).thenReturn(appService);
            when(appService.getApplication("myApp")).thenReturn(app);

            Map<String, List<OreSiSynthesis>> result = service.getSynthesis("myApp", "unknownType", "avg");

            assertNull(result);
        }
    }
}