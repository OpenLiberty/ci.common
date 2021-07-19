package io.openliberty.tools.common.plugins.util;

public class HotKey {
    HotKey(char key, String message){
        this.key = key;
        this.message = message;
    }

    public void printMessage(){
        return this.message;
    }

    public boolean checkKeyPress(String line, String checkWord){
        if (line != null && (line.trim().equalsIgnoreCase(this.key) || line.trim().equalsIgnoreCase(checkWord))) {
            return true;
        } else {
            return false;
        }
    }
}