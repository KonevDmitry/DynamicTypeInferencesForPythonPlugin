package dynamic.type.inferences.notification.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import dynamic.type.inferences.windowOnStartUp.ModelDoNotShowOption;

import javax.swing.*;

public class ModelApplicationSettingsComponent {
    private final JPanel mainPanel;
    private final JBCheckBox modelStatus = new JBCheckBox("Do not show VaDima notification at PyCharm start");

    public ModelApplicationSettingsComponent() {
        ModelDoNotShowOption.VaDimaState state = ModelDoNotShowOption.getInstance().getState();
        modelStatus.setSelected(state.toBeShown);
        mainPanel = FormBuilder
                .createFormBuilder()
                .addComponent(modelStatus, 1)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return modelStatus;
    }

    public boolean getModelStatus() {
        return modelStatus.isSelected();
    }

    public void setModelStatus(boolean newStatus) {
        modelStatus.setSelected(newStatus);
    }
}
