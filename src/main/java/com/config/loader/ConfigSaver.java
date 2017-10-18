package com.config.loader;

import com.server.portpool.BasicPortPool;

public interface ConfigSaver {
    void save(BasicPortPool pool, long timeout, String path);
}
