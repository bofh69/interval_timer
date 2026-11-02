package com.example.intervaltimer;

public final class WorkoutTimer {
    public enum Phase {
        PREPARATION, WORK, REST, FINISHED
    }

    private final Workout workout;
    private Phase phase;
    private int secondsRemaining;
    private int workPeriodsLeft;

    public WorkoutTimer(Workout workout) {
        this.workout = workout;
        workPeriodsLeft = workout.repeats;
        if (workout.preparationSeconds > 0) {
            phase = Phase.PREPARATION;
            secondsRemaining = workout.preparationSeconds;
        } else {
            phase = Phase.WORK;
            secondsRemaining = workout.workSeconds;
        }
    }

    public Phase phase() {
        return phase;
    }

    public int secondsRemaining() {
        return secondsRemaining;
    }

    public int workPeriodsLeft() {
        return workPeriodsLeft;
    }

    public void tick() {
        if (phase == Phase.FINISHED) {
            return;
        }
        if (secondsRemaining > 1) {
            secondsRemaining--;
        } else {
            advance();
        }
    }

    public void skipForward() {
        if (phase != Phase.FINISHED) {
            advance();
        }
    }

    public void skipBack() {
        switch (phase) {
            case PREPARATION:
                secondsRemaining = workout.preparationSeconds;
                break;
            case WORK:
                if (workPeriodsLeft < workout.repeats) {
                    phase = Phase.REST;
                    secondsRemaining = workout.restSeconds;
                } else if (workout.preparationSeconds > 0) {
                    phase = Phase.PREPARATION;
                    secondsRemaining = workout.preparationSeconds;
                } else {
                    secondsRemaining = workout.workSeconds;
                }
                break;
            case REST:
                phase = Phase.WORK;
                workPeriodsLeft = Math.min(workout.repeats, workPeriodsLeft + 1);
                secondsRemaining = workout.workSeconds;
                break;
            case FINISHED:
                workPeriodsLeft = 1;
                phase = Phase.WORK;
                secondsRemaining = workout.workSeconds;
                break;
        }
    }

    private void advance() {
        switch (phase) {
            case PREPARATION:
                phase = Phase.WORK;
                secondsRemaining = workout.workSeconds;
                break;
            case WORK:
                workPeriodsLeft--;
                if (workPeriodsLeft == 0) {
                    phase = Phase.FINISHED;
                    secondsRemaining = 0;
                } else {
                    phase = Phase.REST;
                    secondsRemaining = workout.restSeconds;
                }
                break;
            case REST:
                phase = Phase.WORK;
                secondsRemaining = workout.workSeconds;
                break;
            case FINISHED:
                break;
        }
    }
}
