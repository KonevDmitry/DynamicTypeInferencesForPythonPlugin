package dynamic.type.inferences.windowOnStartUp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import dynamic.type.inferences.model.translator.RanksGetter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.lang.documentation.DocumentationMarkup.*;

public class WindowExistingClasses extends DialogWrapper {

    private final ModelDoNotShowOption modelDoNotShowOption = ModelDoNotShowOption.getInstance();

    private static final String HTML_NEW_LINE = "<br>";
    private static final String HTML_OPEN_TAG = "<html>";
    private static final String HTML_CLOSE_TAG = "</html>";

    public WindowExistingClasses(Project project) {
        super(project);

        init();
        setTitle("VaDima Plugin Information");

        myCheckBoxDoNotShowDialog = new JBCheckBox(modelDoNotShowOption.getDoNotShowMessage());
        setDoNotAskOption(modelDoNotShowOption);
        createDoNotAskCheckbox();
    }

    @Override
    @Nullable
    protected JComponent createCenterPanel() {
        ModelDoNotShowOption.VaDimaState vaDimaState = modelDoNotShowOption.getState();
            JPanel jPanel = new JPanel(new BorderLayout());
            RanksGetter ranksGetter = new RanksGetter();
            List<String> ranks = ranksGetter.getRanksFromFile();
            JBList<String> jbList = new JBList<>(ranks);
            JBScrollPane scrollPane = new JBScrollPane(jbList);

            scrollPane.createVerticalScrollBar();
            JLabel jLabel = new JLabel(
                    HTML_OPEN_TAG
                            .concat(DEFINITION_START)
                            .concat("Thanks for downloading VaDima plugin!")
                            .concat(DEFINITION_END)
                            .concat(HTML_NEW_LINE)
                            .concat(SECTIONS_START)
                            .concat("VaDima can recognize next variable types:")
                            .concat(HTML_CLOSE_TAG)
            );

            JBCheckBox checkBox = new JBCheckBox("Never show again");
            jPanel.add(jLabel, BorderLayout.NORTH);
            jPanel.add(scrollPane, BorderLayout.CENTER);
            jPanel.add(checkBox, BorderLayout.AFTER_LAST_LINE);
            checkBox.addChangeListener(e -> {
                vaDimaState.toBeShown = checkBox.isSelected();
            });
            return jPanel;
        }
}
