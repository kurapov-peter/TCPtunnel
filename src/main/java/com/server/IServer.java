package com.server;

import com.server.status.IStatus;

public interface IServer {
    void start();
    void shutdown();
    IStatus getStatus(int port);
}
