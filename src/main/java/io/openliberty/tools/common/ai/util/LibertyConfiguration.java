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

public class LibertyConfiguration implements ILibertyConfiguration {

    public static class Type {

        private String name = null;
        private Integer min = null;
        private Integer max = null;
        private List<String> enumeration = null;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getMin() {
            return min;
        }

        public void setMin(Integer min) {
            this.min = min;
        }

        public Integer getMax() {
            return max;
        }

        public void setMax(Integer max) {
            this.max = max;
        }

        public List<String> getEnumeration() {
            return enumeration;
        }

        public void setEnumeration(List<String> enumeration) {
            this.enumeration = enumeration;
        }
        
        public void addEnumeration(String e) {
            if (this.enumeration == null) {
                this.enumeration = new ArrayList<String>();
            }
            this.enumeration.add(e);
        }
        
        public String toString() {
            if (enumeration != null) {
                String e = "[";
                for (int i = 0; i < enumeration.size(); i++) {
                    e += enumeration.get(i);
                    if (i < enumeration.size() - 1) {
                        e += ",";
                    }
                }
                e += "]";
                return e;
            }
            if (min == null) {
                if (max != null) {
                    return name + "(.." + max + "]";
                }
            } else {
                if (max == null) {
                    return name + "[" + min + "..)";    
                } else {
                    return name + "[" + min + ".." + max + "]";    
                }
            }
            return name;
        }

    }

    public static class Sequence implements ILibertyConfiguration {

        private String label = null;
        private String name = null;
        private String description = null;
        private String defaultValue = null;
        private String type = null;
        private Integer minOccurs = null;
        private Integer maxOccurs = null;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Integer getMinOccurs() {
            return minOccurs;
        }

        public void setMinOccurs(Integer minOccurs) {
            this.minOccurs = minOccurs;
        }

        public Integer getMaxOccurs() {
            return maxOccurs;
        }

        public void setMaxOccurs(Integer maxOccurs) {
            this.maxOccurs = maxOccurs;
        }

    }
    
    public static class Attribute implements ILibertyConfiguration {

        private String label = null;
        private String name = null;
        private String description = null;
        private String use = null;
        private String defaultValue = null;
        private Type type = null;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public String getUse() {
            return use;
        }
        
        public boolean isRequired() {
            return use != null && use.equalsIgnoreCase("required");
        }

        public void setUse(String use) {
            this.use = use;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

    }

    private String name = null;
    private String label = null;
    private String description = null;
    private Sequence sequence = null;
    private List<Attribute> attributes = null;
    private List<Attribute> choices = null;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Sequence getSequence() {
        return sequence;
    }

    public void setSequence(Sequence sequence) {
        this.sequence = sequence;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        if (this.attributes == null) {
            this.attributes = attributes;
        } else {
            attributes.addAll(attributes);
        }
    }
    
    public void addAttribute(Attribute attribute) {
        if (attribute == null) {
            return;
        }
        if (this.attributes == null) {
            this.attributes = new ArrayList<Attribute>();
        }
           attributes.add(attribute);
    }

    public List<Attribute> getChoices() {
        return choices;
    }

    public void setChoices(List<Attribute> choices) {
        if (this.choices == null) {
            this.choices = choices;
        } else {
            choices.addAll(choices);
        }
    }

    public void addChoice(Attribute attribute) {
        if (attribute == null) {
            return;
        }
        if (this.choices == null) {
            this.choices = new ArrayList<Attribute>();
        }
        choices.add(attribute);
    }
}
