package dynamic.type.inferences.notification.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import dynamic.type.inferences.notification.ModelNotLoadedNotification;
import dynamic.type.inferences.windowOnStartUp.ModelDoNotShowOption;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ModelSettingsConfigurable implements Configurable {
    private ModelApplicationSettingsComponent component;

    private final ModelDoNotShowOption.VaDimaState state = ModelDoNotShowOption.getInstance().getState();

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "VaDima Settings";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return component.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable JComponent createComponent() {
        component = new ModelApplicationSettingsComponent();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        return component.getModelStatus() != state.toBeShown;
    }

    @Override
    public void apply() throws ConfigurationException {
        state.toBeShown = component.getModelStatus();
    }

    @Override
    public void reset() {
        component.setModelStatus(state.toBeShown);
    }
}
