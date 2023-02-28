package fr.inra.oresing.model.rightsrequest;

import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class RightsRequest extends OreSiEntity {

    public final static RightsRequest EMPTY_INSTANCE() {
        return new RightsRequest();
    }

    UUID application;
    UUID user;

    String comment;
    Map<String, String> rightsRequestForm;
    OreSiAuthorization rightsRequest;
}