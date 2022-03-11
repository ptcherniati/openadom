package fr.inra.oresing.persistence;


import fr.inra.oresing.model.BinaryFileDataset;
import lombok.Getter;

import java.util.UUID;

@Getter
public class BinaryFileInfos {
    public final static BinaryFileInfos EMPTY_INSTANCE(){
        return new BinaryFileInfos();
    }
    public BinaryFileDataset binaryFiledataset = BinaryFileDataset.EMPTY_INSTANCE();
    public boolean published;
    public UUID publisheduser;
    public String publisheddate;
    public UUID createuser;
    public String createdate;
    public String comment;

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }
}
