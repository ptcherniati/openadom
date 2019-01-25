package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString(callSuper = true)
public class BinaryFile extends OreSiEntity {
    private String name;
    private long size;
    private byte[] data;
}
