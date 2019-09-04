/**
 * (C) Copyright IBM Corporation 2018, 2019.
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

/**
 * Generic exception that should not fail the build but instead be handled gracefully.
 *
 */
public class PluginScenarioException extends Exception {

    private static final long serialVersionUID = 1L;

    public PluginScenarioException(String message) {
        super(message);
    }
    
    public PluginScenarioException(String message, Throwable e) {
        super(message, e);
    }
    
    public PluginScenarioException(Throwable e) {
        super(e);
    }
}
