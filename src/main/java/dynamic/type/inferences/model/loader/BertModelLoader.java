package dynamic.type.inferences.model.loader;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.*;

public class BertModelLoader {
    final Object sharedObject;

    public BertModelLoader(Object sharedObject) {
        this.sharedObject = sharedObject;
    }

    public void loadTo(String pathToLoad) throws IOException, DbxException {
        DbxRequestConfig config = new DbxRequestConfig("BertModelLoader");
        String token = "zkNb09HCVTAAAAAAAAAAAVb0XG9hL-oQMWzmnieliPzboj8g2UOnCNXeAtNsCdkU";

        DbxClientV2 client = new DbxClientV2(config, token);
        OutputStream outputStream = new FileOutputStream(pathToLoad);
        FileMetadata metadata = client
                .files()
                .downloadBuilder("/eeee.pt")
                .download(outputStream);
        synchronized (sharedObject) {
            sharedObject.notify();
        }
    }
}