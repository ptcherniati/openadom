package fr.inra.oresing.rest;

import fr.inra.oresing.model.Configuration;
import lombok.Value;

import java.util.List;
import java.util.SortedSet;

@Value
public class GetRightsRequestResult {
    SortedSet<GetGrantableResult.User> users;
    List<RightsRequestResult> rightsRequests;
    Configuration.RightsRequestDescription description;
}