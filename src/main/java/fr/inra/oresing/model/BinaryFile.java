package fr.inra.oresing.model;

import fr.inra.oresing.persistence.BinaryFileInfos;
import fr.inra.oresing.persistence.UserRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.postgresql.util.Base64;

import javax.xml.bind.DatatypeConverter;
import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class BinaryFile extends OreSiEntity {
    public final static BinaryFileInfos EMPTY_INSTANCE(){
        return new BinaryFileInfos();
    }
    private UUID application;
    private String name;
    private long size;
    private byte[] data;
    private BinaryFileInfos params;
}
