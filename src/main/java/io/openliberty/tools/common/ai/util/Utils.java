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
import java.util.List;
import java.util.function.Function;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Utils {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final int CONSOLE_WIDTH = 79;

    public static LineReader reader;
    public static Terminal terminal;

    public static String bold(String text) {
        return ansi().bold().fgBrightDefault().a(text).reset().toString();
    }

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
        return bold(absolutePath);
    }

    public static LineReader getReader() {
        if (reader == null) {
            try {
                terminal = TerminalBuilder.builder().system(true).build();
                reader = LineReaderBuilder.builder()
                             .terminal(terminal)
                             .parser(new MultiLineParser())
                             .variable(LineReader.SECONDARY_PROMPT_PATTERN, "")
                             .build();
            } catch (IOException e) {
                // do nothing
            }
        }
        return reader;
    }

    public static void closeTerminal() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            terminal = null;
        }
    }

    public static boolean confirm(String message) {
        String question = message + " [" +
            ansi().bold().fgCyan().a("yes").reset() + "|" +
            ansi().bold().fgCyan().a("no").reset() + "] ";
        LoadingThread.hide();
        String answer = getReader().readLine(question).trim();
        LoadingThread.show();
        return answer.equalsIgnoreCase("yes") || answer.equalsIgnoreCase("y");
    }

    public static <T> T promptSelection(List<T> objects, Function<T, String> toString, String prefix) {
        StringBuilder message = new StringBuilder();
        message.append(prefix);
        message.append("\n");
        for (int i = 0; i < objects.size(); i++) {
            message.append((i + 1) + ": " + toString.apply(objects.get(i)) + "\n");
        }
        message.append("Input a selection [1-" + objects.size() + "]: ");
        LoadingThread.hide();
        String answer = getReader().readLine(message.toString()).trim();
        T object = objects.get(Integer.parseInt(answer) - 1);
        LoadingThread.show();
        return object;
    }

    public static void printReplyTop() {
        System.out.print(IS_WINDOWS ? "\n+" : "\n\u250C");
        for (int i = 0; i < CONSOLE_WIDTH; i++) {
            System.out.print(IS_WINDOWS ? "-" : "\u2500");
        }
        System.out.println("\n");
    }

    public static void printReplyBottom() {
        System.out.print(IS_WINDOWS ? "+" : "\u2514");
        for (int i = 0; i < CONSOLE_WIDTH; i++) {
            System.out.print(IS_WINDOWS ? "-" : "\u2500");
        }
        System.out.println("\n");
    }

}
