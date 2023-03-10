package fr.inra.oresing.rest;

import fr.inra.oresing.model.rightsrequest.RightsRequest;
import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class RightsRequestResult {
    UUID id;
    UUID application;
    UUID user;

    String comment;
    Map<String, String> rightsRequestForm;
    Map<String, Map<OperationType, List<AuthorizationParsed>>> rightsRequest;
    Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> authorizationByDatatypeAndPath;
    boolean setted;

    public RightsRequestResult(RightsRequest rightsRequest, Map<String, Map<OperationType, List<AuthorizationParsed>>> authorizationsparsed, Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> authorizationByDatatypeAndPath) {
        this.id = rightsRequest.getId();
        this.application= rightsRequest.getApplication();
        this.user = rightsRequest.getUser();
        this.comment = rightsRequest.getComment();
        this.rightsRequestForm = rightsRequest.getRightsRequestForm();
        this.setted = rightsRequest.isSetted();
        this.rightsRequest = authorizationsparsed;
        this.authorizationByDatatypeAndPath = authorizationByDatatypeAndPath;
    }
}