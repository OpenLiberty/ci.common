/**
 * (C) Copyright IBM Corporation 2021.
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

public class HotKey {

    private String[] keywords; 

    /**
     * @param keywords The words that can trigger the hotkey. Can be single letters or full words.
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