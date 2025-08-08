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

import static org.fusesource.jansi.Ansi.ansi;

import java.io.File;
import java.io.IOException;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Utils {

    public static LineReader reader;

    public static boolean isEmptyPath(String path) {
        String children[] = new File(path).list();
        return children == null || children.length == 0;
    }

    public static String getAbsolutePath(String p) {
        return getAbsolutePath(new File(p));
    }

    public static String getAbsolutePath(File p) {
        String absolutePath;
        try {
            absolutePath = p.getCanonicalFile().getAbsolutePath();
        } catch (IOException e) {
            absolutePath = p.getAbsolutePath();
        }
        return ansi().bold().fgBrightDefault().a(absolutePath).reset().toString();
    }

    public static LineReader getReader() {
        if (reader == null) {
            Terminal terminal;
			try {
				terminal = TerminalBuilder.builder().system(true).build();
	            reader = LineReaderBuilder.builder().terminal(terminal).build();
			} catch (IOException e) {
				// do nothing
			}
        }
        return reader;
    }

    public static boolean confirm(String message) {
        String question = message + " [" +
            ansi().bold().fgCyan().a("yes").reset() + "|" +
            ansi().bold().fgCyan().a("no").reset() + "] ";
        LoadingThread.hide();
        String answer = getReader().readLine(question).trim();
        LoadingThread.show();
        return answer.equalsIgnoreCase("yes");
    }

}
