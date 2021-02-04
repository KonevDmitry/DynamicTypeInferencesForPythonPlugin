package dynamic.type.inferences.modelWorker;

import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import dynamic.type.inferences.modelWorker.runner.BertLoader;

import java.io.IOException;
import java.util.ArrayList;

public class MainBertCaller {
    private static BertLoader loader;

    public static void main(String[] args) throws MalformedModelException, ModelNotFoundException, IOException, TranslateException {
        loader = new BertLoader();
        loader.modelInit();
        ArrayList<String> list = new ArrayList<String>() {{
            add("you are awesome");
            add("bad, very bad");
        }};
        ArrayList<Classifications> classifications = loader.predict(list);
        
        for(int i = 0; i< (classifications != null ? classifications.size() : 0); i++){
            System.out.println(list.get(i)+" , "+classifications.get(i));
        }
    }
}
