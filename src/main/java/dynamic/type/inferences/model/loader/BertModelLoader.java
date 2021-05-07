package dynamic.type.inferences.model.loader;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import dynamic.type.inferences.GlobalProjectInstances;

import java.io.*;

/**
 * Class for loading model from the DropBox
 */
public class BertModelLoader {

    final Object sharedObject;

    /**
     * Default constructor with shared object for notifying other processes when model is loaded, where
     *
     * @param sharedObject is the shared object instance
     */
    public BertModelLoader(Object sharedObject) {
        this.sharedObject = sharedObject;
    }

    /**
     * Loading model to specified path. Can be used for someone if he/she will have a wish
     * to load a model to a special path (can be used in a future). Parameters:
     *
     * @param pathToLoad where to load a model.
     * @throws IOException  exception is throw if a by magic reason token not read
     * @throws DbxException DropBox problems
     */
    public void loadTo(String pathToLoad) throws IOException, DbxException {
//         Model loading process with connection to DropBox api
        DbxRequestConfig config = new DbxRequestConfig("BertModelLoader");

//        Get token and load model. Explanation: model is too big for putting
//        it inside plugin and jar file (obviously, memory leak)
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(GlobalProjectInstances.URL_TOKEN.openStream()));

        String token = reader.readLine();
        reader.close();

//        DropBox client creation and loading process
        DbxClientV2 client = new DbxClientV2(config, token);
        OutputStream outputStream = new FileOutputStream(pathToLoad);
        client
                .files()
                .downloadBuilder(GlobalProjectInstances.MODEL_NAME)
                .download(outputStream);
//        When the model is loaded, then other processes should be notified.
        synchronized (sharedObject) {
            sharedObject.notify();
        }
    }
}