package com.server.status;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

public interface IStatus {
    int getSent();
    int getRecv();
    boolean isConnected();
    LocalDateTime getLastTimeConnected();
    Map.Entry<Integer, Integer> getRule();
}
