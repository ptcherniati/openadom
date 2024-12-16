package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import lombok.Value;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class RightsRequestResult {
    UUID id;
    UUID application;
    UUID user;
    Timestamp createDate;
    Timestamp updateDate;
    String comment;
    Map<String, String> rightsRequestForm;
    Map<String, List<AuthorizationParsed>> rightsRequest;
    boolean setted;

    public RightsRequestResult(
            final RightsRequest rightsRequest,
            final Map<String, List<AuthorizationParsed>> authorizationsParsed) {
        super();
        id = rightsRequest.getId();
        application = rightsRequest.getApplication();
        user = rightsRequest.getUser();
        comment = rightsRequest.getComment();
        rightsRequestForm = rightsRequest.getRightsRequestForm();
        setted = rightsRequest.isSetted();
        this.rightsRequest = authorizationsParsed;
        this.createDate = Timestamp.valueOf(rightsRequest.getCreationDate());
        this.updateDate = Timestamp.valueOf(rightsRequest.getUpdateDate());
    }
}