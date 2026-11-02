package com.example.intervaltimer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkoutTimerTest {
    @Test
    public void completesWorkAndRestWithoutAddingFinalRest() {
        WorkoutTimer timer = new WorkoutTimer(new Workout("", 0, 2, 5, 2));

        timer.tick();
        assertEquals(1, timer.secondsRemaining());
        timer.tick();
        assertEquals(WorkoutTimer.Phase.REST, timer.phase());
        assertEquals(1, timer.workPeriodsLeft());

        for (int i = 0; i < 5; i++) {
            timer.tick();
        }
        assertEquals(WorkoutTimer.Phase.WORK, timer.phase());
        timer.tick();
        timer.tick();

        assertEquals(WorkoutTimer.Phase.FINISHED, timer.phase());
        assertEquals(0, timer.secondsRemaining());
        assertEquals(0, timer.workPeriodsLeft());
    }

    @Test
    public void preparationLeadsIntoWorkAndSkipBackReturnsToPreparation() {
        WorkoutTimer timer = new WorkoutTimer(new Workout("", 8, 10, 120, 1));

        assertEquals(WorkoutTimer.Phase.PREPARATION, timer.phase());
        timer.skipForward();
        assertEquals(WorkoutTimer.Phase.WORK, timer.phase());
        timer.skipBack();

        assertEquals(WorkoutTimer.Phase.PREPARATION, timer.phase());
        assertEquals(8, timer.secondsRemaining());
    }

    @Test
    public void unnamedWorkoutUsesConfiguredValues() {
        Workout workout = new Workout(" ", 8, 10, 120, 10);

        assertEquals("10 x 0:10 work, 2:00 rest", workout.displayName());
    }
}
