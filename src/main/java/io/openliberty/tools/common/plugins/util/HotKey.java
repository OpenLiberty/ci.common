package io.openliberty.tools.common.plugins.util;

public class HotKey {

    private String[] keywords; 

    /**
     * @param keywords The words that triggers the hotkey. Can single letters or full words.
     */
    public HotKey(String... keywords){
        this.keywords = keywords;
    }

    public boolean isPressed(String line){
        if (line != null) {
            for (String keyword : keywords) {
                if (line.trim().equalsIgnoreCase(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }
}