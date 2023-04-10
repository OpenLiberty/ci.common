package io.openliberty.tools.common;

public class TestLogger implements CommonLoggerI {

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        System.out.println("[DEBUG] " + msg);
    }

    @Override
    public void debug(Throwable e) {
        System.out.println("[DEBUG] " + e.getMessage());
    }

    @Override
    public void debug(String msg, Throwable e) {
        System.out.println("[DEBUG] " + msg + ": " + e.getMessage());
    }

    @Override
    public void warn(String msg) {
        System.out.println("[WARN] " + msg);
    }

    @Override
    public void info(String msg) {
        System.out.println("[INFO] " + msg);
    }

    @Override
    public void error(String msg) {
        System.out.println("[ERROR] " + msg);
    }

}