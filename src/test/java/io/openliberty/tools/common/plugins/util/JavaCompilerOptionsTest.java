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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class JavaCompilerOptionsTest {

    @Test
    public void testAllOptions() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setSource("9");
        jco.setTarget("1.8");

        Set<String> result = jco.getOptions();
        assertEquals(2, result.size());
        assertTrue(result.contains("-source 9"));
        assertTrue(result.contains("-target 1.8"));
    }
    
    @Test
    public void testNoOptions() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();

        Set<String> result = jco.getOptions();
        assertEquals(0, result.size());
    }

    @Test
    public void testSource() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setSource("10");

        Set<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.contains("-source 10"));
    }

    @Test
    public void testTarget() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setTarget("10");

        Set<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.contains("-target 10"));
    }

}
