package com.marinov.clearcache.ui.quicktiles;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.marinov.clearcache.R;
import com.marinov.clearcache.ui.CleanCacheDialogActivity;

public class QuickTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, CleanCacheDialogActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final int FLAG_ALLOW_BAL = (Build.VERSION.SDK_INT >= 35) ? 0x40000000 : 0x40000000;
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE
                | FLAG_ALLOW_BAL;

        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pi);
        } else {
            try {
                startActivityAndCollapse(intent);
            } catch (UnsupportedOperationException e) {
                startActivity(intent);
            }
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.qs_tile_label));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_clear_cache));
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }
}