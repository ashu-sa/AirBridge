package com.airbridge.fixture;
import android.app.*;
import android.content.*;
import android.os.Build;
/** Shell-only fixture. Fixed payloads, no account data, never bundled with AirBridge. */
public class NotificationReceiver extends BroadcastReceiver {
    public void onReceive(Context c, Intent i) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if ("airbridge.test.CANCEL".equals(i.getAction())) { m.cancel(831); return; }
        boolean call = "airbridge.test.CALL".equals(i.getAction());
        if (!call && !"airbridge.test.POST".equals(i.getAction())) return;
        NotificationChannel channel = new NotificationChannel("controlled", "Controlled test", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null); channel.enableVibration(false); m.createNotificationChannel(channel);
        Notification.Builder b = new Notification.Builder(c, "controlled").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AirBridge test").setContentText("Controlled test notification");
        if (call) {
            String phase = i.getStringExtra("phase");
            PendingIntent decline = PendingIntent.getBroadcast(c, 1, new Intent("airbridge.test.CANCEL").setClass(c, NotificationReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent answer = PendingIntent.getBroadcast(c, 2, new Intent("airbridge.test.CANCEL").setClass(c, NotificationReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setCategory(Notification.CATEGORY_CALL).setOngoing(true).setFullScreenIntent(answer, true);
            if (Build.VERSION.SDK_INT >= 31 && !"legacy".equals(phase)) {
                Person person = new Person.Builder().setName("Test caller").build();
                b.setStyle("ongoing".equals(phase) ? Notification.CallStyle.forOngoingCall(person, decline)
                    : Notification.CallStyle.forIncomingCall(person, decline, answer));
            } else {
                b.setFullScreenIntent(answer, true).addAction(new Notification.Action.Builder(0, "Decline", decline).build())
                    .addAction(new Notification.Action.Builder(0, "Answer", answer).build());
            }
        }
        Notification built = b.build();
        if (call && "legacy".equals(i.getStringExtra("phase"))) built.flags |= Notification.FLAG_INSISTENT;
        m.notify(831, built);
    }
}
