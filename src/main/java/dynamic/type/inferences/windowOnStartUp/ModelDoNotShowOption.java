package dynamic.type.inferences.windowOnStartUp;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

@State(
        name = "VaDimaState",
        storages = {@Storage("vaDimaPlugin.xml")}
)
public class ModelDoNotShowOption implements DialogWrapper.DoNotAskOption, PersistentStateComponent<ModelDoNotShowOption.VaDimaState> {
    private VaDimaState myVaDimaState = new VaDimaState();

    public static ModelDoNotShowOption getInstance() {
        return ServiceManager.getService(ModelDoNotShowOption.class);
    }

    @Override
    public boolean isToBeShown() {
        return false;
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
        myVaDimaState.toBeShown = toBeShown;
    }

    @Override
    public boolean canBeHidden() {
        return false;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
        return false;
    }

    @Override
    public @NotNull @NlsContexts.Checkbox String getDoNotShowMessage() {
        return "Never show this again";
    }

    @Override
    @NotNull
    public ModelDoNotShowOption.VaDimaState getState() {
        return myVaDimaState;
    }

    @Override
    public void loadState(@NotNull ModelDoNotShowOption.VaDimaState vaDimaState) {
        myVaDimaState = vaDimaState;
    }

    public static class VaDimaState {
        public boolean toBeShown;
    }

}
