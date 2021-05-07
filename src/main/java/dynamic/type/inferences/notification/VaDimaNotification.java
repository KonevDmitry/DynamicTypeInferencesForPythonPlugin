package dynamic.type.inferences.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import dynamic.type.inferences.completer.ModelCompletionProvider;
import dynamic.type.inferences.documentationProvider.ModelDocumentationProvider;


/**
 * Class for type hinting. Used inside {@link ModelCompletionProvider} and {@link ModelDocumentationProvider}
 * Used for notifying user about errors (if something crashed) and warnings (when model is not loaded)
 */
public class VaDimaNotification {

    /**
     * Warning notification
     *
     * @return Balloon warning notification
     */
    public Notification createNotLoadedNotification() {
        return NotificationGroupManager
                .getInstance()
                .getNotificationGroup("VaDima Notification Group")
                .createNotification(
                        "VaDima plugin info",
                        "Model is not loaded",
                        "Predictions for functions will be available after model load.\n",
                        NotificationType.INFORMATION);
    }

    /**
     * Error notification
     *
     * @return Balloon error notification
     */
    public Notification createErrorNotification() {
        return NotificationGroupManager
                .getInstance()
                .getNotificationGroup("VaDima Notification Group")
                .createNotification(
                        "VaDima plugin error",
                        "Internal error.",
                        "Reloading model...",
                        NotificationType.ERROR);
    }
}
