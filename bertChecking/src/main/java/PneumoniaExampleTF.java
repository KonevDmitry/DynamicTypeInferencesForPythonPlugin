import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PneumoniaExampleTF {

    private static final Logger logger = LoggerFactory.getLogger(PneumoniaExampleTF.class);

    private static final List<String> CLASSES = Arrays.asList("Normal", "Pneumonia");

    public static void main(String[] args) throws IOException, TranslateException, ModelException {
        String imagePath;
        if (args.length == 0) {
            imagePath = "https://djl-ai.s3.amazonaws.com/resources/images/chest_xray.jpg";
            logger.info("Input image not specified, using image:\n\t{}", imagePath);
        } else {
            imagePath = args[0];
        }
        Image image;
        if (imagePath.startsWith("http")) {
            image = ImageFactory.getInstance().fromUrl(imagePath);
        } else {
            image = ImageFactory.getInstance().fromFile(Paths.get(imagePath));
        }

        Translator<Image, Classifications> translator =
                ImageClassificationTranslator.builder()
                        .addTransform(a -> NDImageUtils.resize(a, 224).div(255.0f))
                        .optSynset(CLASSES)
                        .build();
        Criteria<Image, Classifications> criteria =
                Criteria.builder()
                        .setTypes(Image.class, Classifications.class)
                        .optModelUrls("/home/dmitry/IdeaProjects/BertChecking/sources/model/pretrained/pneumania")
                        .optTranslator(translator)
                        .build();

        try (ZooModel<Image, Classifications> model = ModelZoo.loadModel(criteria);
             Predictor<Image, Classifications> predictor = model.newPredictor()) {
            Classifications result = predictor.predict(image);
            logger.info("Diagnose: {}", result);
        }
    }
}