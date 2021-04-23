package dynamic.type.inferences.model.loader;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import dynamic.type.inferences.GlobalProjectInstances;

import java.io.*;

public class BertModelLoader {

    final Object sharedObject;

    public BertModelLoader(Object sharedObject) {
        this.sharedObject = sharedObject;
    }

    public void loadTo(String pathToLoad) throws IOException, DbxException {
//         model loading process with connection to DropBox api
        DbxRequestConfig config = new DbxRequestConfig("BertModelLoader");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(GlobalProjectInstances.URL_TOKEN.openStream()));

        String token = reader.readLine();
        reader.close();
        DbxClientV2 client = new DbxClientV2(config, token);
        OutputStream outputStream = new FileOutputStream(pathToLoad);
        client
                .files()
                .downloadBuilder(GlobalProjectInstances.MODEL_NAME)
                .download(outputStream);
        synchronized (sharedObject) {
            sharedObject.notify();
        }
    }
}