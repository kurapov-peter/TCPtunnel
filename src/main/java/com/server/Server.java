package com.server;

import com.server.portpool.BasicPortPool;
import com.server.session.BasicSession;
import com.server.session.Session;
import com.server.status.IManageableStatus;
import com.server.status.IStatus;
import com.server.status.Status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server implements Runnable, IServer {

    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private int bufferSize;
    private long timeout;
    private BasicPortPool pool;
    private Selector selector;
    private ByteBuffer buffer;


    // Channels storage
    private Map<Integer, ServerSocketChannel> serverSocketChannels = new HashMap<>();
    private Map<Integer, SocketChannel> socketChannels = new HashMap<>();

    // Status storage
    private Map<Integer, IManageableStatus> statuses = new HashMap<>();
    private Map<Integer, Boolean> connected = new HashMap<>();


    private void log(Level level, String message, int port) {
        logger.log(level, "{0}", new Object[]{message, port});
    }

    private void initSelector() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
//            logger.log(Level.SEVERE, "Unable to open selector: " + e.getMessage());
//            log(Level.SEVERE, "Unable to open selector: " + e.getMessage(), -1);
            logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to open selector: " + e.getMessage(), -1});
        }
    }

    @Override
    public void start() {
        run();
    }

    public void shutdown() {
        try {
            selector.close();
        } catch (IOException e) {
//            logger.log(Level.SEVERE, "Unable to close selector: " + e.getMessage());
//            log(Level.SEVERE, "Unable to close selector: " + e.getMessage(), -1);
            logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to close selector: " + e.getMessage(), -1});
        }
    }

    private void clearBuffer() {
        buffer.clear();
        buffer.put(new byte[bufferSize]);
        buffer.clear();
    }

    public void init(int bufferSize, long timeout, BasicPortPool pool) {
        this.bufferSize = bufferSize;
        this.timeout = timeout;
        this.pool = pool;
        this.buffer = ByteBuffer.allocate(bufferSize);
        serverSocketChannels = new HashMap<>();
        socketChannels = new HashMap<>();
    }

    public long getTimeout() {
        return timeout;
    }

    int getBufferSize() {
        return bufferSize;
    }

    void registerChannels(Selector selector) {
        assert (selector.isOpen());
        // TODO: if port busy add logic for skipping data translation

        for (int port : pool.getPorts()) {
            ServerSocketChannel serverSocketChannel;

            try {
                serverSocketChannel = ServerSocketChannel.open();
            } catch (IOException e) {
//                logger.warning("Unable to open server socket on port " + port +
//                        ". Open method failed with message:\n" + e.getMessage());
//                log(Level.WARNING, "Unable to open server socket on port " + port +
//                        ". Open method failed with message:\n" + e.getMessage(), port);
                logger.log(Level.WARNING, "{0}", new Object[]{"Unable to open server socket on port " + port +
                        ". Open method failed with message:\n" + e.getMessage(), port});
                continue;
            }

            try {
                if (serverSocketChannel != null) {
                    serverSocketChannel.configureBlocking(false);
                } else {
//                    logger.warning("Server socket channel is not opened and cannot be configured.");
//                    log(Level.WARNING, "Server socket channel is not opened and cannot be configured.", port);
                    logger.log(Level.WARNING, "{0}", new Object[]{"Server socket channel is not opened and cannot be configured.", port});
                    continue;
                }
            } catch (IOException e) {
//                logger.log(Level.SEVERE, "Unable to configure server socket channel to non-blocking mode for " +
//                        "port " + port + ". " + e.getMessage());
//                log(Level.SEVERE, "Unable to configure server socket channel to non-blocking mode for " +
//                        "port " + port + ". " + e.getMessage(), port);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to configure server socket channel to non-blocking mode for " +
                        "port " + port + ". " + e.getMessage(), port});
                continue;
            }

            try {
                serverSocketChannel.socket().bind(new InetSocketAddress(port));
            } catch (IOException e) {
//                logger.log(Level.SEVERE, "Can not bind server socket channel to port " +
//                        port + ". Port busy. " + e.getMessage());
//                log(Level.SEVERE, "Can not bind server socket channel to port " +
//                        port + ". Port busy. " + e.getMessage(), port);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Can not bind server socket channel to port " +
                        port + ". Port busy. " + e.getMessage(), port});
                continue;
            }

            // Create new session
            BasicSession session = new Session();
            session.init(bufferSize, timeout, port);

            try {
                // Register accept event with generated session
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, session);
            } catch (ClosedChannelException e) {
//                logger.log(Level.SEVERE, "Unable to register ACCEPT event for port " +
//                        port + ". " + e.getMessage());
//                log(Level.SEVERE, "Unable to register ACCEPT event for port " +
//                        port + ". " + e.getMessage(), port);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to register ACCEPT event for port " +
                        port + ". " + e.getMessage(), port});
                continue;
            }

            // Store registered server socket channels
            serverSocketChannels.put(port, serverSocketChannel);

            // Store connected status for port
            connected.put(port, false);
        }

        initializeStatuses();
    }

    private void initializeStatuses() {
        for (Map.Entry<Integer, Integer> entry : pool.getRules().entrySet()) {
            IManageableStatus status = new Status(entry);
            statuses.put(entry.getKey(), status);
        }
    }

    void deregisterChannels() throws IOException {
        for (Map.Entry<Integer, ServerSocketChannel> entry : serverSocketChannels.entrySet()) {
            entry.getValue().close();
        }

        for (Map.Entry<Integer, SocketChannel> entry : socketChannels.entrySet()) {
            entry.getValue().close();
        }
    }

    private void updateStatusConnection(int port, boolean status) {
        assert connected.containsKey(port);

//        logger.info("Updating status for port " + port);
//        log(Level.INFO, "Updating status for port " + port, port);
        logger.log(Level.INFO, "{0}", new Object[]{"Updating status for port " + port, port});

        connected.put(port, status);

        int dest = pool.getRule(port);

        IManageableStatus currentStatus;
        if (statuses.containsKey(port)) {
            currentStatus = statuses.get(port);
        } else {
            currentStatus = statuses.get(dest);
        }


        if (status && connected.get(dest)) {
//            logger.info("Connection between ports " + port + " " + dest + " established.");
//            log(Level.INFO, "Connection between ports " + port + " " + dest + " established.", port);
            logger.log(Level.INFO, "{0}", new Object[]{"Connection between ports " + port + " " + dest + " established.", port});
            currentStatus.setLastTimeConnected(LocalDateTime.now());
            currentStatus.setConnected(true);
        } else {
            currentStatus.setConnected(false);
        }

    }

    private void updateStatusBytes(int port, int bytes) {
        int dest = pool.getRule(port);

        if (statuses.containsKey(port)) {
            IManageableStatus status = statuses.get(port);
            status.setSent(status.getSent() + bytes);
        } else {
            IManageableStatus status = statuses.get(dest);
            status.setRecv(status.getRecv() + bytes);
        }
    }

    SelectionKey handleAcceptEvent(Selector selector, SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept incoming connection
        SocketChannel socketChannel;
        try {
            socketChannel = serverSocketChannel.accept();
        } catch (IOException e) {
//            logger.log(Level.SEVERE, "Unable to accept connection for key " +
//                    key + " with msg: " + e.getMessage());
//            log(Level.SEVERE, "Unable to accept connection for key " +
//                    key + " with msg: " + e.getMessage(), -1);
            logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to accept connection for key " +
                    key + " with msg: " + e.getMessage(), -1});
            return null;
        }

        // Setup new socket channel
        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
//            logger.log(Level.SEVERE, "Unable to configure socket channel to non-blocking mode: "
//                    + e.getMessage());
            log(Level.SEVERE, "Unable to configure socket channel to non-blocking mode: "
                    + e.getMessage(), socketChannel.socket().getLocalPort());
            logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to configure socket channel to non-blocking mode: "
                    + e.getMessage(), socketChannel.socket().getLocalPort()});
            return null;
        }

        BasicSession session = (BasicSession) key.attachment();
//        logger.info("Accepting connection for port " + session.getPort() + ".");
//        log(Level.INFO, "Accepting connection for port " + session.getPort() + ".", session.getPort());
        logger.log(Level.INFO, "{0}", new Object[]{"Accepting connection for port " + session.getPort() + ".", session.getPort()});

        // Check if there's data to process
        try {
            if (session.hasData()) {
                socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, session);
//                logger.info("Socket state for port " + session.getPort() +
//                        " changed to listening READ | WRITE events (as current session has data to process).");
//                log(Level.INFO, "Socket state for port " + session.getPort() +
//                        " changed to listening READ | WRITE events (as current session has data to process).",
//                        session.getPort());
                logger.log(Level.INFO, "{0}", new Object[]{"Socket state for port " + session.getPort() +
                        " changed to listening READ | WRITE events (as current session has data to process).", session.getPort()});
            } else {
                socketChannel.register(selector, SelectionKey.OP_READ, session);
//                logger.info("Socket state for port " + session.getPort() + " changed to listening READ event.");
//                log(Level.INFO, "Socket state for port " + session.getPort() +
//                        " changed to listening READ event.", session.getPort());
                logger.log(Level.INFO, "{0}", new Object[]{"Socket state for port " + session.getPort() +
                        " changed to listening READ event.", session.getPort()});
            }
        } catch (ClosedChannelException e) {
//            logger.log(Level.SEVERE, "Unable to register a read event: " + e.getMessage());
//            log(Level.SEVERE, "Unable to register a read event: " + e.getMessage(), session.getPort());
            logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to register a read event: " + e.getMessage(), session.getPort()});
            return null;
        }

        // Update socket storage
        socketChannels.put(session.getPort(), socketChannel);

        // TODO: prototype failed to read first byte of message. It's possible that here you have to look for data

        updateStatusConnection(session.getPort(), true);

        return socketChannel.keyFor(selector);
    }


    private SelectionKey handleReadEvent(Selector selector, SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Get source and destination ports
        int source = socketChannel.socket().getLocalPort();
        int dest = pool.getRule(source);

//        logger.info("Socket on port " + source + " ready for incoming data.");
//        log(Level.INFO, "Socket on port " + source + " ready for incoming data.", source);
        logger.log(Level.INFO, "{0}", new Object[]{"Socket on port " + source + " ready for incoming data.", source});

        try {
            socketChannel.read(buffer);
        } catch (IOException e) {
//            logger.log(Level.SEVERE, "Unable to read from socket channel.");
//            log(Level.SEVERE, "Unable to read from socket channel.", source);
            logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to read from socket channel.", source});
        }

        if (buffer.position() > 0) {
            assert (serverSocketChannels.containsKey(source));

            // Get destination channel session
            ServerSocketChannel destServerSocketChannel = serverSocketChannels.get(dest);
            SelectionKey destKey = destServerSocketChannel.keyFor(selector);
            BasicSession session = (BasicSession) destKey.attachment();

//            logger.info("Reading data for port pair: " + source + " -> " + dest);
//            log(Level.INFO, "Reading data for port pair: " + source + " -> " + dest, source);
            logger.log(Level.INFO, "{0}", new Object[]{"Reading data for port pair: " + source + " -> " + dest, source});

            // Store data into target session
            session.writeWithFlip(buffer);

//            logger.info("Data received: " + session.getBufferAsString());
//            log(Level.INFO, "Data received: " + session.getBufferAsString(), source);
            logger.log(Level.INFO, "{0}", new Object[]{"Data received: " + session.getBufferAsString(), source});

            // If other side is ready to read then propose data for it
            System.out.println("now I want to propose my ("+ source + ") data to destination(" + dest + ").");
            System.out.println("socket channels has to contain key : " + socketChannels.containsKey(dest));

            if (socketChannels.containsKey(dest)) {
                SocketChannel destChannel = socketChannels.get(dest);

                if (destChannel == null) {
//                    logger.info("No destination channel");
//                    log(Level.INFO, "No destination channel", source);
                    logger.log(Level.INFO, "{0}", new Object[]{"No destination channel", source});
                    return key;
                }

//                logger.info("Destination port " + destChannel.socket().getLocalPort() + " ready for data.");
//                log(Level.INFO, "Destination port " + destChannel.socket().getLocalPort() +
//                        " ready for data.", source);
                logger.log(Level.INFO, "{0}", new Object[]{"Destination port " + destChannel.socket().getLocalPort() +
                        " ready for data.", source});

                try {
                    destChannel.register(selector, SelectionKey.OP_WRITE, destKey.attachment());
                } catch (ClosedChannelException e) {
//                    logger.log(Level.SEVERE, "Unable to register write event. " + e.getMessage());
//                    log(Level.SEVERE, "Unable to register write event. " + e.getMessage(), source);
                    logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to register write event. " + e.getMessage(), source});
                }

                // Update number of bytes transferred
                updateStatusBytes(source, buffer.position());
            }
        } else {
            // No data received. Connection lost(?). Set key to listen accept event
//            logger.info("Connection lost for port " + source + ". Channel will be listening to accept event");
//            log(Level.INFO, "Connection lost for port " + source +
//                    ". Channel will be listening to accept event", source);
            logger.log(Level.INFO, "{0}", new Object[]{"Connection lost for port " + source +
                    ". Channel will be listening to accept event", source});

            // Reset socket storage
            assert (serverSocketChannels.containsKey(source));
            ServerSocketChannel serverSocketChannel = serverSocketChannels.get(source);

//            socketChannels.remove(dest); //???
            // Close current connection
            try {
                socketChannel.socket().close();
            } catch (IOException e) {
//                logger.log(Level.SEVERE, "Unable to close current connection. " + e.getMessage());
//                log(Level.SEVERE,"Unable to close current connection. " + e.getMessage(), source);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to close current connection. " + e.getMessage(), source});
            }

            // Reset server socket session
            try {
                serverSocketChannel.configureBlocking(false);
            } catch (IOException e) {
//                logger.log(Level.SEVERE, "Unable to configure server socket channel to non-blocking mode " +
//                        e.getMessage());
//                log(Level.SEVERE, "Unable to configure server socket channel to non-blocking mode " +
//                        e.getMessage(), source);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to configure server socket channel to non-blocking mode " +
                        e.getMessage(), source});
            }

            BasicSession session = new Session();
            session.init(bufferSize, timeout, source);

            // Reset channel to wait for new connections
            try {
                serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, session);
            } catch (ClosedChannelException e) {
//                logger.log(Level.SEVERE, "Unable to re-register server socket channel on connection lost: " +
//                        e.getMessage());
//                log(Level.SEVERE, "Unable to re-register server socket channel on connection lost: " +
//                        e.getMessage(), source);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to re-register server socket channel on connection lost: " +
                        e.getMessage(), source});
            }

            // Clear buffer as the data is no longer used
            clearBuffer();

            // Update status on connection lost
            updateStatusConnection(source, false);

            return null;
        }

        // Clear buffer as the data is no longer used
        clearBuffer();

        return socketChannel.keyFor(selector);
    }


    SelectionKey handleWriteEvent(Selector selector, SelectionKey key) {
//        logger.info("Key is writable.");

        SocketChannel socketChannel = (SocketChannel) key.channel();
        BasicSession session = (BasicSession) key.attachment();

        if (session.hasData()) {
//            logger.info("Sending data to socket on port "+ socketChannel.socket().getLocalPort()
//                    + ": " + session.getBufferAsString());
//            log(Level.INFO, "Sending data to socket on port "+ socketChannel.socket().getLocalPort()
//                    + ": " + session.getBufferAsString(), socketChannel.socket().getLocalPort());
            logger.log(Level.INFO, "{0}", new Object[]{"Sending data to socket on port "+ socketChannel.socket().getLocalPort()
                    + ": " + session.getBufferAsString(), socketChannel.socket().getLocalPort()});

            // Dump data into internal server buffer
            session.flashTo(buffer);

            try {
                // Write buffer to socket
                ByteBuffer bf = (ByteBuffer) buffer.flip();
                socketChannel.write(bf);

//                logger.info("Data sent.");
//                log(Level.INFO, "Data sent.", socketChannel.socket().getLocalPort());
                logger.log(Level.INFO, "{0}", new Object[]{"Data sent.", socketChannel.socket().getLocalPort()});
            } catch (IOException e) {
//                logger.log(Level.SEVERE, "Unable to write data to socket: " + e.getMessage());
//                log(Level.SEVERE, "Unable to write data to socket: " + e.getMessage(),
//                        socketChannel.socket().getLocalPort());
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to write data to socket: " + e.getMessage(),
                        socketChannel.socket().getLocalPort()});
                return key;
            }

        }

        // Clear buffer as the data is no longer used
        clearBuffer();

        // Cancel write event and wait for more data
        key.interestOps(SelectionKey.OP_READ);

        return socketChannel.keyFor(selector);
    }

    public void run() {
        initSelector();
        registerChannels(selector);

//        logger.info("Server is up.");
//        log(Level.INFO, "Server is up.", -1);
        logger.log(Level.INFO, "{0}", new Object[]{"Server is up.", -1});

        while (selector.isOpen()) {
            if (Thread.currentThread().isInterrupted()) {
                stop();
//                logger.info("Server is stopped.");
//                log(Level.INFO, "Server is stopped.", -1);
                logger.log(Level.INFO, "{0}", new Object[]{"Server is stopped.", -1});
                return;
            }

            try {
                selector.select();
            } catch (IOException e) {
//                logger.log(Level.SEVERE, "Unable to select: " + e.getMessage());
//                log(Level.SEVERE, "Unable to select: " + e.getMessage(), -1);
                logger.log(Level.SEVERE, "{0}", new Object[]{"Unable to select: " + e.getMessage(), -1});
            }


            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {
                    handleAcceptEvent(selector, key);
                }

                if (key.isReadable()) {
                    SelectionKey test = handleReadEvent(selector, key);
                    if (test == null) {
                        System.out.println("Lost connection.");
                        iterator.remove();
                        continue;
                    }
                }

                if (key.isValid() && key.isWritable()) {
                    handleWriteEvent(selector, key);
                }

                iterator.remove();
            }
        }


    }

    public void stop() {
        try {
            deregisterChannels();
        } catch (IOException e) {
            e.printStackTrace();
        }

        shutdown();
    }

    public IStatus getStatus(int port) {
        assert statuses.containsKey(port);
        return statuses.get(port);
    }

}
