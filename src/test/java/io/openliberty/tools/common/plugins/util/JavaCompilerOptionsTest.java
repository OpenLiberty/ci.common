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
        assertEquals(8, result.size());
        assertTrue(result.contains("-source"));
        assertTrue(result.contains("9"));
        assertTrue(result.contains("-target"));
        assertTrue(result.contains("1.8"));
        assertTrue(result.contains("--release"));
        assertTrue(result.contains("10"));
        assertTrue(result.contains("-encoding"));
        assertTrue(result.contains("UTF-8"));
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
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-source"));
        assertTrue(result.contains("10"));
    }

    @Test
    public void testTarget() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setTarget("10");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-target"));
        assertTrue(result.contains("10"));
    }

    @Test
    public void testRelease() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setRelease("10");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("--release"));
        assertTrue(result.contains("10"));
    }

    @Test
    public void testEncoding() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setEncoding("UTF-8");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-encoding"));
        assertTrue(result.contains("UTF-8"));
    }

    @Test
    public void testAnnotationProcessorPath() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessorPath("/path/to/lombok.jar:/path/to/mapstruct.jar");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-processorpath"));
        assertTrue(result.contains("/path/to/lombok.jar:/path/to/mapstruct.jar"));
    }

    @Test
    public void testAnnotationProcessorPathWithOtherOptions() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setRelease("17");
        jco.setEncoding("UTF-8");
        jco.setAnnotationProcessorPath("/path/to/lombok.jar");

        List<String> result = jco.getOptions();
        assertEquals(7, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("--release"));
        assertTrue(result.contains("17"));
        assertTrue(result.contains("-encoding"));
        assertTrue(result.contains("UTF-8"));
        assertTrue(result.contains("-processorpath"));
        assertTrue(result.contains("/path/to/lombok.jar"));
    }

    @Test
    public void testAnnotationProcessorPathNull() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessorPath(null);

        List<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
    }

    @Test
    public void testAnnotationProcessorPathEmpty() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessorPath("");

        List<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.get(0).equals("-nowarn"));
    }

    @Test
    public void testAnnotationProcessors() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessors("lombok.AnnotationProcessor,org.mapstruct.ap.MappingProcessor");

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-processor"));
        assertTrue(result.contains("lombok.AnnotationProcessor,org.mapstruct.ap.MappingProcessor"));
    }

    @Test
    public void testAnnotationProcessorsWithPath() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessorPath("/path/to/lombok.jar");
        jco.setAnnotationProcessors("lombok.AnnotationProcessor");

        List<String> result = jco.getOptions();
        assertEquals(5, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-processorpath"));
        assertTrue(result.contains("/path/to/lombok.jar"));
        assertTrue(result.contains("-processor"));
        assertTrue(result.contains("lombok.AnnotationProcessor"));
    }

    @Test
    public void testAnnotationProcessorsNull() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessors(null);

        List<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.contains("-nowarn"));
    }

    @Test
    public void testAnnotationProcessorsEmpty() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        jco.setAnnotationProcessors("");

        List<String> result = jco.getOptions();
        assertEquals(1, result.size());
        assertTrue(result.contains("-nowarn"));
    }

    @Test
    public void testCompilerArgs() throws Exception {
        JavaCompilerOptions jco = new JavaCompilerOptions();
        List<String> args = new java.util.ArrayList<>();
        args.add("-parameters");
        args.add("-Xlint:unchecked");
        jco.setCompilerArgs(args);

        List<String> result = jco.getOptions();
        assertEquals(3, result.size());
        assertTrue(result.contains("-nowarn"));
        assertTrue(result.contains("-parameters"));
        assertTrue(result.contains("-Xlint:unchecked"));
    }

}
