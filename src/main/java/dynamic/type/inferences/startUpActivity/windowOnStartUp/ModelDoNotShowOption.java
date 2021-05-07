package dynamic.type.inferences.startUpActivity.windowOnStartUp;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * Component entity which persists its state across IDE restarts
 * For VaDima only stored the option "show variables at PyCharm start"
 */
@State(
        name = "VaDimaState",
        storages = {@Storage("vaDimaPlugin.xml")}
)
public class ModelDoNotShowOption implements DialogWrapper.DoNotAskOption, PersistentStateComponent<ModelDoNotShowOption.VaDimaState> {
    private VaDimaState myVaDimaState = new VaDimaState();

    /**
     * Simple getter of own instance
     *
     * @return own instance
     */
    public static ModelDoNotShowOption getInstance() {
        return ServiceManager.getService(ModelDoNotShowOption.class);
    }

    /**
     * Default value is set to false
     *
     * @return false at first start. After that state from vaDimaPlugin.xml
     */
    @Override
    public boolean isToBeShown() {
        return false;
    }

    /**
     * Setter for option to be shown
     *
     * @param toBeShown value to be shown - true/false
     * @param exitCode  exit code - not used
     */
    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
        myVaDimaState.toBeShown = toBeShown;
    }

    /**
     * Option to show checkbox
     *
     * @return false
     */
    @Override
    public boolean canBeHidden() {
        return false;
    }

    /**
     * @return Do not save option on cancel
     */
    @Override
    public boolean shouldSaveOptionsOnCancel() {
        return false;
    }

    /**
     * @return CheckBox text "Do not show again"
     */
    @Override
    public @NotNull @NlsContexts.Checkbox String getDoNotShowMessage() {
        return "Never show this again";
    }

    /**
     * Getter of current state
     *
     * @return current state
     */
    @Override
    @NotNull
    public ModelDoNotShowOption.VaDimaState getState() {
        return myVaDimaState;
    }

    /**
     * Method is called when new component state is loaded.
     *
     * @param vaDimaState loaded state
     */
    @Override
    public void loadState(@NotNull ModelDoNotShowOption.VaDimaState vaDimaState) {
        myVaDimaState = vaDimaState;
    }

    /**
     * Class initializer
     */
    public static class VaDimaState {
        public boolean toBeShown;
    }

}
