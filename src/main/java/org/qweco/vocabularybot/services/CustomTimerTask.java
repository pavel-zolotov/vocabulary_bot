package org.qweco.vocabularybot.services;

public abstract class CustomTimerTask {
    private String taskName = "";
    private int times = 1;

    /**
     * Constructor
     *
     * @param taskName Name of the task
     */
    public CustomTimerTask(String taskName, int times) {
        this.taskName = taskName;
        this.times = times;
    }

    /**
     * Get name
     *
     * @return name
     */
    public String getTaskName() {
        return this.taskName;
    }

    /**
     * Set name
     *
     * @param taskName new name
     */
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    /**
     * Getter for the times
     *
     * @return Remaining times the task must be executed
     */
    public int getTimes() {
        return this.times;
    }

    /**
     * Setter for the times
     *
     * @param times Number of times the task must be executed
     */
    public void setTimes(int times) {
        this.times = times;
    }

    public void reduceTimes() {
        if (this.times > 0) {
            this.times -= 1;
        }
    }

    /**
     * Should contain the functionality of the task
     */
    public abstract void execute();
}
