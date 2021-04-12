package dynamic.type.inferences.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;


public class VaDimaNotification {

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
