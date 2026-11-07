package com.example.timerapp;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.List;

public class QuickTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile t = getQsTile();
        if (t == null) {
            return;
        }
        try {
            List<TimerItem> list = PrefsStore.loadTimers(this);
            TimerItem target = list.isEmpty() ? null : list.get(0);
            boolean running = target != null && target.state == TimerItem.RUNNING;
            t.setLabel(getString(R.string.app_name));
            if (Build.VERSION.SDK_INT >= 29) {
                if (target == null) {
                    t.setSubtitle(getString(R.string.no_timers));
                } else {
                    long rem = running
                            ? Math.max(0L, target.deadline - System.currentTimeMillis())
                            : target.remaining;
                    t.setSubtitle(target.name + " \u2022 " + MainActivity.formatDuration(rem));
                }
            }
            t.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.updateTile();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        try {
            List<TimerItem> list = PrefsStore.loadTimers(this);
            TimerItem target = list.isEmpty() ? null : list.get(0);
            if (target == null) {
                target = new TimerItem(System.currentTimeMillis(), getString(R.string.quick_focus),
                        15L * 60L * 1000L);
                list.add(target);
            }
            if (target.state == TimerItem.RUNNING) {
                TimerOps.pause(this, target);
            } else {
                if (target.total <= 0) {
                    Toast.makeText(this, R.string.open_app_set_time,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                TimerOps.start(this, target);
            }
            PrefsStore.saveTimers(this, list);
            TimerOps.refreshSurfaces(this);
        } catch (Exception ignored) {
        }
    }

    public static void refresh(Context c) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                TileService.requestListeningState(
                        c, new ComponentName(c, QuickTile.class));
            } catch (Exception ignored) {
            }
        }
    }
}
