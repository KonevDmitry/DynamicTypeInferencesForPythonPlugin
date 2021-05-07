package dynamic.type.inferences.notification.settings;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.startUpActivity.windowOnStartUp.ModelDoNotShowOption;

import javax.swing.*;

/**
 * Class for creating Settings option In PyCharm with checkbox "show types at PyCharm start"
 * and the same information inside settings.
 * <p>
 * Used inside {@link ModelSettingsConfigurable}
 */
public class ModelApplicationSettingsComponent {

    private final JPanel mainPanel;
    private final JBCheckBox modelStatus = new JBCheckBox("Show VaDima recognizable types at PyCharm start");

    private static final String HTML_BOLD_START = "<html><b>";
    private static final String HTML_BOLD_END = "</b></html>";

    /**
     * Main class for creating mentioned above objects.
     */
    public ModelApplicationSettingsComponent() {
//        Load or define state from xml file created by PyCharm.
        ModelDoNotShowOption.VaDimaState state = ModelDoNotShowOption.getInstance().getState();
//        Set the value of checkbox that user selected
        modelStatus.setSelected(state.toBeShown);
//        Load ranks from file and put them into scroll pane
        JBScrollPane scrollPane = GlobalProjectInstances.getRanksScrollPanel();
        JLabel jLabel = new JBLabel(HTML_BOLD_START + "Recognizable types by VaDima: " + HTML_BOLD_END);

        jLabel.setBorder(BorderFactory.createEmptyBorder(30, 0, 0, 0));

//        Put everything together
        mainPanel = FormBuilder
                .createFormBuilder()
                .addComponent(modelStatus, 0)
                .addComponent(jLabel, 1)
                .addComponent(scrollPane, 2)
                .addComponentFillVertically(new JPanel(), 3)
                .getPanel();
    }

    /**
     * Simple getter of panel
     *
     * @return panel
     */
    public JPanel getPanel() {
        return mainPanel;
    }

    /**
     * Get element to be focused on
     *
     * @return status of the model
     */
    public JComponent getPreferredFocusedComponent() {
        return modelStatus;
    }

    /**
     * Getter of checkBox select
     *
     * @return status of the model heckBox
     */
    public boolean getModelStatus() {
        return modelStatus.isSelected();
    }

    /**
     * Setter of checkBox select
     *
     * @param newStatus status to select
     */
    public void setModelStatus(boolean newStatus) {
        modelStatus.setSelected(newStatus);
    }
}
