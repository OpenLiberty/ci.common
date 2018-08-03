/**
 * (C) Copyright IBM Corporation 2018.
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
package net.wasdev.wlp.common.springboot.util;

public class SpringBootThinException extends Exception{
    
    private static final long serialVersionUID = 1L;

    public SpringBootThinException(String message) {
        super(message);
    }
    
    public SpringBootThinException(String message, Throwable cause) {
        super(message, cause);
    }
}
