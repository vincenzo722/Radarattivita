package it.violante.radarattivita;

import android.app.*;
import android.content.*;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){
        String name=i.getStringExtra("name"); if(name==null)name="Attività";
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        String ch="richiami";
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"Richiami clienti",NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,ch):new Notification.Builder(c);
        b.setContentTitle("Da richiamare: "+name).setContentText("È arrivato il momento del prossimo contatto.").setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);
        nm.notify((int)(System.currentTimeMillis()%100000),b.build());
    }
}