package dynamic.type.inferences.notification.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts;
import dynamic.type.inferences.startUpActivity.windowOnStartUp.ModelDoNotShowOption;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * From {@link Configurable}:
 * "Configurable component that provides a Swing form to configure some settings via the Settings dialog."
 * <p>
 * This settings is defined for VaDima plugin: model an recognizable types.
 */
public class ModelSettingsConfigurable implements Configurable {

    private ModelApplicationSettingsComponent component;

    private final ModelDoNotShowOption.VaDimaState state = ModelDoNotShowOption.getInstance().getState();

    /**
     * Visible name of configurable settings.
     *
     * @return "VaDima"
     */
    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "VaDima";
    }

    /**
     * Get element to focus on
     *
     * @return the status of the checkBox to be selected
     */
    @Override
    public JComponent getPreferredFocusedComponent() {
        return component.getPreferredFocusedComponent();
    }

    /**
     * Create swing form that is created inside {@link ModelApplicationSettingsComponent}
     *
     * @return main panel of {@link ModelApplicationSettingsComponent}
     */
    @Override
    public @Nullable JComponent createComponent() {
        component = new ModelApplicationSettingsComponent();
        return component.getPanel();
    }

    /**
     * Check if form was modified. In this case - checkBox select
     *
     * @return check if something was modified
     */
    @Override
    public boolean isModified() {
        return component.getModelStatus() != state.toBeShown;
    }

    /**
     * Apply changes inside a form
     */
    @Override
    public void apply() {
        state.toBeShown = component.getModelStatus();
    }

    /**
     * Loads the settings from the configurable component -> get checkBox status in vaDima case
     */
    @Override
    public void reset() {
        component.setModelStatus(state.toBeShown);
    }
}
