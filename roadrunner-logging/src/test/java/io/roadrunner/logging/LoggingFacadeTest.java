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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.helpers.MessageFormatter;

class LoggingFacadeTest {

    private final RecordingLogger recording = new RecordingLogger();
    private final org.slf4j.Logger logger = new LoggingFacade.MessageOnlyLogger(recording);

    @Test
    void logsThrowableMessageInsteadOfStackTrace() {
        logger.error("sampler {} failed", "jdbc", new IllegalStateException("boom"));

        assertThat(recording.throwable).isNull();
        assertThat(recording.message).isEqualTo("sampler jdbc failed: java.lang.IllegalStateException: boom");
    }

    @Test
    void logsThrowablePassedAsOnlyArgument() {
        logger.warn("connect failed", new IllegalStateException("boom"));

        assertThat(recording.throwable).isNull();
        assertThat(recording.message).isEqualTo("connect failed: java.lang.IllegalStateException: boom");
    }

    // runs last on purpose, disabling is a one way switch
    @Test
    void returnsPlainLoggerOnceThrowablesAreNotOmitted() {
        assertThat(LoggingFacade.getLogger(getClass())).isInstanceOf(LoggingFacade.MessageOnlyLogger.class);

        LoggingFacade.retainThrowables();

        assertThat(LoggingFacade.getLogger(getClass())).isNotInstanceOf(LoggingFacade.MessageOnlyLogger.class);
    }

    @Test
    void leavesThrowableFreeCallsAlone() {
        logger.info("running {} of {}", 1, 2);

        assertThat(recording.throwable).isNull();
        assertThat(recording.message).isEqualTo("running 1 of 2");
    }

    private static final class RecordingLogger extends AbstractLogger {
        private String message;
        private Throwable throwable;

        @Override
        protected void handleNormalizedLoggingCall(
                Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
            this.message = MessageFormatter.basicArrayFormat(messagePattern, arguments);
            this.throwable = throwable;
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return null;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return true;
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return true;
        }
    }
}
