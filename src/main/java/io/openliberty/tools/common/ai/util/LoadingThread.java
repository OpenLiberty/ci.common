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

import java.time.Duration;
import java.time.Instant;

public class LoadingThread extends Thread {
    private static final String[] sequence = new String[] {"-", "\\", "|", "/"};

    private static Thread current = null;
    private static Instant start = null;
    private static Duration elapsed = Duration.ZERO;

    public static void resetElapsed() {
        elapsed = Duration.ZERO;
    }

    public static void show() {
        if (current != null)
            return;
        start = Instant.now().minus(elapsed);
        current = new LoadingThread();
        current.start();
    }

    public static void hide() {
        if (current == null)
            return;
        elapsed = Duration.between(start, Instant.now()).withNanos(0);
        current.interrupt();
        try {
            current.join();
        } catch (InterruptedException e) {
            // Nothing much we can do...
        }
        current = null;
    }

    public void run() {
        while (true) {
            try {
                for (String symbol : sequence) {
                    Thread.sleep(500);
                    System.out.print("\r" + symbol + " thinking... ("
                        + Duration.between(start, Instant.now()).toSeconds()
                        + " seconds)");
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.print("\r                                        \r");
    }
}
