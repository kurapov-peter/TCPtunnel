package com.config.loader;

import com.server.portpool.BasicPortPool;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConfigLoader implements ConfigSaver {
    private static final Logger logger = Logger.getLogger(ConfigLoader.class.getName());
    private JSONObject config = new JSONObject();

    private void parseJsonString(String jsonString) throws JSONException {
        // parse raw string into json
        this.config = new JSONObject(jsonString);

    }

    private String readFile(String path, Charset encoding) throws IOException {
        // Read raw data from file
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    public Object getTimeout() throws JSONException {
        if (this.config.has("timeout")) {
            return this.config.getInt("timeout");
        }

        logger.log(Level.WARNING, "Timeout not found in config.");

        return null;
    }

    public Map<Integer, Integer> getRules() throws InvalidConfigFormatException {
        Map<Integer, Integer> rules = new HashMap<>();

        if (!this.config.has("rules")) {
            logger.log(Level.WARNING, "Rules not found in config.");
            return rules;
        }

        try {
            JSONArray rulesArray = config.getJSONArray("rules");

            for (int i = 0; i < rulesArray.length(); i++) {
                JSONArray pair = rulesArray.getJSONArray(i);

                if (pair.length() != 2) {
                    logger.log(Level.WARNING, "Config contains wrong format rule at position " + i +
                            ". Skipping rule " + pair.toString());
                    continue;
                }

                rules.put(pair.getInt(0), pair.getInt(1));
            }
        } catch (JSONException e) {
            throw new InvalidConfigFormatException(config.toString() + "\n" + e.getMessage());
        }

        return rules;
    }

    public void parse(String path) throws NoSuchFileException, InvalidConfigFormatException {
        logger.info("Parsing config file " + path + " ...");

        String data = null;

        try {
            data = this.readFile(path, StandardCharsets.UTF_8);
            this.parseJsonString(data);

        } catch (IOException e) {
            throw new NoSuchFileException(path);
        } catch (JSONException e) {
            throw new InvalidConfigFormatException(data);
        }
    }

    public void save(BasicPortPool pool, long timeout, String path) {
        JSONObject config = new JSONObject();

        // Store timeout
        try {
            config.put("timeout", timeout);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Store rules array
        JSONArray rules = new JSONArray();
        for (Map.Entry entry : pool.getRules().entrySet()) {
            JSONArray rule = new JSONArray();
            rule.put(entry.getKey());
            rule.put(entry.getValue());

            rules.put(rule);
        }

        try {
            config.put("rules", rules);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Save data to file
        try {
            FileWriter out = new FileWriter(path);
            out.write(config.toString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
