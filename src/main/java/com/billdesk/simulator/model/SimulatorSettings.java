package com.billdesk.simulator.model;


public class SimulatorSettings {

    private SimulatorOutcome defaultOutcome = SimulatorOutcome.SUCCESS;

    private int callbackDelaySeconds = 0;

    private boolean dropCallback = false;

    private boolean duplicateCallback = false;

    private int pendingCheckerDelaySeconds = 10;

    private SimulatorOutcome pendingFinalOutcome = SimulatorOutcome.SUCCESS;

    public SimulatorOutcome getDefaultOutcome() {
        return defaultOutcome;
    }
    public void setDefaultOutcome(SimulatorOutcome defaultOutcome) {
        this.defaultOutcome = defaultOutcome;
    }

    public int getCallbackDelaySeconds() {
        return callbackDelaySeconds;
    }
    public void setCallbackDelaySeconds(int callbackDelaySeconds) {
        this.callbackDelaySeconds = callbackDelaySeconds;
    }

    public boolean isDropCallback() {
        return dropCallback;
    }
    public void setDropCallback(boolean dropCallback) {
        this.dropCallback = dropCallback;
    }

    public boolean isDuplicateCallback() {
        return duplicateCallback;
    }
    public void setDuplicateCallback(boolean duplicateCallback) {
        this.duplicateCallback = duplicateCallback;
    }

    public int getPendingCheckerDelaySeconds() {
        return pendingCheckerDelaySeconds;
    }
    public void setPendingCheckerDelaySeconds(int pendingCheckerDelaySeconds) {
        this.pendingCheckerDelaySeconds = pendingCheckerDelaySeconds;
    }

    public SimulatorOutcome getPendingFinalOutcome() {
        return pendingFinalOutcome;
    }
    public void setPendingFinalOutcome(SimulatorOutcome pendingFinalOutcome) {
        this.pendingFinalOutcome = pendingFinalOutcome;
    }
}
