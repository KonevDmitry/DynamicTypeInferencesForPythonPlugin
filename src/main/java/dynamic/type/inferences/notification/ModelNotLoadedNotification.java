package dynamic.type.inferences.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;


public class ModelNotLoadedNotification {
    private static final NotificationGroup BALOON_GROUP =
            new NotificationGroup("VaDima",
                    NotificationDisplayType.BALLOON, false);


    public Notification createNotification() {
        return BALOON_GROUP.createNotification(
                "VaDima plugin info",
                "Model not loaded",
                "Predictions for user-defined functions will be available after model load.\n",
                NotificationType.INFORMATION);
    }
}
