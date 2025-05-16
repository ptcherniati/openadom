package fr.inra.oresing.domain.data.read.query;

import java.util.List;

public record AuthorizationDescription(IntervalValues timeScope,
                                       List<List<RequiredAuthorization>> requiredAuthorizations) {


}