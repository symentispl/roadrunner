/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.logging;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.MessageFormatter;

/**
 * Loggers that, by default, log a throwable as a one line message instead of a stack trace.
 * Call {@link #disableOmitThrowables()} before any logger is created to get plain slf4j loggers back.
 */
public final class LoggingFacade {

    private static volatile boolean omitThrowables = true;

    private LoggingFacade() {}

    public static void disableOmitThrowables() {
        omitThrowables = false;
    }

    public static Logger getLogger(Class<?> clazz) {
        var logger = LoggerFactory.getLogger(clazz);
        return omitThrowables ? new MessageOnlyLogger(logger) : logger;
    }

    // package private for tests
    static final class MessageOnlyLogger extends AbstractLogger {

        private final transient Logger delegate;

        MessageOnlyLogger(Logger delegate) {
            this.delegate = delegate;
            this.name = delegate.getName();
        }

        @Override
        protected void handleNormalizedLoggingCall(
                Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
            var args = arguments == null ? new Object[0] : arguments;
            if (throwable == null) {
                // a throwable passed as the single format argument isn't normalized by AbstractLogger, but
                // MessageFormatter would still print its stack trace, so route it through the same decision
                throwable = MessageFormatter.getThrowableCandidate(args);
                if (throwable != null) {
                    args = MessageFormatter.trimmedCopy(args);
                }
            }

            var event = delegate.atLevel(level);
            if (marker != null) {
                event = event.addMarker(marker);
            }
            if (throwable == null) {
                event.log(messagePattern, args);
            } else {
                var withThrowable = Arrays.copyOf(args, args.length + 1);
                withThrowable[args.length] = throwable.toString();
                event.log(messagePattern + ": {}", withThrowable);
            }
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return LoggingFacade.class.getName();
        }

        @Override
        public boolean isTraceEnabled() {
            return delegate.isTraceEnabled();
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return delegate.isTraceEnabled(marker);
        }

        @Override
        public boolean isDebugEnabled() {
            return delegate.isDebugEnabled();
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return delegate.isDebugEnabled(marker);
        }

        @Override
        public boolean isInfoEnabled() {
            return delegate.isInfoEnabled();
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return delegate.isInfoEnabled(marker);
        }

        @Override
        public boolean isWarnEnabled() {
            return delegate.isWarnEnabled();
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return delegate.isWarnEnabled(marker);
        }

        @Override
        public boolean isErrorEnabled() {
            return delegate.isErrorEnabled();
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return delegate.isErrorEnabled(marker);
        }
    }
}
