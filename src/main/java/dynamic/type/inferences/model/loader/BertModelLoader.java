package dynamic.type.inferences.model.loader;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;

import java.io.*;
import java.net.URL;
import java.util.Objects;

public class BertModelLoader {
    final Object sharedObject;

    public BertModelLoader(Object sharedObject) {
        this.sharedObject = sharedObject;
    }

    public void loadTo(String pathToLoad) throws IOException, DbxException {
        // model loading process with connection to DropBox api
        DbxRequestConfig config = new DbxRequestConfig("BertModelLoader");
        URL url = getClass().getClassLoader().getResource("/data/torchBERT/token");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(url).openStream()));

        String token = reader.readLine();
        reader.close();
        DbxClientV2 client = new DbxClientV2(config, token);
        OutputStream outputStream = new FileOutputStream(pathToLoad);
        client
                .files()
                .downloadBuilder("/eeee.pt")
                .download(outputStream);
        synchronized (sharedObject) {
            sharedObject.notify();
        }
    }
}