package fr.inra.oresing.rest;

import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.rightsrequest.RightsRequest;
import lombok.Value;

import java.util.List;

@Value
public class GetRightsRequestResult {
    List<RightsRequest> rightsRequests;
    Configuration.RightsRequestDescription description;
}