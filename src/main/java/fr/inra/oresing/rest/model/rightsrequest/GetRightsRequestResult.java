package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.List;
import java.util.SortedSet;


public record GetRightsRequestResult(SortedSet<GetGrantableResult.User> users, List<RightsRequestResult> rightsRequests,
                                     fr.inra.oresing.domain.application.configuration.RightRequestDescription description) {
}