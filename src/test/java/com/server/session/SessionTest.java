package com.server.session;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class SessionTest {
    private Session session;
    @Before
    public void setUp() throws Exception {
        session = new Session();
    }

    @Test
    public void whenBufferSizeIsSetThenSessionHasCorrectBufferSize() throws Exception {
        session.setBufferSize(4096);
        assertEquals(4096, session.getBufferSize());
    }

    @Test
    public void whenTimeoutIsSetThenSessionHasCorrectTimeout() throws Exception {
        session.setTimeout(2000);
        assertEquals(2000, session.getTimeout());
    }

    @Test
    public void whenPortIsSetThenSessionHasCorrectPort() throws Exception {
        session.setPort(5000);
        assertEquals(5000, session.getPort());
    }

    @Test
    public void whenInitializingThenValuesAreSetCorrectly() throws Exception {
        session.init(4096, 2000, 5000);
        assertEquals(4096, session.getBufferSize());
        assertEquals(2000, session.getTimeout());
        assertEquals(5000, session.getPort());
    }

    @Test
    public void whenDataIsWrittenThenSessionHasData() throws Exception {
        session.init(4096, 2000, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put(bytes);
        session.write(buffer);
        assertTrue(session.hasData());
    }

    @Test
    public void whenWritingMoreThanCapacityThenBufferReallocates() throws Exception {
        session.init(10, 2000, 5000);
        byte[] bytes = "too_long_array".getBytes();
        // note the size of allocated buffer is more than session capacity
        ByteBuffer buffer = ByteBuffer.allocate(15);
        buffer.put(bytes);

        session.write(buffer);

        assertEquals(16, session.getBufferSize());
        assertTrue(session.hasData());
    }

    @Test
    public void whenConstructingThenDefaultFieldsApplied() throws Exception {
        assertEquals(512, session.getBufferSize());
        assertEquals(2000, session.getTimeout());
        assertEquals( 5000, session.getPort());
    }

    @Test
    public void whenWritingToSessionThenCorrectDataIsReadable() throws Exception {
        session.init(4096, 2000, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put(bytes);

        session.write((ByteBuffer) buffer.flip());

        ByteBuffer result = ByteBuffer.allocate(4096);
        session.flashTo(result);

        ByteBuffer expected = ByteBuffer.allocate(4096);
        expected.put(bytes);

        assertEquals(expected, result);
    }

    @Test
    public void whenWritingWithFlipThenCorrectDataIsReadable() throws Exception {
        session.init(4096, 2000, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put(bytes);

        session.writeWithFlip(buffer);

        ByteBuffer result = ByteBuffer.allocate(4096);
        session.flashTo(result);

        ByteBuffer expected = ByteBuffer.allocate(4096);
        expected.put(bytes);

        assertEquals(expected, result);
    }

    @Test
    public void whenWritingToSessionWrappedThenCorrectDataIsReadable() throws Exception {
        session.init(4096, 2000, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(bytes.length);

        session.write((ByteBuffer) buffer.flip());

        ByteBuffer result = ByteBuffer.allocate(4096);
        session.flashTo(result);

        ByteBuffer expected = ByteBuffer.allocate(4096);
        expected.put(bytes);

        assertEquals(expected, result);
    }

    @Test
    public void whenBufferOverflowsWritingWrappedThenCleanUp() throws Exception {
        session.init(10, 2000, 5000);
        byte[] bytes = "test_too".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(bytes.length);

        session.write((ByteBuffer) buffer.flip());

        // second write will make buffer overflow
        session.write((ByteBuffer) buffer.flip());

        ByteBuffer expected = ByteBuffer.allocate(10);
        expected.put(bytes);

        ByteBuffer result = ByteBuffer.allocate(10);
        session.flashTo(result);

        assertEquals(expected, result);
    }

    @Test
    public void whenBufferOverflowsThenCleanUp() throws Exception {
        session.init(10, 2000, 5000);
        byte[] bytes = "test_too".getBytes();
        // this is same way of passing buffer to session
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put(bytes);

        session.write((ByteBuffer) buffer.flip());

        // second write will make buffer overflow
        session.write((ByteBuffer) buffer.flip());

        ByteBuffer expected = ByteBuffer.allocate(10);
        expected.put(bytes);

        ByteBuffer result = ByteBuffer.allocate(10);
        session.flashTo(result);

        assertEquals(expected, result);
    }

    @Test
    public void whenBufferHasRemainingThenDataIsWrittenConsequently() throws Exception {
        session.init(20, 2000, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(bytes);
        // write same message twice
        session.write((ByteBuffer) buffer.flip());
        session.write((ByteBuffer) buffer.flip());

        ByteBuffer expected = ByteBuffer.allocate(20);
        expected.put(bytes);
        expected.put(bytes);

        ByteBuffer result = ByteBuffer.allocate(20);
        session.flashTo(result);

        assertEquals(expected, result);
    }

    @Test
    public void whenConvertedToHexWrittenWrappedStringThenResultIsValid() throws Exception {
        // method works even if position is broken
        session.write(ByteBuffer.wrap("test123".getBytes()));

        assertEquals("74 65 73 74 31 32 33", session.getBufferAsString());
        assertEquals("74 65 73 74 31 32 33", session.getBufferAsString());
    }

    @Test
    public void whenConvertedToHexWrittenStringThenResultIsValid() throws Exception {
        byte[] bytes = "test123".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(bytes);

        session.write((ByteBuffer) buffer.flip());
        assertEquals("74 65 73 74 31 32 33", session.getBufferAsString());
        // second assertion checks if state of inner buffer remains unchanged after method invocation
        assertEquals("74 65 73 74 31 32 33", session.getBufferAsString());
    }

    @Test
    public void whenDataIsReadThenBufferIsFlashed() throws Exception {
        byte[] bytes = "test123".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(bytes);
        // writing data to session
        session.write((ByteBuffer) buffer.flip());
        ByteBuffer result = ByteBuffer.allocate(20);
        // reading data should flash the inner buffer
        session.flashTo(result);

        assertFalse(session.hasData());
    }

    @Test
    public void whenWritingToSessionThenReadValueIsWrittenAndReadCorrectly() throws Exception {
        // write data to session
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.put(bytes);

        session.write((ByteBuffer) buffer.flip());

        // read the written data
        ByteBuffer result = ByteBuffer.allocate(20);
        session.flashTo(result);

        // write received data again
        session.write((ByteBuffer) result.flip());

        result.clear();
        result.put(new byte[20]);
        result.clear();

        // check if nothing changed
        session.flashTo(result);

        ByteBuffer expected = ByteBuffer.allocate(20);
        expected.put(bytes);

        assertEquals(expected, result);
    }

    @Test
    public void whenBufferIsReadAfterTimeoutThenNoDataIsReturned() throws Exception {
        session.init(4096, 50, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(bytes.length);

        session.write((ByteBuffer) buffer.flip());

        // Wait for buffer to flash
        Thread.sleep(60);

        ByteBuffer result = ByteBuffer.allocate(20);
        session.flashTo(result);

        ByteBuffer expected = ByteBuffer.allocate(20);

        assertEquals(expected, result);
    }

    @Test
    public void whenBufferIsWrittenAfterTimeoutThenBufferIsFlashed() throws Exception {
        session.init(4096, 50, 5000);
        byte[] bytes = "test".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(bytes.length);

        session.write((ByteBuffer) buffer.flip());
        // Wait for buffer to flash
        Thread.sleep(60);
        session.write((ByteBuffer) buffer.flip());

        // Read session data
        ByteBuffer result = ByteBuffer.allocate(20);
        session.flashTo(result);

        ByteBuffer expected = ByteBuffer.allocate(20);
        expected.put(bytes);

        assertEquals(expected, result);
    }

    @After
    public void tearDown() throws Exception {
    }

}