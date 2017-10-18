package com;

import com.config.loader.ConfigLoader;
import com.config.loader.InvalidConfigFormatException;
import com.gui.App;
import com.gui.BestGui;
import com.gui.Gui;
import com.gui.IGui;
import com.server.Server;
import com.server.portpool.ManageablePortPool;
import com.server.portpool.PortPool;
import org.json.JSONException;

import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.logging.Logger;

public class ExecutionManager {
    private static final Logger logger = Logger.getLogger(ExecutionManager.class.getName());

    public static void main(String[] args) {
        // Load config file
        ConfigLoader loader = new ConfigLoader();
        Map<Integer, Integer> rules = null;
        int timeout = 0;

        try {
            loader.parse("config/config.json");
            rules = loader.getRules();
            timeout = (int) loader.getTimeout();
        } catch (NoSuchFileException | InvalidConfigFormatException | JSONException e) {
            // Unable to read config file. Continue with default settings
            e.printStackTrace();
        }

        // Create pool of ports
        ManageablePortPool pool = new PortPool();
        assert rules != null;
        for (Map.Entry<Integer, Integer> entry : rules.entrySet()) {
            pool.addRule(entry.getKey(), entry.getValue());
        }

        Server server = new Server();
        server.init(4096, timeout, pool);

        // Run GUI
//        App app = new App(server, pool, loader);
//        app.createAndRunGUI();
        IGui gui = new BestGui(server, pool, loader);
        gui.start();

    }
}
