package dynamic.type.inferences.startUpActivity.windowOnStartUp;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import dynamic.type.inferences.GlobalProjectInstances;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Class for showing all recognizable types by model. Represents standard base class for modal dialog boxes.
 */
public class WindowExistingClasses extends DialogWrapper {

    private final ModelDoNotShowOption modelDoNotShowOption = ModelDoNotShowOption.getInstance();

    private static final String HTML_NEW_LINE = "<br>";
    private static final String HTML_OPEN_TAG = "<html>";
    private static final String HTML_CLOSE_TAG = "</html>";

    /**
     * Base builder of a class
     *
     * @param project is a current project
     */
    public WindowExistingClasses(Project project) {
//        Default way of creating dialog box from official tutorial
//        Firstly, call super and after that modify and add own objects
        super(project);

        init();
        setTitle("VaDima Plugin Information");

        myCheckBoxDoNotShowDialog = new JBCheckBox(modelDoNotShowOption.getDoNotShowMessage());
        setDoNotAskOption(modelDoNotShowOption);
        createDoNotAskCheckbox();
    }

    /**
     * Create a panel with dialog options.
     *
     * @return JPanel with added types, JLabel, scrollPane, and checkBox
     */
    @Override
    @Nullable
    protected JComponent createCenterPanel() {
        ModelDoNotShowOption.VaDimaState vaDimaState = modelDoNotShowOption.getState();
//        Main panel
        JPanel jPanel = new JPanel(new BorderLayout());
//        Recognizable types by VaDima
        JBScrollPane scrollPane = GlobalProjectInstances.getRanksScrollPanel();

//        Header and text at top of panel
        JLabel jLabel = new JLabel(
                HTML_OPEN_TAG
                        .concat(DocumentationMarkup.DEFINITION_START)
                        .concat("Thanks for downloading VaDima plugin!")
                        .concat(DocumentationMarkup.DEFINITION_END)
                        .concat(HTML_NEW_LINE)
                        .concat(DocumentationMarkup.SECTIONS_START)
                        .concat("VaDima can recognize next variable types:")
                        .concat(HTML_CLOSE_TAG)
        );

//        CheckBox for never showing option :(
        JBCheckBox checkBox = new JBCheckBox("Never show again");
//        Add all objects above and listen to checkBox changes
        jPanel.add(jLabel, BorderLayout.NORTH);
        jPanel.add(scrollPane, BorderLayout.CENTER);
        jPanel.add(checkBox, BorderLayout.AFTER_LAST_LINE);
        checkBox.addChangeListener(e -> vaDimaState.toBeShown = !checkBox.isSelected());
        return jPanel;
    }
}
