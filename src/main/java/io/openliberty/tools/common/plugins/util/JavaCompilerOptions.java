/**
 * (C) Copyright IBM Corporation 2020.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.openliberty.tools.common.plugins.util;

import java.util.ArrayList;
import java.util.List;

public class JavaCompilerOptions {
    
    /**
     * Source Java version
     */
    private String source;

    /**
     * Target Java version
     */
    private String target;

    /**
     * Java release version
     */
    private String release;

    /**
     * Whether to show warnings. Default is false.
     */
    private boolean showWarnings = false;

    /**
     * Source file encoding
     */
    private String encoding;

    /**
     * Get list of options that can be passed to the compiler.
     * Options with values are represented as multiple strings in the list
     *  e.g. "-source" and "1.8"
     * 
     * @return List of options
     */
    public List<String> getOptions() {
        List<String> options = new ArrayList<String>();
        if (!showWarnings) {
            options.add("-nowarn");
        }
        addStringOption(options, "-source", source);
        addStringOption(options, "-target", target);
        addStringOption(options, "--release", release);
        addStringOption(options, "-encoding", encoding);
        return options;
    }

    private static void addStringOption(List<String> options, String optionKey, String optionValue) {
        if (optionValue != null && !optionValue.trim().isEmpty()) {
            options.add(optionKey);
            options.add(optionValue.trim());
        }
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean isShowWarnings() {
        return showWarnings;
    }

    public void setShowWarnings(boolean showWarnings) {
        this.showWarnings = showWarnings;
    }

    public String getRelease() {
        return release;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

}
