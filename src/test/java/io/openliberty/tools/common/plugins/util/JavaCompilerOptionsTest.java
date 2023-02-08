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

import java.util.List;

import org.junit.Test;

public class JavaCompilerOptionsTest {

    @Test
    public void testAllOptions() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setShowWarnings(true);
        jco.setSource("9");
        jco.setTarget("1.8");
        jco.setRelease("10");
        jco.setEncoding("UTF-8");
        
        List<String> result = jco.getOptions();
        int i = 0;
        assertTrue(result.get(i++).equals("-source"));
        assertTrue(result.get(i++).equals("9"));
        assertTrue(result.get(i++).equals("-target"));
        assertTrue(result.get(i++).equals("1.8"));
        assertTrue(result.get(i++).equals("--release"));
        assertTrue(result.get(i++).equals("10"));
        assertTrue(result.get(i++).equals("-encoding"));
        assertTrue(result.get(i++).equals("UTF-8"));
        assertEquals(i, result.size());
    }
    
    @Test
    public void testDefaultOptions() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();

        List<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
    }

    @Test
    public void testSource() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setSource("10");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
        assertTrue(result.get(1).equals("-source"));
        assertTrue(result.get(2).equals("10"));
    }

    @Test
    public void testTarget() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setTarget("10");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
        assertTrue(result.get(1).equals("-target"));
        assertTrue(result.get(2).equals("10"));
    }

    @Test
    public void testRelease() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setRelease("10");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
        assertTrue(result.get(1).equals("--release"));
        assertTrue(result.get(2).equals("10"));
    }

    @Test
    public void testEncoding() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setEncoding("UTF-8");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
        assertTrue(result.get(1).equals("-encoding"));
        assertTrue(result.get(2).equals("UTF-8"));
    }

}
