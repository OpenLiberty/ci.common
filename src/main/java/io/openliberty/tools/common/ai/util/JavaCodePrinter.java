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

import org.fusesource.jansi.Ansi;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaCodePrinter {

    private static final Set<String> JAVAKEY = new HashSet<>();

    public static String keywordJansiColor(String token, Ansi.Color color) {
        return ansi().fg(color)
                     .bold()
                     .a(token)
                     .reset()
                     .toString();
    }

    public static String jansiColor(String token, Ansi.Color color) {
        return ansi().fg(color)
                     .a(token)
                     .reset()
                     .toString();
    }

    public static String colorKeywords(String code) {

        final String[] javaKeyWords = {
                "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "break", "throws", "true", "false",
                "abstract", "assert", "boolean", "this", "throw", "byte", "case", "catch",
                "transient", "try", "void", "volatile", "while", "continue", "default",
                "char", "class", "const", "do", "double", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private",
                "else", "enum", "extends", "final", "finally", "float", "for",
                "null"
        };

        for (String word : javaKeyWords) {
            JAVAKEY.add(word);
        }
        String keywords = "\\b(" + String.join("|", JAVAKEY) + ")\\b";
        String blockComments = "\\/\\*(\\*(?!\\/)|[^*])*\\*\\/";
        String inlineComments = "//.*";
        String stringLiteral = "\"([^\"\\\\]|\\\\.)*\"";
        String patternFull = String.format(
                "(" + "?<COM>%s|%s" + ")" + "|" + "(" + "?<STR>%s" + ")" + "|" + "(" + "?<KEY>%s" + ")",
                blockComments, inlineComments, stringLiteral, keywords);
        Pattern combinedPattern = Pattern
                .compile(patternFull);

        Matcher patternMatch = combinedPattern.matcher(code);
        StringBuilder result = new StringBuilder();

        result.append(ansi().fgBright(Ansi.Color.BLACK).toString());
        while (patternMatch.find()) {
            String codeWithAnsi = "";
            if (patternMatch.group("COM") != null) {
                codeWithAnsi = jansiColor(patternMatch.group("COM"), Ansi.Color.GREEN);
            } else if (patternMatch.group("STR") != null) {
                codeWithAnsi = jansiColor(patternMatch.group("STR"), Ansi.Color.CYAN);
            } else if (patternMatch.group("KEY") != null) {
                codeWithAnsi = keywordJansiColor(patternMatch.group("KEY"), Ansi.Color.MAGENTA);
            }
            patternMatch.appendReplacement(result, Matcher.quoteReplacement(codeWithAnsi));
            result.append(ansi().bold().fgBright(Ansi.Color.BLACK).toString());
        }
        patternMatch.appendTail(result);
        result.append(ansi().reset().fg(Ansi.Color.DEFAULT).toString());

        return result.toString();
    }

}
