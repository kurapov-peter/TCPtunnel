package com.server.session;

import java.nio.ByteBuffer;

public interface BasicSession {
    void init(int bufferSize, long timeout, int port);
    boolean hasData();
    void writeWithFlip(ByteBuffer buffer);
    String getBufferAsString();
    void flashTo(ByteBuffer target);
    int getPort();
}
