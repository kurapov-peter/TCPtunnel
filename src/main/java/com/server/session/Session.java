package com.server.session;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class Session implements BasicSession {
    private static final Logger logger = Logger.getLogger(Session.class.getName());
    private long time;
    private int bufferSize;
    private long timeout;
    private int port;
    private ByteBuffer buffer = null;


    private int getNearestPower2(int size) {
        return 1 << (32 - Integer.numberOfLeadingZeros(size - 1));
    }

    private void cleanUpBuffer() {
        buffer.clear();
        buffer.put(new byte[bufferSize]);
        buffer.clear();
    }

    private boolean isValidByTimeout() {
        return System.currentTimeMillis() - time < timeout;
    }

    private void updateTime() {
        time = System.currentTimeMillis();
    }

    private void reset(int bufferSize) {
        this.bufferSize = bufferSize;
        buffer = ByteBuffer.allocate(bufferSize);
        updateTime();
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public Session() {
        init(512, 2000, 5000);
    }

    public void init(int bufferSize, long timeout, int port) {
        assert (bufferSize > 0 && timeout > 0 && port > 0);

        setBufferSize(bufferSize);
        setTimeout(timeout);
        setPort(port);

        buffer = ByteBuffer.allocate(bufferSize);
        time = System.currentTimeMillis();
    }

    public boolean hasData() {
        return buffer.position() > 0;
    }

    public void write(ByteBuffer buffer) {
        assert (buffer.remaining() > 0);

        if (buffer.capacity() > this.buffer.capacity()) {
            reset(getNearestPower2(buffer.capacity()));

            logger.warning("Session received too much data to store.\n" +
                    "\tCurrent buffer size " + getBufferSize() + " bytes. Received " +
                    buffer.capacity() + ".\n\tReallocating buffer with size " + getNearestPower2(buffer.capacity()));
        }

        if (!isValidByTimeout()) {
            cleanUpBuffer();
            logger.info("Session buffer is flashed by timeout on writing operation.");
        }

        if (this.buffer.remaining() < buffer.remaining()) {
            cleanUpBuffer();
            logger.info("Session buffer has no space for incoming message.\nBuffer will be flashed.");
        }

        this.buffer.put((ByteBuffer) buffer.rewind());
        updateTime();
    }

    public void writeWithFlip(ByteBuffer buffer) {
        // DEBUG PURPOSE ONLY
//        System.out.println(toString());
        // END DEBUG
        write((ByteBuffer) buffer.flip());
        // DEBUG PURPOSE ONLY
//        System.out.println(toString());
        // END DEBUG
    }

    public String getBufferAsString() {
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < buffer.position(); i++) {
            res.append(Integer.toString((int)buffer.get(i) & 0xFF, 16));
            res.append(" ");
        }

        return res.toString().trim();
    }

    public void flashTo(ByteBuffer target) {
        // DEBUG PURPOSE ONLY
//        System.out.println(toString());
        // END DEBUG
        assert (target != null);
        assert (target.remaining() >= buffer.position());
        // If timeout clear buffer
        if (!this.isValidByTimeout()) {
            logger.info("Session buffer is flashed by timeout on reading operation.");
            cleanUpBuffer();
        }

        int position = buffer.position();
        buffer.rewind();

        while (buffer.position() < position) {
            target.put(buffer.get());
        }

        cleanUpBuffer();
        updateTime();
        // DEBUG PURPOSE ONLY
//        System.out.println(toString());
        // END DEBUG
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("buffer size:      " + bufferSize + "\n");
        builder.append("timeout:          " + timeout + "\n");
        builder.append("port:             " + port + "\n");
        builder.append("last time updated:" + time + "\n");
        builder.append("Buffer state:     "  + buffer.toString() + "\n");
        builder.append("Buffer data in HEX:\n");
        builder.append(getBufferAsString());
        return  "-----------------------\n" +
                getClass().getName() + "\n" +
                builder.toString() + "\n" +
                "-----------------------\n";
    }

}
