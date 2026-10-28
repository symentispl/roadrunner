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
package io.roadrunner.cli;

import io.roadrunner.samplers.spi.SamplerOptions;
import io.roadrunner.samplers.spi.cli.OptionConverter;
import io.roadrunner.samplers.spi.cli.SamplerOption;
import io.roadrunner.samplers.spi.cli.SamplerOptionGroup;
import io.roadrunner.samplers.spi.cli.SamplerParameter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.IGetter;
import picocli.CommandLine.Model.ISetter;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Turns the library independent annotations of {@link SamplerOptions} into a picocli {@link CommandSpec}.
 * This is the only place that knows how sampler options map onto picocli.
 */
final class SamplerOptionsSpecs {

    private SamplerOptionsSpecs() {}

    static CommandSpec create(SamplerOptions<?> options) {
        var spec = CommandSpec.wrapWithoutInspection(options);
        var description = options.commandDescription();
        if (!description.isEmpty()) {
            spec.usageMessage().description(description);
        }

        var root = new Holder(options, null);
        var positionalIndex = 0;
        for (var field : options.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(SamplerParameter.class)) {
                spec.addPositional(positional(root, field, positionalIndex++));
            } else if (field.isAnnotationPresent(SamplerOption.class)) {
                spec.addOption(option(root, field, true));
            } else if (field.isAnnotationPresent(SamplerOptionGroup.class)) {
                spec.addArgGroup(group(root, field));
            }
        }
        return spec;
    }

    private static ArgGroupSpec group(Holder parent, Field groupField) {
        open(groupField);
        var holder = new Holder(parent, groupField);
        var group = ArgGroupSpec.builder()
                .exclusive(groupField.getAnnotation(SamplerOptionGroup.class).exclusive())
                .multiplicity("0..1");
        List<OptionSpec> members = new ArrayList<>();
        for (var field : groupField.getType().getDeclaredFields()) {
            if (field.isAnnotationPresent(SamplerOption.class)) {
                members.add(option(holder, field, false));
            }
        }
        members.forEach(group::addArg);
        return group.build();
    }

    private static OptionSpec option(Holder holder, Field field, boolean hasInitialValue) {
        open(field);
        var annotation = field.getAnnotation(SamplerOption.class);
        var builder = OptionSpec.builder(annotation.names())
                .description(annotation.description())
                .paramLabel("<%s>".formatted(field.getName()))
                .required(annotation.required())
                .type(field.getType())
                .getter(getter(holder, field))
                .setter(setter(holder, field));
        if (!annotation.defaultValue().isEmpty()) {
            builder.defaultValue(annotation.defaultValue());
        }
        var converter = converter(annotation.converter());
        if (converter != null) {
            builder.converters(converter);
        }
        // options of a group live in an object that only exists once one of them is given,
        // there is nothing to reset to before that
        if (hasInitialValue) {
            builder.initialValue(holder.existing() == null ? null : read(holder.existing(), field));
            builder.hasInitialValue(true);
        } else {
            builder.hasInitialValue(false);
        }
        return builder.build();
    }

    private static PositionalParamSpec positional(Holder holder, Field field, int index) {
        open(field);
        var annotation = field.getAnnotation(SamplerParameter.class);
        var builder = PositionalParamSpec.builder()
                .paramLabel(annotation.paramLabel())
                .description(annotation.description())
                .index(String.valueOf(index))
                .type(field.getType())
                .getter(getter(holder, field))
                .setter(setter(holder, field))
                .initialValue(read(holder.existing(), field))
                .hasInitialValue(true);
        var converter = converter(annotation.converter());
        if (converter != null) {
            builder.converters(converter);
        }
        return builder.build();
    }

    private static IGetter getter(Holder holder, Field field) {
        return new IGetter() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T get() throws Exception {
                var target = holder.existing();
                return target == null ? null : (T) field.get(target);
            }
        };
    }

    private static ISetter setter(Holder holder, Field field) {
        return new ISetter() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T set(T value) throws Exception {
                var target = holder.getOrCreate();
                var previous = (T) field.get(target);
                field.set(target, value);
                return previous;
            }
        };
    }

    private static ITypeConverter<?> converter(Class<? extends OptionConverter<?>> type) {
        if (type == SamplerOption.DefaultConverter.class) {
            return null;
        }
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            OptionConverter<?> converter = constructor.newInstance();
            return converter::convert;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("cannot create option converter %s".formatted(type.getName()), e);
        }
    }

    private static Object read(Object target, Field field) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read option field %s".formatted(field), e);
        }
    }

    private static void open(Field field) {
        try {
            field.setAccessible(true);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "cannot access option field %s, its package has to be opened to io.roadrunner.cli".formatted(field),
                    e);
        }
    }

    /** The object whose fields are bound to options: the sampler options, or a group object created on demand. */
    private static final class Holder {
        private final Holder parent;
        private final Object root;
        private final Field field;

        Holder(Object root, Field field) {
            this.parent = null;
            this.root = root;
            this.field = field;
        }

        Holder(Holder parent, Field field) {
            this.parent = parent;
            this.root = null;
            this.field = field;
        }

        Object existing() {
            if (parent == null) {
                return root;
            }
            var owner = parent.existing();
            return owner == null ? null : read(owner, field);
        }

        Object getOrCreate() throws ReflectiveOperationException {
            if (parent == null) {
                return root;
            }
            var owner = parent.getOrCreate();
            var current = field.get(owner);
            if (current == null) {
                var constructor = field.getType().getDeclaredConstructor();
                constructor.setAccessible(true);
                current = constructor.newInstance();
                field.set(owner, current);
            }
            return current;
        }
    }
}
