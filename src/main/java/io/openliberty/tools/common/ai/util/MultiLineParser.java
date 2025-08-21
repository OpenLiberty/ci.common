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

import java.util.List;

import org.jline.reader.EOFError;
import org.jline.reader.CompletingParsedLine;
import org.jline.reader.Parser;

public class MultiLineParser implements Parser {
    public CompletingParsedLine parse(String line, int cursor, Parser.ParseContext context) {
        switch (context) {
            case ACCEPT_LINE:
                List<String> lines = line.lines().toList();
                if (!lines.isEmpty() && lines.get(0).matches("@ai\\s*\\[.*") && !lines.get(lines.size()-1).matches("@ai\\s*\\]\\s*")) {
                    throw new EOFError(-1, -1, "incomplete multi-line message");
                }
        }
        return new CompletingParsedLine() {
            public String word() { return line; }
            public int wordCursor() { return cursor; }
            public int wordIndex() { return 0; }
            public List<String> words() { return List.of(line); }
            public String line() { return line; }
            public int cursor() { return cursor; }
            public CharSequence escape(CharSequence candidate, boolean complete) { return candidate; }
            public int rawWordCursor() { return cursor; }
            public int rawWordLength() { return line.length(); }
        };
    }
}

