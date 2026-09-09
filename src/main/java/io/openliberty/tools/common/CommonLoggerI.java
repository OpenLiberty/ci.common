/**
 * (C) Copyright IBM Corporation 2019.
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
package io.openliberty.tools.common;

public interface CommonLoggerI {

    /**
     * Returns a no-op {@code CommonLoggerI} that silently discards all log calls.
     * Useful when a logger instance is required by an API but no output is desired.
     */
    static CommonLoggerI noop() {
        return NoopLogger.INSTANCE;
    }

    /** Singleton backing {@link #noop()}. */
    final class NoopLogger implements CommonLoggerI {
        static final NoopLogger INSTANCE = new NoopLogger();
        private NoopLogger() {}
        @Override public void debug(String msg)              {}
        @Override public void debug(String msg, Throwable e) {}
        @Override public void debug(Throwable e)             {}
        @Override public void warn(String msg)               {}
        @Override public void info(String msg)               {}
        @Override public void error(String msg)              {}
        @Override public boolean isDebugEnabled()            { return false; }
    }

    /**
     * Log debug
     * 
     * @param msg
     */
    public abstract void debug(String msg);

    /**
     * Log debug
     * 
     * @param msg
     * @param e
     */
    public abstract void debug(String msg, Throwable e);

    /**
     * Log debug
     * 
     * @param e
     */
    public abstract void debug(Throwable e);

    /**
     * Log warning
     * 
     * @param msg
     */
    public abstract void warn(String msg);

    /**
     * Log info
     * 
     * @param msg
     */
    public abstract void info(String msg);

    /**
     * Log error
     * 
     * @param msg
     */
    public abstract void error(String msg);

    /**
     * Returns whether debug is enabled by the current logger
     * 
     * @return whether debug is enabled
     */
    public abstract boolean isDebugEnabled();

}