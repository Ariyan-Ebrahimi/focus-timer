package com.example.timerapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** One line about what a session was spent on, asked right after it's logged. */
public final class NoteDialog {

    private NoteDialog() {
    }

    /** The session that was just logged automatically, if it's recent enough to ask about. */
    public static PrefsStore.Hist justLogged(Activity a, long since) {
        long ts = PrefsStore.getLastLog(a);
        if (ts < since || System.currentTimeMillis() - ts > 15 * 60_000L) {
            return null;
        }
        return PrefsStore.findHistory(a, ts);
    }

    /**
     * Asks for a note on session h. title is shown above the field (e.g. "Logged 25m to
     * Math"); null uses "Add a note". onDone (may be null) runs after saving or skipping.
     */
    public static void show(final Activity a, final PrefsStore.Hist h, String title,
                            final Runnable onDone) {
        if (a.isFinishing()) {
            return;
        }
        int main = ThemeHelper.resolve(a, R.attr.textMain);
        int sub = ThemeHelper.resolve(a, R.attr.textSub);
        int surface2 = ThemeHelper.resolve(a, R.attr.surface2);
        float d = a.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPaddingRelative(Math.round(24 * d), Math.round(8 * d), Math.round(24 * d), 0);
        final EditText note = new EditText(a);
        note.setHint(R.string.note_hint);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        note.setSingleLine(true);
        Clay.text(note, 700, 16, main);
        note.setHintTextColor(sub);
        Clay.inset(note, surface2, 18);
        note.setPaddingRelative(Math.round(18 * d), Math.round(13 * d), Math.round(18 * d),
                Math.round(13 * d));
        note.setText(h.note);
        note.setSelection(note.getText().length());
        root.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Tags.chips(a, note));

        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle(title != null ? title : a.getString(
                        h.note.isEmpty() ? R.string.add_note : R.string.edit_note))
                .setMessage(android.text.BidiFormatter.getInstance().unicodeWrap(h.name)
                        + "  \u00b7  " + Projects.hours(h.millis))
                .setView(root)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int w) {
                        String text = note.getText().toString().trim();
                        if (!text.equals(h.note)) {
                            PrefsStore.setNote(a, h.ts, text);
                            if (!text.isEmpty()) {
                                Toast.makeText(a, R.string.note_saved, Toast.LENGTH_SHORT).show();
                            }
                        }
                        if (onDone != null) {
                            onDone.run();
                        }
                    }
                })
                .setNegativeButton(R.string.skip, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int w) {
                        if (onDone != null) {
                            onDone.run();
                        }
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (onDone != null) {
                            onDone.run();
                        }
                    }
                })
                .create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        note.requestFocus();
    }
}
