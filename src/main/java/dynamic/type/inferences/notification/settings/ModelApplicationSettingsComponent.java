package dynamic.type.inferences.notification.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.windowOnStartUp.ModelDoNotShowOption;

import javax.swing.*;

public class ModelApplicationSettingsComponent {

    private final JPanel mainPanel;
    private final JBCheckBox modelStatus = new JBCheckBox("Show VaDima recognizable types at PyCharm start");

    private static final String HTML_BOLD_START = "<html><b>";
    private static final String HTML_BOLD_END = "</b></html>";

    public ModelApplicationSettingsComponent() {
        ModelDoNotShowOption.VaDimaState state = ModelDoNotShowOption.getInstance().getState();
        modelStatus.setSelected(state.toBeShown);
        JBScrollPane scrollPane = GlobalProjectInstances.getRanksScrollPanel();
        JLabel jLabel = new JBLabel(HTML_BOLD_START + "Recognizable types by VaDima: " + HTML_BOLD_END);

        jLabel.setBorder(BorderFactory.createEmptyBorder(30, 0, 0, 0));

        mainPanel = FormBuilder
                .createFormBuilder()
                .addComponent(modelStatus, 0)
                .addComponent(jLabel, 1)
                .addComponent(scrollPane, 2)
                .addComponentFillVertically(new JPanel(), 3)
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
