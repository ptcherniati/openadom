package fr.inra.oresing.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
@ToString
public abstract class OreSiEntity {
    private UUID id = UUID.randomUUID();
    private LocalDateTime creationDate;
    private LocalDateTime updateDate;
}
