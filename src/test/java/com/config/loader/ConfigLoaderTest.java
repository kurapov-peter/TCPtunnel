package com.config.loader;

import com.server.portpool.ManageablePortPool;
import com.server.portpool.PortPool;
import org.junit.Test;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConfigLoaderTest {
    private Map<Integer, Integer> expected;
    private ConfigLoader loader;

    @org.junit.Before
    public void setUp() throws Exception {
        expected = new HashMap<>();
        expected.put(2343, 1414);
        expected.put(5001, 1234);
        expected.put(2222, 1111);
        loader = new ConfigLoader();
    }

    @Test
    public void whenParsingFileThenAllFieldsParsedCorrectly() throws Exception {
        loader.parse("target/classes/config/valid_config_file.json");
        assertEquals(expected, loader.getRules());
        assertEquals(1000, loader.getTimeout());
    }

    @Test (expected = NoSuchFileException.class)
    public void whenParsingFileWrongPathThenIOExceptionIsThrown() throws Exception {
        loader.parse("wrong/file/path");
    }

    @Test (expected = InvalidConfigFormatException.class)
    public void whenParsingFileWrongFormatThenJSONExceptionIsThrown() throws Exception {
        loader.parse("target/classes/config/invalid_config.json");
    }

    @Test
    public void whenParsingFileWithInvalidRulesFormatThenRuleIsSkipped() throws Exception {
        loader.parse("target/classes/config/invalid_rules_format.json");
        Map<Integer, Integer> expected = new HashMap<>();
        expected.put(4324, 3333);
        assertEquals(expected, loader.getRules());
    }

    @Test
    public void whenSavingConfigThenCorrectDataIsStored() throws Exception {
        ManageablePortPool pool = new PortPool();

        pool.addRule(5000, 5001);
        pool.addRule(1234, 4432);

        long timeout = 2000;

        loader.save(pool, timeout, "target/classes/config/save_config.json");

        String actual = new String(Files.readAllBytes(Paths.get("target/classes/config/save_config.json")));

        assertEquals("{\"rules\":[[4432,1234],[5000,5001]],\"timeout\":2000}", actual);

        // clear save_config.json
        FileWriter file = new FileWriter("target/classes/config/save_config.json");
        file.write("{}");
        file.close();
    }

    @org.junit.After
    public void tearDown() throws Exception {
    }

}