package com.airbridge.fixture;
import android.app.*;
import android.content.*;
/** Shell-only fixture. No user data; never bundled with AirBridge. */
public class NotificationReceiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent i) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if ("airbridge.test.CANCEL".equals(i.getAction())) { m.cancel(831); return; }
        if (!"airbridge.test.POST".equals(i.getAction())) return;
        NotificationChannel channel = new NotificationChannel("controlled", "Controlled test", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null); channel.enableVibration(false); m.createNotificationChannel(channel);
        m.notify(831, new Notification.Builder(c, "controlled").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AirBridge test").setContentText("Controlled test notification").build());
    }
}
