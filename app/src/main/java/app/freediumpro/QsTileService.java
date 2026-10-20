package app.freediumpro;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** Quick Settings tile: opens the app and offers to read the link on the clipboard. */
public class QsTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        Intent i = new Intent(this, HomeActivity.class)
                .setAction(HomeActivity.ACTION_PASTE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            //noinspection deprecation
            startActivityAndCollapse(i);
        }
    }
}
