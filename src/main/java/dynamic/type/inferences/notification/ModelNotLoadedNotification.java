package dynamic.type.inferences.notification;

import com.intellij.notification.*;


public class ModelNotLoadedNotification {
    private static final NotificationGroup BALOON_GROUP =
            new NotificationGroup("VaDima Notificaion Group",
                    NotificationDisplayType.BALLOON, true);

    public Notification createInfoNotification() {

        return BALOON_GROUP
                .createNotification(
                        "VaDima plugin info",
                        "Model not loaded",
                        "Predictions for user-defined functions will be available after model load.\n",
                        NotificationType.INFORMATION);
    }

    public Notification createErrorNotification() {
        return BALOON_GROUP
                .createNotification(
                        "VaDima plugin error",
                        "Model was not loaded correctly. Trying to reload.",
                        "Trying to reload...\n",
                        NotificationType.ERROR);
    }
}
