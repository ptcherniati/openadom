package fr.inra.oresing.rest.reactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs du package {@code rest.reactive} — aucun contexte Spring.
 * Couvre : ReactiveType, ReactiveTypeError, ReactiveTypeInfo, ReactiveTypeProgress,
 *          ReactiveTypeResult, ReactiveResult, ReactiveEventHelper.
 */
@Tag("domain.model")
class ReactiveTypesTest {

    // ------------------------------------------------------------------ //
    //  ReactiveType                                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ReactiveType enum")
    class ReactiveTypeEnumTest {

        @Test
        void allValuesAccessible() {
            assertThat(ReactiveType.values()).containsExactlyInAnyOrder(
                    ReactiveType.REACTIVE_RESULT,
                    ReactiveType.REACTIVE_ERROR,
                    ReactiveType.REACTIVE_PROGRESS,
                    ReactiveType.REACTIVE_INFO
            );
        }

        @Test
        void valueOfWorks() {
            assertThat(ReactiveType.valueOf("REACTIVE_RESULT")).isEqualTo(ReactiveType.REACTIVE_RESULT);
            assertThat(ReactiveType.valueOf("REACTIVE_ERROR")).isEqualTo(ReactiveType.REACTIVE_ERROR);
            assertThat(ReactiveType.valueOf("REACTIVE_PROGRESS")).isEqualTo(ReactiveType.REACTIVE_PROGRESS);
            assertThat(ReactiveType.valueOf("REACTIVE_INFO")).isEqualTo(ReactiveType.REACTIVE_INFO);
        }
    }

    // ------------------------------------------------------------------ //
    //  ReactiveTypeResult                                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ReactiveTypeResult")
    class ReactiveTypeResultTest {

        @Test
        void constructorSetsResultAndType() {
            ReactiveTypeResult<String> r = new ReactiveTypeResult<>("hello");
            assertThat(r.result()).isEqualTo("hello");
            assertThat(r.type()).isEqualTo(ReactiveType.REACTIVE_RESULT);
            assertThat(r.time()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        void fullConstructorRoundtrip() {
            LocalDateTime ts = LocalDateTime.of(2024, 1, 1, 0, 0);
            ReactiveTypeResult<Integer> r = new ReactiveTypeResult<>(42, ts, ReactiveType.REACTIVE_RESULT);
            assertThat(r.result()).isEqualTo(42);
            assertThat(r.time()).isEqualTo(ts);
        }

        @Test
        void nullResultAllowed() {
            ReactiveTypeResult<String> r = new ReactiveTypeResult<>(null);
            assertThat(r.result()).isNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  ReactiveTypeProgress                                                //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ReactiveTypeProgress")
    class ReactiveTypeProgressTest {

        @Test
        void constructorSetsProgressAndType() {
            ReactiveTypeProgress<Double> p = new ReactiveTypeProgress<>(0.5);
            assertThat(p.result()).isEqualTo(0.5);
            assertThat(p.type()).isEqualTo(ReactiveType.REACTIVE_PROGRESS);
            assertThat(p.time()).isNotNull();
        }

        @Test
        void zeroProgressIsValid() {
            ReactiveTypeProgress<Double> p = new ReactiveTypeProgress<>(0.0);
            assertThat(p.result()).isEqualTo(0.0);
        }

        @Test
        void oneProgressIsValid() {
            ReactiveTypeProgress<Double> p = new ReactiveTypeProgress<>(1.0);
            assertThat(p.result()).isEqualTo(1.0);
        }
    }

    // ------------------------------------------------------------------ //
    //  ReactiveTypeInfo                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ReactiveTypeInfo")
    class ReactiveTypeInfoTest {

        @Test
        void constructorWithResultOnly() {
            ReactiveTypeInfo<String> info = new ReactiveTypeInfo<>("MANIFEST");
            assertThat(info.result()).isEqualTo("MANIFEST");
            assertThat(info.type()).isEqualTo(ReactiveType.REACTIVE_INFO);
            assertThat(info.params()).isEmpty();
            assertThat(info.time()).isNotNull();
        }

        @Test
        void constructorWithParams() {
            ReactiveTypeInfo<String> info = new ReactiveTypeInfo<>("LOADED_DATA", Map.of("count", "5"));
            assertThat(info.result()).isEqualTo("LOADED_DATA");
            assertThat(info.params()).containsEntry("count", "5");
        }

        @Test
        void fullConstructorRoundtrip() {
            LocalDateTime ts = LocalDateTime.of(2025, 6, 1, 12, 0);
            ReactiveTypeInfo<String> info = new ReactiveTypeInfo<>("MSG", Map.of(), ts, ReactiveType.REACTIVE_INFO);
            assertThat(info.time()).isEqualTo(ts);
        }
    }

    // ------------------------------------------------------------------ //
    //  ReactiveTypeError                                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ReactiveTypeError")
    class ReactiveTypeErrorTest {

        @Test
        void constructorWithStringResult() {
            ReactiveTypeError<String> e = new ReactiveTypeError<>("some error");
            assertThat(e.result()).isEqualTo("some error");
            assertThat(e.type()).isEqualTo(ReactiveType.REACTIVE_ERROR);
            // errorType = class simple name of the result
            assertThat(e.errorType()).isEqualTo("String");
            assertThat(e.time()).isNotNull();
        }

        @Test
        void constructorWithNullResult() {
            ReactiveTypeError<Object> e = new ReactiveTypeError<>((Object) null);
            assertThat(e.result()).isNull();
            assertThat(e.errorType()).isEqualTo("null");
        }

        @Test
        void constructorWithExceptionResult() {
            RuntimeException ex = new RuntimeException("boom");
            ReactiveTypeError<RuntimeException> e = new ReactiveTypeError<>(ex);
            assertThat(e.result()).isSameAs(ex);
            assertThat(e.errorType()).isEqualTo("RuntimeException");
        }

        @Test
        void fullConstructorRoundtrip() {
            LocalDateTime ts = LocalDateTime.of(2025, 1, 1, 0, 0);
            ReactiveTypeError<String> e = new ReactiveTypeError<>("t", "payload", ts, ReactiveType.REACTIVE_ERROR);
            assertThat(e.errorType()).isEqualTo("t");
            assertThat(e.result()).isEqualTo("payload");
            assertThat(e.time()).isEqualTo(ts);
        }
    }

    // ------------------------------------------------------------------ //
    //  ReactiveEventHelper                                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ReactiveEventHelper")
    class ReactiveEventHelperTest {

        /** Collecteur simple pour capturer les événements émis. */
        private List<ReactiveResult> capture() {
            return new ArrayList<>();
        }

        @Test
        void pushProgressEmitsProgressEvent() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushProgress(0.3);

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ReactiveTypeProgress.class);
            assertThat(((ReactiveTypeProgress<?>) events.get(0)).result()).isEqualTo(0.3);
            assertThat(helper.getProgress()).isEqualTo(0.3);
        }

        @Test
        void pushMessageEmitsInfoEvent() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushMessage("MANIFEST");

            assertThat(events).hasSize(1);
            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            assertThat(info.result()).isEqualTo("MANIFEST");
        }

        @Test
        void pushMessageWithBaseLabelPrefixes() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add, "import");
            helper.pushMessage("step1");

            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            assertThat(info.result()).isEqualTo("import.step1");
        }

        @Test
        void pushMessageWithParams() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushMessage("KEY", Map.of("a", "b"));

            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            // ReactiveTypeInfo déclare Map<String,String> où String est le type-param (shadowing) → cast nécessaire
            @SuppressWarnings("unchecked")
            java.util.Map<java.lang.String, java.lang.String> params = (java.util.Map<java.lang.String, java.lang.String>) info.params();
            assertThat(params).containsEntry("a", "b");
        }

        @Test
        void pushResultEmitsResultEvent() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushResult("myResult");

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ReactiveTypeResult.class);
            assertThat(((ReactiveTypeResult<?>) events.get(0)).result()).isEqualTo("myResult");
        }

        @Test
        void pushErrorWithIOException() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushError(new IOException("io failure"));

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ReactiveTypeError.class);
        }

        @Test
        void pushErrorWithGenericException() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushError(new IllegalStateException("illegal"));

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(ReactiveTypeError.class);
        }

        @Test
        void completeEmitsProgress1() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.complete();

            assertThat(helper.getProgress()).isEqualTo(1.0);
            assertThat(events).hasSize(1);
            assertThat(((ReactiveTypeProgress<?>) events.get(0)).result()).isEqualTo(1.0);
        }

        @Test
        void incrementAndPushUpdatesProgress() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushProgress(0.2);
            helper.incrementAndPush(p -> p + 0.1);

            assertThat(helper.getProgress()).isEqualTo(0.30000000000000004); // floating point
            assertThat(events).hasSize(2);
        }

        @Test
        void withSubLabelCreatesNewHelperWithCombinedLabel() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper parent = new ReactiveEventHelper(events::add, "root");
            ReactiveEventHelper child = parent.withSubLabel("child");
            child.pushMessage("event");

            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            assertThat(info.result()).isEqualTo("root.child.event");
        }

        @Test
        void withSubLabelOnEmptyBaseLabel() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            ReactiveEventHelper child = helper.withSubLabel("sub");
            child.pushMessage("msg");

            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            assertThat(info.result()).isEqualTo("sub.msg");
        }

        @Test
        void upRemovesLastLabel() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add, "a.b.c");
            ReactiveEventHelper parent = helper.up();
            parent.pushMessage("msg");

            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            assertThat(info.result()).isEqualTo("a.b.msg");
        }

        @Test
        void upOnRootLabelGivesEmptyLabel() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add, "root");
            ReactiveEventHelper parent = helper.up();
            parent.pushMessage("msg");

            ReactiveTypeInfo<?> info = (ReactiveTypeInfo<?>) events.get(0);
            assertThat(info.result()).isEqualTo("msg");
        }

        @Test
        void progressIsInheritedBySubLabel() {
            List<ReactiveResult> events = capture();
            ReactiveEventHelper helper = new ReactiveEventHelper(events::add);
            helper.pushProgress(0.5);
            ReactiveEventHelper child = helper.withSubLabel("x");
            assertThat(child.getProgress()).isEqualTo(0.5);
        }
    }
}