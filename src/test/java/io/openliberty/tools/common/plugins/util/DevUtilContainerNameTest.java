/**
 * (C) Copyright IBM Corporation 2026.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DevUtilContainerNameTest extends BaseDevUtilTest {

    @Test
    public void testSanitize_null() {
        assertEquals("", DevUtil.sanitizeContainerNameSegment(null));
    }

    @Test
    public void testSanitize_emptyString() {
        assertEquals("", DevUtil.sanitizeContainerNameSegment(""));
    }

    @Test
    public void testSanitize_blankString() {
        assertEquals("", DevUtil.sanitizeContainerNameSegment("   "));
    }

    @Test
    public void testSanitize_simpleAlphanumeric() {
        assertEquals("myapp", DevUtil.sanitizeContainerNameSegment("myApp"));
    }

    @Test
    public void testSanitize_hyphenAndDot() {
        assertEquals("my-app.v1", DevUtil.sanitizeContainerNameSegment("my-app.v1"));
    }

    @Test
    public void testSanitize_specialCharactersReplacedWithHyphen() {
        assertEquals("com.example-myapp", DevUtil.sanitizeContainerNameSegment("com.example:myApp"));
        assertEquals("my-app", DevUtil.sanitizeContainerNameSegment("my app"));
        assertEquals("a-b", DevUtil.sanitizeContainerNameSegment("a/b"));
    }

    @Test
    public void testSanitize_consecutiveSeparatorsCollapsed() {
        assertEquals("a-b", DevUtil.sanitizeContainerNameSegment("a..b"));
        assertEquals("a-b", DevUtil.sanitizeContainerNameSegment("a--b"));
        assertEquals("a-b", DevUtil.sanitizeContainerNameSegment("a_.b"));
    }

    @Test
    public void testSanitize_leadingAndTrailingSeparatorsRemoved() {
        assertEquals("app", DevUtil.sanitizeContainerNameSegment("-app-"));
        assertEquals("app", DevUtil.sanitizeContainerNameSegment("..app.."));
    }

    @Test
    public void testSanitize_truncatesLongInput() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) sb.append('a');
        String result = DevUtil.sanitizeContainerNameSegment(sb.toString());
        assertTrue("Sanitized segment must be <= 110 chars", result.length() <= 110);
    }

    @Test
    public void testSanitize_upperCaseLowered() {
        assertEquals("moduleabc", DevUtil.sanitizeContainerNameSegment("ModuleABC"));
    }

    @Test
    public void testGenerateContainerName_noExistingContainers() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "moduleA", null);
        assertEquals("liberty-dev-modulea", util.callGenerateNewContainerName());
    }

    @Test
    public void testGenerateContainerName_preferredNameFree() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "moduleA", "some-other-container liberty-dev-moduleb");
        assertEquals("liberty-dev-modulea", util.callGenerateNewContainerName());
    }

    @Test
    public void testGenerateContainerName_preferredNameTaken_firstSuffix() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "moduleA", "liberty-dev-modulea");
        assertEquals("liberty-dev-modulea-1", util.callGenerateNewContainerName());
    }

    @Test
    public void testGenerateContainerName_preferredNameAndFirstSuffixTaken() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "moduleA", "liberty-dev-modulea liberty-dev-modulea-1");
        assertEquals("liberty-dev-modulea-2", util.callGenerateNewContainerName());
    }

    @Test
    public void testGenerateContainerName_differentModulesGetDifferentNames() throws Exception {
        DevTestUtil utilA = getNewDevUtil(temp.newFolder(), "moduleA", null);
        DevTestUtil utilB = getNewDevUtil(temp.newFolder(), "moduleB", null);
        assertNotEquals(utilA.callGenerateNewContainerName(), utilB.callGenerateNewContainerName());
    }

    @Test
    public void testGenerateContainerName_nullApplicationId() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), null, null);
        assertEquals("liberty-dev", util.callGenerateNewContainerName());
    }

    @Test
    public void testGenerateContainerName_applicationIdWithSpecialChars() throws Exception {
        DevTestUtil util = getNewDevUtil(temp.newFolder(), "com.example:my-App", null);
        assertEquals("liberty-dev-com.example-my-app", util.callGenerateNewContainerName());
    }
}
