package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.hateoas.Identifiable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
@ToString
public abstract class OreSiEntity implements Identifiable<UUID> {
    private UUID id = UUID.randomUUID();
    private Date creationDate;
    private Date updateDate;
}
