package model;

import lombok.Data;

import service.RangeHandler;
import ui.MEDIA_TYPE;

import java.io.File;

@Data
public class Media {
    File file;
    MEDIA_TYPE mediaType;
    RangeHandler.Range range;
}
