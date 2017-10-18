package com.server.portpool;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PortPool implements BasicPortPool, ManageablePortPool {
    private static final Logger logger = Logger.getLogger(PortPool.class.getName());
    private Map<Integer, Integer> rules;


    public PortPool() {
        rules = new HashMap<>();
    }

    public void addRule(int portA, int portB) throws InvalidParameterException {
        assert (portA > 0 && portB > 0);
        assert (portA != portB);

        if (rules.containsKey(portA) || rules.containsKey(portB)) {
            throw new InvalidParameterException("Pool contains rule with one of ports: " + portA + " " + portB);
        }

        rules.put(portA, portB);
        rules.put(portB, portA);

        logger.info("Rule for ports " + portA + "=" + portB + " added.");
    }

    public int getRule(int port) {
        if (rules.containsKey(port))
            return rules.get(port);

        logger.log(Level.WARNING, "Accessed port " + port + " has no rule in pool.\n" +
                "Current pool state is:\n" + this.toString());

        return 0;
    }

    public void deleteRule(int port) {
        if (!rules.containsKey(port)) {
            logger.info("Attempting to delete nonexistent rule for port " + port);
            return;
        }

        logger.info("Deleting rule for port " + port);

        int dest = getRule(port);

        rules.remove(port);
        rules.remove(dest);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<Integer, Integer> entry : this.rules.entrySet()) {
            builder.append(entry.getKey());
            builder.append(" ---> ");
            builder.append(entry.getValue());
            builder.append("\n");
        }

        return
                "-----------------------\n" +
                getClass().getName() +"\n" +
                "Rules:\n" +
                builder.toString() +
                "-----------------------\n";
    }

    public ArrayList<Integer> getPorts() {
        ArrayList<Integer> list = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : rules.entrySet()) {
            if (!list.contains(entry.getKey())) {
                list.add(entry.getKey());
                list.add(entry.getValue());
            }
        }

        return list;
    }

    public Map<Integer, Integer> getRules() {
        Map<Integer, Integer> result = new HashMap<>();

        for (Map.Entry entry: rules.entrySet()) {
            int port = (int) entry.getValue();
            if (!result.containsKey(port)) {
                result.put((int) entry.getKey(), (int) entry.getValue());
            }
        }

        return result;
    }

}
