package com.example.intervaltimer;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;
    private static final int BLUE = 0xFF1565C0;
    private static final int BEEP_DURATION_MS = 100;
    private static final int FINAL_BEEP_DURATION_MS = 750;
    private final ArrayList<Workout> workouts = new ArrayList<>();
    private final Object timerLock = new Object();
    private final Object audioLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService timerExecutor =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerTask;
    private final Runnable timerTick = this::runTimerTick;

    private LinearLayout root;
    private WorkoutTimer timer;
    private Workout activeWorkout;
    private TextView countdown;
    private TextView phaseLabel;
    private TextView periodsLeft;
    private LinearLayout pauseControls;
    private boolean paused;
    private volatile boolean destroyed;
    private ToneGenerator toneGenerator;
    private AudioTrack silentAudio;
    private ScheduledFuture<?> audioStopTask;
    private int audioGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        loadWorkouts();
        showWorkoutList();
    }

    @Override
    protected void onDestroy() {
        synchronized (timerLock) {
            destroyed = true;
            timer = null;
            timerExecutor.shutdownNow();
        }
        mainHandler.removeCallbacksAndMessages(null);
        synchronized (audioLock) {
            stopAudio();
            if (silentAudio != null) {
                silentAudio.release();
                silentAudio = null;
            }
            if (toneGenerator != null) {
                toneGenerator.release();
                toneGenerator = null;
            }
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
        cancelTimerTask();
        synchronized (timerLock) {
            timer = null;
        }
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
        addLabeledField(fields, "Name", name);
        addLabeledField(fields, "Time before start (seconds)", preparation);
        addLabeledField(fields, "Working time (seconds)", work);
        addLabeledField(fields, "Resting time (seconds)", rest);
        addLabeledField(fields, "Repeats", repeats);
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
        synchronized (timerLock) {
            activeWorkout = workout;
            timer = new WorkoutTimer(workout);
            paused = false;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showTimer();
        scheduleTimerTask();
    }

    private void showTimer() {
        root = createRoot();
        root.setGravity(Gravity.CENTER);
        root.setOnClickListener(v -> setPaused(!paused));
        countdown = text("", 96, 0xFFFFFFFF);
        countdown.setGravity(Gravity.CENTER);
        countdown.setSingleLine(true);
        countdown.setHorizontallyScrolling(false);
        phaseLabel = text("", 24, 0xFFFFFFFF);
        phaseLabel.setGravity(Gravity.CENTER);
        periodsLeft = text("", 22, 0xFFFFFFFF);
        periodsLeft.setGravity(Gravity.CENTER);
        pauseControls = new LinearLayout(this);
        pauseControls.setGravity(Gravity.CENTER);

        Button back = button("◀");
        back.setOnClickListener(v -> {
            synchronized (timerLock) {
                if (timer != null) {
                    timer.skipBack();
                }
            }
            updateTimerDisplay();
        });
        Button resume = button("▶");
        resume.setOnClickListener(v -> setPaused(false));
        Button forward = button("▶|");
        forward.setOnClickListener(v -> {
            synchronized (timerLock) {
                if (timer != null) {
                    timer.skipForward();
                }
            }
            updateTimerDisplay();
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
        root.addView(countdown, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(periodsLeft);
        root.addView(pauseControls);
        countdown.addOnLayoutChangeListener((view, left, top, right, bottom,
                                             oldLeft, oldTop, oldRight, oldBottom) ->
                adjustCountdownTextSize());
        countdown.setOnClickListener(v -> setPaused(!paused));
        phaseLabel.setOnClickListener(v -> setPaused(!paused));
        periodsLeft.setOnClickListener(v -> setPaused(!paused));
        updateTimerDisplay();
        setContentView(root);
    }

    private void setPaused(boolean pause) {
        synchronized (timerLock) {
            if (timer == null || timer.phase() == WorkoutTimer.Phase.FINISHED) {
                return;
            }
            paused = pause;
        }
        pauseControls.setVisibility(paused ? View.VISIBLE : View.GONE);
        if (paused) {
            cancelTimerTask();
        } else {
            scheduleTimerTask();
        }
    }

    private void updateTimerDisplay() {
        WorkoutTimer.Phase phase;
        int seconds;
        int periodsRemaining;
        synchronized (timerLock) {
            if (timer == null || countdown == null) {
                return;
            }
            phase = timer.phase();
            seconds = timer.secondsRemaining();
            periodsRemaining = timer.workPeriodsLeft();
        }
        String time = Workout.formatDuration(seconds);
        countdown.setText(time);
        adjustCountdownTextSize();
        periodsLeft.setText("Work periods left: " + periodsRemaining);
        switch (phase) {
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
        cancelTimerTask();
        synchronized (timerLock) {
            paused = true;
            timer = null;
        }
        countdown = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void runTimerTick() {
        if (destroyed) {
            return;
        }
        boolean shouldBeep;
        boolean endOfInterval;
        boolean finished;
        synchronized (timerLock) {
            if (timer == null || paused) {
                return;
            }
            int before = timer.secondsRemaining();
            timer.tick();
            shouldBeep = before <= 3 && before > 0;
            endOfInterval = before == 1;
            finished = timer.phase() == WorkoutTimer.Phase.FINISHED;
            if (finished && timerTask != null) {
                timerTask.cancel(false);
                timerTask = null;
            }
            boolean keepAudioWarm = !finished && timer.secondsRemaining() <= 4;
            if (keepAudioWarm) {
                prepareAudio();
            }
            if (shouldBeep) {
                playBeep(endOfInterval, keepAudioWarm);
            }
        }
        mainHandler.post(() -> {
            if (destroyed) {
                return;
            }
            updateTimerDisplay();
            if (finished) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void scheduleTimerTask() {
        synchronized (timerLock) {
            if (timerTask != null) {
                timerTask.cancel(false);
            }
            if (timer != null && timer.phase() != WorkoutTimer.Phase.FINISHED
                    && timer.secondsRemaining() <= 4) {
                prepareAudio();
            }
            timerTask = timerExecutor.scheduleAtFixedRate(timerTick, 1, 1, TimeUnit.SECONDS);
        }
    }

    private void cancelTimerTask() {
        synchronized (timerLock) {
            if (timerTask != null) {
                timerTask.cancel(false);
                timerTask = null;
            }
            stopAudio();
        }
    }

    private void adjustCountdownTextSize() {
        if (countdown == null || countdown.getWidth() == 0 || countdown.getHeight() == 0) {
            return;
        }
        float currentSize = countdown.getTextSize();
        float textWidth = countdown.getPaint().measureText(countdown.getText().toString());
        android.graphics.Paint.FontMetrics metrics = countdown.getPaint().getFontMetrics();
        float textHeight = metrics.bottom - metrics.top;
        float availableWidth = countdown.getWidth() - countdown.getPaddingLeft()
                - countdown.getPaddingRight() - 2;
        float availableHeight = countdown.getHeight() - countdown.getPaddingTop()
                - countdown.getPaddingBottom() - 2;
        if (textWidth > 0 && textHeight > 0 && availableWidth > 0 && availableHeight > 0) {
            float fittingSize = (float) Math.floor(Math.min(
                    currentSize * availableWidth / textWidth,
                    currentSize * availableHeight / textHeight));
            countdown.setTextSize(TypedValue.COMPLEX_UNIT_PX, fittingSize);
        }
    }

    private void prepareAudio() {
        synchronized (audioLock) {
            audioGeneration++;
            if (audioStopTask != null) {
                audioStopTask.cancel(false);
                audioStopTask = null;
            }
            if (silentAudio == null) {
                int sampleRate = 8000;
                short[] silence = new short[sampleRate];
                silentAudio = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(silence.length * 2)
                        .build();
                if (silentAudio.write(silence, 0, silence.length) != silence.length
                        || silentAudio.setLoopPoints(0, silence.length, -1) != AudioTrack.SUCCESS) {
                    throw new IllegalStateException("Could not initialize headset warm-up audio");
                }
            }
            if (silentAudio.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                silentAudio.play();
            }
        }
    }

    private void playBeep(boolean endOfInterval, boolean keepAudioWarm) {
        synchronized (audioLock) {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP,
                        endOfInterval ? FINAL_BEEP_DURATION_MS : BEEP_DURATION_MS);
                if (endOfInterval && !keepAudioWarm) {
                    int generation = audioGeneration;
                    audioStopTask = timerExecutor.schedule(() -> {
                        synchronized (audioLock) {
                            if (generation == audioGeneration) {
                                audioStopTask = null;
                                stopAudio();
                            }
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    private void stopAudio() {
        synchronized (audioLock) {
            audioGeneration++;
            if (audioStopTask != null) {
                audioStopTask.cancel(false);
                audioStopTask = null;
            }
            if (toneGenerator != null) {
                toneGenerator.stopTone();
            }
            if (silentAudio != null
                    && silentAudio.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                silentAudio.stop();
            }
        }
    }

    private LinearLayout createRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFFFFFFFF);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(systemBars.left, systemBars.top,
                        systemBars.right, systemBars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        return layout;
    }

    private void addLabeledField(LinearLayout container, String labelText, EditText input) {
        TextView label = text(labelText, 16, 0xFF222222);
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        container.addView(label);
        container.addView(input);
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
