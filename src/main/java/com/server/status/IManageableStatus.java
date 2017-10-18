package com.server.status;

import java.time.LocalDateTime;
import java.util.Date;

public interface IManageableStatus extends IStatus {
    void setSent(int sent);
    void setRecv(int recv);
    void setConnected(boolean connected);
    void setLastTimeConnected(LocalDateTime lastTimeConnected);
}
