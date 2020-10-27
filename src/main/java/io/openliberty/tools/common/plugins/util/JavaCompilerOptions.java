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

import java.util.HashSet;
import java.util.Set;

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
     * Set of options that can be passed to the compiler.
     * @return
     */
    public Set<String> getOptions() {
        Set<String> options = new HashSet<String>();
        addStringOption(options, "source", source);
        addStringOption(options, "target", target);
        return options;
    }

    private static void addStringOption(Set<String> options, String optionName, String optionValue) {
        if (optionValue != null && !optionValue.trim().isEmpty()) {
            options.add("-" + optionName + " " + optionValue.trim());
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

}
