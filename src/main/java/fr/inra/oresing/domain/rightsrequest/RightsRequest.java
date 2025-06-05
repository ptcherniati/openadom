package fr.inra.oresing.domain.rightsrequest;

import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class RightsRequest extends OreSiEntity {

    UUID application;
    UUID user;
    String comment;
    Map<String, String> rightsRequestForm;
    OreSiAuthorization rightsRequest;
    boolean setted;

    public static RightsRequest EMPTY_INSTANCE() {
        return new RightsRequest();
    }
}