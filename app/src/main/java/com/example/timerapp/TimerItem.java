package com.example.timerapp;

import java.util.ArrayList;
import java.util.List;

/**
 * A countdown timer. With {@link #steps} it is a sequence (e.g. Pomodoro or work/rest
 * intervals): {@link #total} then always holds the length of the current step.
 */
public class TimerItem {

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    public static final int PAUSED = 2;
    public static final int FINISHED = 3;

    public static class Step {
        public String name;
        public long ms;
        /** Breaks are not counted as focus time in History. */
        public boolean rest;

        public Step(String name, long ms, boolean rest) {
            this.name = name;
            this.ms = ms;
            this.rest = rest;
        }
    }

    public long id;
    public String name;
    public long total;
    public long remaining;
    public int state;
    public long deadline;
    public boolean pinned;
    /** Length of the current run, grows with "+1 min"; drives the progress ring. */
    public long span;
    /** True while running the extra minute added after the timer finished. */
    public boolean bonus;
    /** Project this timer belongs to, 0 for none. */
    public long projectId;
    /** Times paused during the current run (or step): lowers its focus score. */
    public int pauses;

    public final List<Step> steps = new ArrayList<Step>();
    public int rounds = 1;
    public int stepIdx;
    public int round;

    public TimerItem(long id, String name, long total) {
        this.id = id;
        this.name = name;
        this.total = total;
        this.remaining = total;
        this.span = total;
        this.state = IDLE;
        this.deadline = 0L;
        this.pinned = false;
    }

    public boolean isSequence() {
        return !steps.isEmpty();
    }

    /** Number of steps over all rounds. */
    public int stepCount() {
        return isSequence() ? steps.size() * Math.max(1, rounds) : 1;
    }

    /** Index of the current step over all rounds. */
    public int position() {
        return isSequence() ? round * steps.size() + stepIdx : 0;
    }

    public Step currentStep() {
        return isSequence() ? steps.get(Math.min(stepIdx, steps.size() - 1)) : null;
    }

    /** Sum of every step over all rounds (or the plain duration). */
    public long plannedTotal() {
        if (!isSequence()) {
            return total;
        }
        long sum = 0;
        for (Step s : steps) {
            sum += s.ms;
        }
        return sum * Math.max(1, rounds);
    }

    public long progressSpan() {
        return span > 0 ? span : total;
    }

    /** Back to the very beginning, not running. */
    public void rewind() {
        stepIdx = 0;
        round = 0;
        bonus = false;
        if (isSequence()) {
            total = steps.get(0).ms;
        }
        remaining = total;
        span = total;
        state = IDLE;
        pauses = 0;
    }
}
