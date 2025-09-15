/**
 * (C) Copyright IBM Corporation 2025
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
package io.openliberty.tools.common.ai.util;

import java.util.ArrayList;
import java.util.List;

public class LibertyFeature {

    private String shortName;
    private String name;
    private String shortDescription;
    private List<String> platforms = new ArrayList<String>();

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public List<String> getPlatforms() {
        return platforms;
    }

    public void setPlatforms(List<String> platforms) {
        this.platforms = platforms;
    }

    public boolean isMicroProfileFeature() {
        for (String p : platforms) {
            if (p.startsWith("microProfile-")) {
                return true;
            }
        }
        return false;
    }

    public boolean isJakartaEEFeature() {
        for (String p : platforms) {
            if (p.startsWith("jakartaee-")) {
                return true;
            }
        }
        return false;
    }

    public boolean isJavaEEFeature(String version) {
        for (String p : platforms) {
            if (p.startsWith("javaee-" + version)) {
                return true;
            }
        }
        return false;
    }

    public boolean isJakartaEEFeature(String version) {
        for (String p : platforms) {
            if (p.startsWith("jakartaee-" + version)) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return shortName + " : " + name;
    }
}
