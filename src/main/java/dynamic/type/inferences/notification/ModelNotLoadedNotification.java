package dynamic.type.inferences.notification;

import com.intellij.notification.*;


public class ModelNotLoadedNotification {

    public Notification createNotLoadedNotification() {
        return NotificationGroupManager
                .getInstance()
                .getNotificationGroup("VaDima Notification Group")
                .createNotification(
                        "VaDima plugin info",
                        "Model not loaded",
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

//    public Notification create512Notification(String funcName) {
//        return NotificationGroupManager
//                .getInstance()
//                .getNotificationGroup("VaDima Notification Group")
//                .createNotification(
//                        "VaDima 512 limitation",
//                        String.format("Cannot predict variables <br>for function: %s", funcName),
//                        "Function code has length more than 512.",
//                        NotificationType.INFORMATION);
//    }
}
