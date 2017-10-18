package com.server.portpool;


public interface ManageablePortPool extends BasicPortPool {
    void addRule(int portA, int portB);
    String toString();
    void deleteRule(int port);
}
