package fr.inra.oresing.rest.usecases.metadata;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.rest.services.NormalizedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetNormalizedSchemaUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @Mock
    private NormalizedService normalizedService;

    @Mock
    private ExecutorService executorService;

    private GetNormalizedSchemaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetNormalizedSchemaUseCase(applicationService, normalizedService, executorService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getNormalizedSchema_keepsSecurityContextAndReturnsSql() throws Exception {
        Application application = mock(Application.class);
        SecurityContext context = new SecurityContextImpl();
        SecurityContextHolder.setContext(context);

        when(applicationService.getApplication("app"))
                .thenReturn(application);
        stubExecutor(executorService);
        when(normalizedService.buildNormalizedSchema(application, false))
                .thenAnswer(invocation -> {
                    assertEquals(context, SecurityContextHolder.getContext());
                    return "SQL";
                });

        String result = useCase.execute("app");

        assertEquals("SQL", result);
    }

    @Test
    void getNormalizedSchema_propagatesExecutionException() throws Exception {
        Application application = mock(Application.class);
        SecurityContextHolder.setContext(new SecurityContextImpl());

        when(applicationService.getApplication("app"))
                .thenReturn(application);
        stubExecutor(executorService);
        when(normalizedService.buildNormalizedSchema(application, false))
                .thenThrow(new IllegalStateException("boom"));

        assertThrows(ExecutionException.class, () -> useCase.execute("app"));
    }

    @SuppressWarnings("unchecked")
    private static void stubExecutor(ExecutorService executorService) {
        when(executorService.submit(any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<Object> callable = invocation.getArgument(0);
                    try {
                        Object result = callable.call();
                        return CompletableFuture.completedFuture(result);
                    } catch (Exception e) {
                        CompletableFuture<Object> future = new CompletableFuture<>();
                        future.completeExceptionally(e);
                        return future;
                    }
                });
    }
}
