package com.server.portpool;

import java.util.ArrayList;
import java.util.Map;

public interface BasicPortPool {
    int getRule(int port);
    ArrayList<Integer> getPorts();
    Map<Integer, Integer> getRules();
}
