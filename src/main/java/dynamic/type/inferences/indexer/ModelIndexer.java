package dynamic.type.inferences.indexer;

import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.pyi.PyiFileType;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.startUpActive.ModelStartUpActivity;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The code below is not used and provides some plain implementation of indexer.
 * We tried to create predictions on indexer step for better performance during typical PyCharm work.
 *
 * But solution with indexer occurred to be too terrible. At least 30 minutes of indexing
 * and several gigabytes of memory...
 *
 * Moreover, the code bellow is not finished - it includes only predictions for
 * functions without their name/path (the last one is more preferable because of same function names)
 *
 * The code below is not commented because it is not used.
 */
public class ModelIndexer extends ScalarIndexExtension<List<Classifications.Classification>> {

    public static final ID<List<Classifications.Classification>, Void> NAME = ID.create("VaDimaIndexer");

    @Override
    public @NotNull ID<List<Classifications.Classification>, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<List<Classifications.Classification>, Void, FileContent> getIndexer() {
        return new DataIndexer<List<Classifications.Classification>, Void, FileContent>() {

            private final TorchBert torchBert = ModelStartUpActivity.getTorchBertInstance();

            @NotNull
            @Override
            public Map<List<Classifications.Classification>, Void> map(@NotNull FileContent inputData) {
                final PsiFile psiFile = inputData.getPsiFile();
                final Map<List<Classifications.Classification>, Void> results = new HashMap<>();
                if (psiFile instanceof PyFile) {
                    try {
                        torchBert.modelInit();
                    } catch (IOException | MalformedModelException | ModelNotFoundException | URISyntaxException | DbxException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    PyFile pyFile = (PyFile) psiFile;
                    @NotNull Collection<PyFunction> res = PsiTreeUtil.findChildrenOfType(pyFile, PyFunction.class);
                    res.forEach(elem -> {
                        try {
                            results.put(torchBert.predictOne(elem), null);
                        } catch (TranslateException e) {
                            e.printStackTrace();
                        }
                    });
                }
                return results;
            }
        };
    }

    @Override
    public @NotNull KeyDescriptor<List<Classifications.Classification>> getKeyDescriptor() {
        return ClassificationStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    @NotNull
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> FileTypeRegistry.getInstance().isFileOfType(file, PythonFileType.INSTANCE) ||
                FileTypeRegistry.getInstance().isFileOfType(file, PyiFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }


    @NotNull
    public static Collection<List<Classifications.Classification>> getAllKeys(@NotNull Project project) {
        return FileBasedIndex.getInstance().getAllKeys(NAME, project);
    }
}
