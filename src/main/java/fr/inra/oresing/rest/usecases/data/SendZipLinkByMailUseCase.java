package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.filesenderclient.MessageInformations;
import fr.inra.oresing.rest.data.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class SendZipLinkByMailUseCase {

    private final DataService dataService;

    public void execute(Path filePath, MessageInformations messageInformations, OreSiUser currentUser) {
        dataService.sendZipLinkByMail(filePath, messageInformations, currentUser);
    }
}