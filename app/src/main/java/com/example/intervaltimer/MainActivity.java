package com.example.intervaltimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;
    private static final int BLUE = 0xFF1565C0;
    private final ArrayList<Workout> workouts = new ArrayList<>();
    private final Handler handler = new Handler();
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (timer == null || paused) {
                return;
            }
            int before = timer.secondsRemaining();
            timer.tick();
            if (before <= 3 && before > 0) {
                playBeep(before == 1);
            }
            updateTimerDisplay();
            if (timer.phase() != WorkoutTimer.Phase.FINISHED) {
                handler.postDelayed(this, 1000);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    };

    private LinearLayout root;
    private WorkoutTimer timer;
    private Workout activeWorkout;
    private TextView countdown;
    private TextView phaseLabel;
    private TextView periodsLeft;
    private LinearLayout pauseControls;
    private boolean paused;
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        loadWorkouts();
        showWorkoutList();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerTick);
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (timer != null) {
            stopTimer();
        }
        showWorkoutList();
    }

    private void showWorkoutList() {
        timer = null;
        paused = false;
        root = createRoot();
        TextView title = text("Workouts", 28, 0xFF222222);
        title.setPadding(20, 20, 20, 12);
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < workouts.size(); i++) {
            final Workout workout = workouts.get(i);
            Button item = new Button(this);
            item.setText(workout.displayName());
            item.setAllCaps(false);
            item.setTextSize(18);
            item.setOnClickListener(v -> startWorkout(workout));
            item.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setMessage("Remove \"" + workout.displayName() + "\"?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (dialog, which) -> {
                            workouts.remove(workout);
                            saveWorkouts();
                            showWorkoutList();
                        })
                        .show();
                return true;
            });
            items.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        scroll.addView(items);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = button("+  Add");
        add.setTextSize(20);
        add.setOnClickListener(v -> showCreateWorkout());
        root.addView(add);
        setContentView(root);
    }

    private void showCreateWorkout() {
        root = createRoot();
        TextView title = text("New workout", 28, 0xFF222222);
        title.setPadding(20, 16, 20, 8);
        root.addView(title);
        EditText name = field("Name", InputType.TYPE_CLASS_TEXT);
        EditText preparation = field("Time before start (seconds)", InputType.TYPE_CLASS_NUMBER);
        EditText work = field("Working time (seconds)", InputType.TYPE_CLASS_NUMBER);
        EditText rest = field("Resting time (seconds)", InputType.TYPE_CLASS_NUMBER);
        EditText repeats = field("Repeats", InputType.TYPE_CLASS_NUMBER);
        name.setText("");
        preparation.setText(Integer.toString(lastValue("preparation", 8)));
        work.setText(Integer.toString(lastValue("work", 10)));
        rest.setText(Integer.toString(lastValue("rest", 120)));
        repeats.setText(Integer.toString(lastValue("repeats", 10)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(16, 0, 16, 0);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(name);
        fields.addView(preparation);
        fields.addView(work);
        fields.addView(rest);
        fields.addView(repeats);
        scroll.addView(fields);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Button accept = button("✓");
        accept.setTextSize(24);
        accept.setOnClickListener(v -> {
            try {
                int prepSeconds = parseValue(preparation, "Time before start", true);
                int workSeconds = parseValue(work, "Working time", false);
                int restSeconds = parseValue(rest, "Resting time", false);
                int repeatCount = parseValue(repeats, "Repeats", false);
                Workout workout = new Workout(name.getText().toString(),
                        prepSeconds, workSeconds, restSeconds, repeatCount);
                workouts.add(workout);
                saveWorkouts();
                getPreferences(MODE_PRIVATE).edit()
                        .putInt("preparation", prepSeconds)
                        .putInt("work", workSeconds)
                        .putInt("rest", restSeconds)
                        .putInt("repeats", repeatCount)
                        .apply();
                showWorkoutList();
            } catch (IllegalArgumentException exception) {
                new AlertDialog.Builder(this)
                        .setMessage(exception.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
        root.addView(accept);
        setContentView(root);
    }

    private void startWorkout(Workout workout) {
        activeWorkout = workout;
        timer = new WorkoutTimer(workout);
        paused = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showTimer();
        handler.removeCallbacks(timerTick);
        handler.postDelayed(timerTick, 1000);
    }

    private void showTimer() {
        root = createRoot();
        root.setGravity(Gravity.CENTER);
        countdown = text("", 96, 0xFFFFFFFF);
        countdown.setGravity(Gravity.CENTER);
        phaseLabel = text("", 24, 0xFFFFFFFF);
        phaseLabel.setGravity(Gravity.CENTER);
        periodsLeft = text("", 22, 0xFFFFFFFF);
        periodsLeft.setGravity(Gravity.CENTER);
        pauseControls = new LinearLayout(this);
        pauseControls.setGravity(Gravity.CENTER);

        Button back = button("◀");
        back.setOnClickListener(v -> {
            if (timer != null) {
                timer.skipBack();
                updateTimerDisplay();
            }
        });
        Button resume = button("▶");
        resume.setOnClickListener(v -> setPaused(false));
        Button forward = button("▶|");
        forward.setOnClickListener(v -> {
            if (timer != null) {
                timer.skipForward();
                updateTimerDisplay();
            }
        });
        Button end = button("End");
        end.setOnClickListener(v -> {
            stopTimer();
            showWorkoutList();
        });
        pauseControls.addView(back);
        pauseControls.addView(resume);
        pauseControls.addView(forward);
        pauseControls.addView(end);
        pauseControls.setVisibility(View.GONE);

        root.addView(phaseLabel);
        root.addView(countdown);
        root.addView(periodsLeft);
        root.addView(pauseControls);
        countdown.setOnClickListener(v -> setPaused(!paused));
        phaseLabel.setOnClickListener(v -> setPaused(!paused));
        periodsLeft.setOnClickListener(v -> setPaused(!paused));
        updateTimerDisplay();
        setContentView(root);
    }

    private void setPaused(boolean pause) {
        if (timer == null || timer.phase() == WorkoutTimer.Phase.FINISHED) {
            return;
        }
        paused = pause;
        pauseControls.setVisibility(paused ? View.VISIBLE : View.GONE);
        if (paused) {
            handler.removeCallbacks(timerTick);
        } else {
            handler.removeCallbacks(timerTick);
            handler.postDelayed(timerTick, 1000);
        }
    }

    private void updateTimerDisplay() {
        if (timer == null || countdown == null) {
            return;
        }
        int seconds = timer.secondsRemaining();
        String time = Workout.formatDuration(seconds);
        countdown.setText(time);
        countdown.setTextSize(time.length() <= 5 ? 96 : time.length() <= 7 ? 72 : 48);
        periodsLeft.setText("Work periods left: " + timer.workPeriodsLeft());
        switch (timer.phase()) {
            case PREPARATION:
                phaseLabel.setText("Get ready");
                root.setBackgroundColor(RED);
                break;
            case WORK:
                phaseLabel.setText("WORK");
                root.setBackgroundColor(GREEN);
                break;
            case REST:
                phaseLabel.setText("REST");
                root.setBackgroundColor(RED);
                break;
            case FINISHED:
                phaseLabel.setText("Workout complete");
                countdown.setText("00:00");
                root.setBackgroundColor(RED);
                pauseControls.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void stopTimer() {
        handler.removeCallbacks(timerTick);
        paused = true;
        timer = null;
        countdown = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void playBeep(boolean endOfInterval) {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, endOfInterval ? 250 : 100);
        }
    }

    private LinearLayout createRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFFFFFFFF);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private EditText field(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private int parseValue(EditText field, String label, boolean allowZero) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            if (value < (allowZero ? 0 : 1)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be " + (allowZero ? "zero or more" : "at least one"));
        }
    }

    private int lastValue(String key, int fallback) {
        return getPreferences(MODE_PRIVATE).getInt(key, fallback);
    }

    private void loadWorkouts() {
        String encoded = getPreferences(MODE_PRIVATE).getString("workouts", "[]");
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                workouts.add(new Workout(item.optString("name", ""),
                        item.getInt("preparation"), item.getInt("work"),
                        item.getInt("rest"), item.getInt("repeats")));
            }
        } catch (JSONException | IllegalArgumentException ignored) {
            workouts.clear();
        }
    }

    private void saveWorkouts() {
        JSONArray array = new JSONArray();
        for (Workout workout : workouts) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", workout.name);
                item.put("preparation", workout.preparationSeconds);
                item.put("work", workout.workSeconds);
                item.put("rest", workout.restSeconds);
                item.put("repeats", workout.repeats);
                array.put(item);
            } catch (JSONException ignored) {
                // Values written here are supported by JSONObject.
            }
        }
        getPreferences(MODE_PRIVATE).edit().putString("workouts", array.toString()).apply();
    }
}
