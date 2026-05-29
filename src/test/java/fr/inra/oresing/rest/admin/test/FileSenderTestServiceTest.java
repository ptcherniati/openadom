package fr.inra.oresing.rest.admin.test;

import fr.inra.oresing.config.AlertsProperties;
import fr.inra.oresing.rest.filesenderclient.FileInfos;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link FileSenderTestService} . Mock du repository
 * FileSender ( evite tout appel reseau ) - verifie genere temp + appelle
 * postTransfer + nettoie fichier .
 */
@Tag("core.config")
@DisplayName("FileSenderTestService - genere fichier + envoie + cleanup")
class FileSenderTestServiceTest {

    private FileSenderRepository fileSender;
    private AlertsProperties alertsProperties;
    private FileSenderTestService service;

    @BeforeEach
    void setUp() throws Exception {
        fileSender = mock(FileSenderRepository.class);
        alertsProperties = new AlertsProperties();
        service = new FileSenderTestService(fileSender, alertsProperties);
    }

    @Test
    @DisplayName("send : succes -> downloadUrl publie + fichier temp supprime")
    void sendSuccess() throws Exception {
        String fakeUrl = "https://filesender.example/?s=download&token=abc";
        when(fileSender.postTransfer(any(FileInfos.class))).thenReturn(fakeUrl);
        FileSenderTestRequest req = new FileSenderTestRequest(
                List.of("a@b.fr"), "subj", "msg");

        FileSenderTestResult res = service.send(req);

        assertTrue(res.success());
        assertEquals(fakeUrl, res.downloadUrl());
        assertTrue(res.fileSizeBytes() > 0);
        assertTrue(res.durationMs() >= 0);
        ArgumentCaptor<FileInfos> captor = ArgumentCaptor.forClass(FileInfos.class);
        verify(fileSender).postTransfer(captor.capture());
        FileInfos sent = captor.getValue();
        assertEquals("a@b.fr", sent.recipient());
        assertEquals("subj", sent.subject());
        assertEquals("msg", sent.message());
        // Fichier temp doit avoir ete supprime apres l'envoi
        assertFalse(sent.fileName().toFile().exists(),
                "Le fichier temp doit etre supprime par le finally");
    }

    @Test
    @DisplayName("send : aucun destinataire -> KO sans appel postTransfer")
    void sendNoRecipients() throws Exception {
        FileSenderTestRequest req = new FileSenderTestRequest(null, "s", "m");
        FileSenderTestResult res = service.send(req);
        assertFalse(res.success());
        assertEquals(0, res.fileSizeBytes());
        verifyNoInteractions(fileSender);
    }

    @Test
    @DisplayName("send : postTransfer throws -> KO + temp supprime")
    void sendCapturesException() throws Exception {
        when(fileSender.postTransfer(any(FileInfos.class)))
                .thenThrow(new RuntimeException("FileSender HS"));
        FileSenderTestRequest req = new FileSenderTestRequest(List.of("a@b.fr"), "s", "m");

        FileSenderTestResult res = service.send(req);

        assertFalse(res.success());
        assertNull(res.downloadUrl());
        assertTrue(res.errorMessage().contains("FileSender HS"));
        // Verifier que le temp file a bien ete supprime malgre l'exception
        ArgumentCaptor<FileInfos> captor = ArgumentCaptor.forClass(FileInfos.class);
        verify(fileSender).postTransfer(captor.capture());
        assertFalse(captor.getValue().fileName().toFile().exists());
    }

    @Test
    @DisplayName("defaultSample retourne valeurs par defaut + recipients de alerts")
    void defaultSample() {
        alertsProperties.setRecipients("ops@inrae.fr");
        FileSenderTestRequest sample = service.defaultSample();
        assertEquals(List.of("ops@inrae.fr"), sample.recipients());
        assertEquals(FileSenderTestService.DEFAULT_SUBJECT, sample.subject());
        assertEquals(FileSenderTestService.DEFAULT_MESSAGE, sample.message());
    }
}
