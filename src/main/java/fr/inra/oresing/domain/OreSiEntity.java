package fr.inra.oresing.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
@ToString
public abstract class OreSiEntity implements Serializable {
    private UUID id = UUID.randomUUID();
    private LocalDateTime creationDate;
    private LocalDateTime updateDate;
}