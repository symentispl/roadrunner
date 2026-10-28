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
package io.roadrunner.samplers.spi.cli;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a command line option of a sampler, bound to the annotated field of a
 * {@link io.roadrunner.samplers.spi.SamplerOptions} class.
 * <p>
 * The field's initial value is the option's default. {@code ${DEFAULT-VALUE}} in the description is replaced
 * with that default when printing usage.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SamplerOption {

    /** Option names, e.g. {@code {"-m", "--method"}}. */
    String[] names();

    String description() default "";

    /** Value to use when the option is not given, in the same textual form as on the command line. */
    String defaultValue() default "";

    boolean required() default false;

    /** Converts the textual value; by default the converter is chosen from the field's type. */
    Class<? extends OptionConverter<?>> converter() default DefaultConverter.class;

    /** Marker meaning "no custom converter". */
    abstract class DefaultConverter implements OptionConverter<Object> {
        private DefaultConverter() {}
    }
}
