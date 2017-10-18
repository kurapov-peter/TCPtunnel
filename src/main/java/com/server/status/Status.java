package com.server.status;

import java.time.LocalDateTime;
import java.util.Map;

public class Status implements IStatus, IManageableStatus {
    private final Map.Entry<Integer, Integer> rule;
    private int sent;
    private int recv;
    private boolean connected;
    private LocalDateTime lastTimeConnected;

    public Status(Map.Entry<Integer, Integer> rule) {
        this.rule = rule;
    }

    public int getSent() {
        return sent;
    }

    public void setSent(int sent) {
        this.sent = sent;
    }

    public int getRecv() {
        return recv;
    }

    public void setRecv(int recv) {
        this.recv = recv;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public LocalDateTime getLastTimeConnected() {
        return lastTimeConnected;
    }

    public void setLastTimeConnected(LocalDateTime lastTimeConnected) {
        this.lastTimeConnected = lastTimeConnected;
    }

    public Map.Entry<Integer, Integer> getRule() {
        return rule;
    }
}
