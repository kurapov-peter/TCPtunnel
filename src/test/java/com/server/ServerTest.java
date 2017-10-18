package com.server;

import com.server.portpool.ManageablePortPool;
import com.server.portpool.PortPool;
import com.server.session.BasicSession;
import com.server.session.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;


class SocketListener extends Thread {

    final Socket socket;
    String data;

    SocketListener(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run() {
        synchronized (this) {
            // Check if data is on socket
            BufferedReader in = null;

            try {
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                assert in != null;

                data = in.readLine();
                System.out.println(data);
                notify();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    String getData() {
        return data;
    }
}

class BufferedSocketListener extends SocketListener {
    BufferedSocketListener(Socket socket) {
        super(socket);
        try {
            socket.setSoTimeout(100);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        BufferedReader in = null;

        try {
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        String line;
        try {
            assert in != null;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
                System.out.println("Appending line: " + line);
            }
        } catch (SocketTimeoutException e) {
            System.out.println("No more data available after timeout.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.data = sb.toString();

    }
}

class SocketWriter extends Thread {

    private final Socket socket;
    private final String data;
    private final long waitFor;

    SocketWriter(Socket socket, String data, long waitForMillis) {
        this.socket = socket;
        this.data = data;
        this.waitFor = waitForMillis;
    }

    @Override
    public void run() {
        PrintWriter out;

        try {
            Thread.sleep(waitFor);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
             out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // write data to socket
        out.println(data);
        System.out.println("Written \"" + data + "\" to socket.");
    }
}

public class ServerTest {
    private Server server;
    private ManageablePortPool pool;
    private Selector selector;
    private ArrayList<ServerSocketChannel> serverSockets = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        server = new Server();
        pool = new PortPool();
    }

    private void closeOpenedServerSockets() {
        for (ServerSocketChannel serverSocket : serverSockets) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        serverSockets.clear();
    }

    private void registerAcceptListenerForPort(int port, BasicSession session) throws Exception {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        if (session == null) {
            session = new Session();
            session.init(4096, 2000, port);
        }

        serverSocketChannel.socket().bind(new InetSocketAddress(port));

        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, session);
        serverSockets.add(serverSocketChannel);
    }

    @Test
    public void whenInitializingThenCorrectValuesAreSet() throws Exception {
        server.init(4096, 2000, pool);
        assertEquals(4096, server.getBufferSize());
        assertEquals(2000, server.getTimeout());
    }

    @Test
    public void whenOpenConnectionsThenServerSocketsForRulesPortsAreAccessible() throws Exception {
        pool.addRule(5000, 5001);
        server.init(4096, 2000, pool);

        Selector selector = Selector.open();
        server.registerChannels(selector);

        Socket socket = null;
        Socket socket2 = null;
        Exception ex = null;
        try {
            socket = new Socket("localhost", 5000);
            socket2 = new Socket("localhost", 5001);
        } catch (Exception e) {
            ex = e;

            selector.close();
        }

        assertEquals(null, ex);
        if (socket != null) {
            socket.close();
        }
        if (socket2 != null) {
            socket2.close();
        }
        server.deregisterChannels();
        selector.close();
    }

    @Test
    public void whenHandlingAcceptEventThenReadEventIsRegistered() throws Exception {
        selector = Selector.open();
        // Register a listener for accept event
        registerAcceptListenerForPort(5001, null);

        // Connect to opened server socket
        Socket socket = new Socket("localhost", 5001);

        // Select the event
        selector.select();

        Set<SelectionKey> readyKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = readyKeys.iterator();
        SelectionKey readEventListenerKey = null;

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();

            if (key.isAcceptable()) {
                readEventListenerKey = server.handleAcceptEvent(selector, key);
            }

            iterator.remove();
        }

        // Check if selector contains read event listener
        assert readEventListenerKey != null;
        assertTrue(readEventListenerKey.interestOps() == SelectionKey.OP_READ);

        socket.close();
        closeOpenedServerSockets();
        selector.close();
    }

    // TODO: This test had an error (checking wrong session for data presence).
    // TODO: It's may be possible to write a case for this, but server class does not have API for
    // TODO: getting destination key.
//    @Test
//    public void whenHandlingReadEventThenCorrectDataIsStoredInDestinationSession() throws Exception {
//        selector = Selector.open();
//        pool.addRule(5000, 5001);
//        server.init(4096, 2000, pool);
//        server.registerChannels(selector);
//
//        // Connect to opened server sockets
//        Socket socket = new Socket("localhost", 5000);
//
//        // Write test data to socket
//        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//        out.println("test");
//
//        SelectionKey readEventListenerKey = null;
//        SelectionKey myKey = null;
//
//        boolean stopped = false;
//        while (!stopped) {
//            selector.select();
//
//            Set<SelectionKey> readyKeys = selector.selectedKeys();
//            Iterator<SelectionKey> iterator = readyKeys.iterator();
//
//            while (iterator.hasNext()) {
//                SelectionKey key = iterator.next();
//
//                if (key.isAcceptable()) {
//                    server.handleAcceptEvent(selector, key);
//                }
//
//                if (key.isReadable()) {
//                    readEventListenerKey = server.handleReadEvent(selector, key);
//
//                }
//
//                if (key.isWritable()) {
//                    myKey = server.handleWriteEvent(selector, key);
//                    stopped = true;
//                }
//
//                iterator.remove();
//            }
//        }
//
//        BasicSession session = (BasicSession) myKey.attachment();
//        assertTrue(session.hasData());
//
//        socket.close();
//        selector.close();
//    }

    @Test
    public void whenDataIsReadyForFlashingThenWriteEventIsRegistered() throws Exception {
        selector = Selector.open();
        server.init(4096, 2000, pool);

        // Create session with data
        BasicSession session = new Session();
        session.init(4096, 2000, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(bytes);
        session.writeWithFlip(buffer);

        // Register a listener for accept event
        registerAcceptListenerForPort(5000, session);

        // Connect to opened server socket
        Socket socket = new Socket("localhost", 5000);

        SelectionKey writeEventListenerKey = null;
        boolean stopped = false;
        while (!stopped) {
            // Select the event
            selector.select();

            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {
                    server.handleAcceptEvent(selector, key);
                }

                if (key.isWritable()) {
                    writeEventListenerKey = server.handleWriteEvent(selector, key);
                    stopped = true;
                }

                iterator.remove();
            }
        }
        // Check if selector contains read event listener
        assert writeEventListenerKey != null;
        assertTrue(writeEventListenerKey.interestOps() == (SelectionKey.OP_READ));

        socket.close();
        closeOpenedServerSockets();
        selector.close();
    }

    @Test
    public void whenHandlingWriteEventThenCorrectDataIsWrittenToDestinationSocket() throws Exception {
        selector = Selector.open();
        server.init(4096, 2000, pool);

        // Create session with data
        BasicSession session = new Session();
        session.init(4096, 2000, 5000);
        byte[] bytes = "test\n".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(bytes);
        session.writeWithFlip(buffer);

        // Register a listener for accept event
        registerAcceptListenerForPort(5000, session);


        // Connect to opened server socket
        Socket socket = new Socket("localhost", 5000);

        String data = null;

        boolean stopped = false;
        while (!stopped) {
            // Select the event
            selector.select();

            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {
                    server.handleAcceptEvent(selector, key);
                }

                if (key.isWritable()) {
                    SocketListener listener = new SocketListener(socket);

                    // Listen to destination socket
                    new Thread(listener).start();

                    synchronized (listener) {
                        server.handleWriteEvent(selector, key);
                        System.out.println("waiting listener to receive data...");
                        listener.wait();
                        data = listener.getData();
                    }

                    stopped = true;
                }

                iterator.remove();
            }
        }


        assertEquals("test", data);

        socket.close();
        closeOpenedServerSockets();
        selector.close();
    }

    @Test
    public void whenWritingToPortThenOtherEndGetsCorrectData() throws Exception {
        pool.addRule(5000, 5001);
        server.init(4096, 2000, pool);

        Thread serve = new Thread(server);
        serve.start();

        Socket sender = new Socket("localhost", 5000);
        Socket receiver = new Socket("localhost", 5001);

        SocketWriter writer = new SocketWriter(sender, "test", 100);
        SocketListener listener = new SocketListener(receiver);

        new Thread(writer).start();

        Thread.sleep(100);
        new Thread(listener).start();

        String data;
        synchronized (listener) {
            listener.wait();
            data = listener.getData();
        }


        assertEquals("test", data);
        sender.close();
        receiver.close();
        serve.interrupt();
        server.deregisterChannels();
        server.shutdown();
    }

    @Test
    public void whenConnectionIsLostThenKeyIsReregisteredAndSelectorWaitsForAnotherConnection() throws Exception {
        pool.addRule(5000, 5001);
        server.init(4096, 20000, pool);

        Thread serve = new Thread(server);
        serve.start();

        Socket sender = new Socket("localhost", 5000);
        SocketWriter writer = new SocketWriter(sender, "test", 5);

        new Thread(writer).start();
        // This sleep is for writer to write
        Thread.sleep(50);
        sender.close();

        sender = new Socket("localhost", 5000);
        writer = new SocketWriter(sender, "againtest", 5);

        Thread wr = new Thread(writer);
        wr.start();

        // This sleep is for writer to write
        // Signals not used because I'm lazy
        Thread.sleep(50);
        wr.interrupt();

        Socket receiver = new Socket("localhost", 5001);
        BufferedSocketListener listener = new BufferedSocketListener(receiver);
        Thread listen = new Thread(listener);

        listen.run();

        String data = listener.getData();

        assertEquals("test\nagaintest\n", data);

        sender.close();
        receiver.close();

        server.deregisterChannels();
        server.shutdown();
        listen.interrupt();
        serve.interrupt();
    }

    @After
    public void tearDown() throws Exception {
        if (selector != null && selector.isOpen())
            selector.close();
    }

}