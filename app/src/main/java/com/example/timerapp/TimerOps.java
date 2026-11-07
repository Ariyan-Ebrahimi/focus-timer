package com.example.timerapp;

import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * The one place timer state changes. Used by the activity, notification buttons, the
 * alarm screen, the quick-settings tile and the alarm/boot receivers.
 */
public final class TimerOps {

    public static final long MINUTE = 60_000L;

    private TimerOps() {
    }

    private static void schedule(Context c, TimerItem it) {
        if (AlarmHelper.canSchedule(c)) {
            try {
                AlarmHelper.schedule(c, it);
            } catch (Exception ignored) {
            }
        }
    }

    public static void start(Context c, TimerItem it) {
        if (it.state == TimerItem.FINISHED || it.remaining <= 0) {
            it.rewind();
        }
        if (it.state != TimerItem.PAUSED) {
            it.span = it.remaining;
            it.pauses = 0;
        }
        if (it.remaining <= 0) {
            return;
        }
        it.deadline = System.currentTimeMillis() + it.remaining;
        it.state = TimerItem.RUNNING;
        schedule(c, it);
    }

    /**
     * Strict mode: while a focus step runs it can't be paused, stopped or reset
     * (breaks and the extra minute are free).
     */
    public static boolean strictLocked(Context c, TimerItem it) {
        return PrefsStore.getStrict(c) && it.state == TimerItem.RUNNING && !it.bonus
                && it.deadline > System.currentTimeMillis()
                && !(it.isSequence() && it.currentStep().rest);
    }

    /** True while any timer is in a strict focus step. */
    public static boolean anyStrictLocked(Context c) {
        for (TimerItem it : PrefsStore.loadTimers(c)) {
            if (strictLocked(c, it)) {
                return true;
            }
        }
        return false;
    }

    /** "Strict mode: stay focused until 12:34". */
    static void sayLocked(Context c, TimerItem it) {
        String at = android.text.format.DateFormat.getTimeFormat(c)
                .format(new java.util.Date(it.deadline));
        android.widget.Toast.makeText(c, c.getString(R.string.strict_blocked, at),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Ends a strict session early ("Give up"): nothing is logged for it. */
    public static void giveUp(Context c, TimerItem it) {
        AlarmHelper.cancel(c, it.id);
        it.rewind();
    }

    public static void pause(Context c, TimerItem it) {
        if (it.state != TimerItem.RUNNING) {
            return;
        }
        if (strictLocked(c, it)) {
            sayLocked(c, it);
            return;
        }
        it.remaining = Math.max(0L, it.deadline - System.currentTimeMillis());
        it.state = TimerItem.PAUSED;
        it.pauses++;
        AlarmHelper.cancel(c, it.id);
    }

    /**
     * Counts the time already studied when a timer is stopped early, so project hours
     * reflect real work (only if at least a minute, and never for breaks).
     */
    public static void logPartial(Context c, TimerItem it) {
        if (it.bonus || (it.state != TimerItem.RUNNING && it.state != TimerItem.PAUSED)) {
            return;
        }
        long left = it.state == TimerItem.RUNNING
                ? Math.max(0L, it.deadline - System.currentTimeMillis()) : it.remaining;
        long done = it.progressSpan() - left;
        if (done < MINUTE) {
            return;
        }
        // stopping early lowers the score in proportion to what was left
        int score = Insights.score(it.pauses, done / (float) it.progressSpan());
        if (it.isSequence()) {
            TimerItem.Step st = it.currentStep();
            if (!st.rest) {
                PrefsStore.addHistory(c, it.name + " \u00b7 " + st.name, done, it.projectId,
                        score);
            }
        } else {
            PrefsStore.addHistory(c, it.name, done, it.projectId, score);
        }
    }

    public static void reset(Context c, TimerItem it) {
        if (strictLocked(c, it)) {
            sayLocked(c, it);
            return;
        }
        logPartial(c, it);
        AlarmHelper.cancel(c, it.id);
        it.rewind();
    }

    /** +1 min: extends a running/paused timer, or runs one extra minute after it ended. */
    public static void addMinute(Context c, TimerItem it) {
        if (it.state == TimerItem.RUNNING) {
            it.deadline += MINUTE;
            it.span += MINUTE;
            schedule(c, it);
        } else if (it.state == TimerItem.PAUSED) {
            it.remaining += MINUTE;
            it.span += MINUTE;
        } else {
            it.remaining = MINUTE;
            it.span = MINUTE;
            it.bonus = true;
            it.deadline = System.currentTimeMillis() + MINUTE;
            it.state = TimerItem.RUNNING;
            schedule(c, it);
        }
    }

    public static final int NONE = 0;
    public static final int STEP = 1;
    public static final int DONE = 2;

    /**
     * Called when a running timer reaches its deadline. Sequences move on to their next
     * step; everything else finishes. Safe to call more than once.
     */
    static int onDeadline(Context c, TimerItem it, long now) {
        if (it.state != TimerItem.RUNNING || it.deadline > now + 250) {
            return NONE;
        }
        if (it.bonus) {
            finish(it);
            return DONE;
        }
        if (!it.isSequence()) {
            // the whole run, including any "+1 min" added along the way
            PrefsStore.addHistory(c, it.name, it.progressSpan(), it.projectId,
                    Insights.score(it.pauses, 1f));
            finish(it);
            return DONE;
        }
        int result = NONE;
        // catch up on every step that ended (e.g. while the phone was asleep)
        while (it.state == TimerItem.RUNNING && it.deadline <= now + 250) {
            TimerItem.Step done = it.currentStep();
            if (!done.rest) {
                PrefsStore.addHistory(c, it.name + " \u00b7 " + done.name, done.ms,
                        it.projectId, Insights.score(it.pauses, 1f));
            }
            // each step is scored on its own
            it.pauses = 0;
            if (it.position() + 1 >= it.stepCount()) {
                finish(it);
                return DONE;
            }
            it.stepIdx++;
            if (it.stepIdx >= it.steps.size()) {
                it.stepIdx = 0;
                it.round++;
            }
            it.total = it.currentStep().ms;
            it.span = it.total;
            it.deadline += it.total;
            it.remaining = it.deadline - now;
            result = STEP;
        }
        schedule(c, it);
        return result;
    }

    private static void finish(TimerItem it) {
        it.state = TimerItem.FINISHED;
        it.remaining = 0;
        it.bonus = false;
    }

    // ------------------------------------------------------------ persisted helpers

    public interface Op {
        void run(Context c, TimerItem it);
    }

    /** Loads the saved timers, applies op to one of them, saves and refreshes all UIs. */
    public static TimerItem apply(Context c, long id, Op op) {
        List<TimerItem> list = PrefsStore.loadTimers(c);
        TimerItem target = null;
        for (TimerItem it : list) {
            if (it.id == id) {
                target = it;
                break;
            }
        }
        if (target != null) {
            op.run(c, target);
            PrefsStore.saveTimers(c, list);
        }
        refreshSurfaces(c);
        return target;
    }

    public static void refreshSurfaces(Context c) {
        LiveNotifier.update(c);
        QuickTile.refresh(c);
        MainActivity.notifyExternalChange();
    }

    /** Handles a deadline for timer id: advances a sequence or rings the alarm. */
    public static void handleDeadline(Context c, long id) {
        final int[] outcome = {NONE};
        TimerItem it = apply(c, id, new Op() {
            @Override
            public void run(Context ctx, TimerItem t) {
                outcome[0] = onDeadline(ctx, t, System.currentTimeMillis());
            }
        });
        if (it == null) {
            return;
        }
        if (outcome[0] == STEP) {
            NotificationHelper.notifyStep(c, it);
        } else if (outcome[0] == DONE) {
            ring(c, it.id, it.name, it.plannedTotal());
        }
    }

    /** Full-screen alarm: insistent notification, plus the alarm screen if we're in front. */
    public static void ring(Context c, long id, String name, long total) {
        NotificationHelper.notifyAlarm(c, id, name, total);
        if (MainActivity.isVisible()) {
            Intent i = AlarmActivity.intent(c, id, name, total);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }
}
