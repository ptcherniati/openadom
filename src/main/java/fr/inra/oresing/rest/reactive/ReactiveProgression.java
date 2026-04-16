package fr.inra.oresing.rest.reactive;

import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

public class ReactiveProgression {
    public sealed interface Counter<C extends Counter> permits DefaultCounter {
        double progress();
    }

    public sealed interface Progression<P extends Progression> permits ChangeOrCreateApplicationProgression, GetApplicationProgression {

        FluxSink<ReactiveResult> fluxSink();

        <C extends Counter> C counter();

        <L extends ProgressionMessagesLabel> L progressionMessagesLabel();

        default void pushProgression() {
            fluxSink().next(new ReactiveTypeProgress(counter().progress()));
        }

        default P incrementAndPush(final DoubleUnaryOperator increment) {
            final double progress = increment.applyAsDouble(counter().progress());
            fluxSink().next(new ReactiveTypeProgress(progress));
            return newProgression(progress, fluxSink(), progressionMessagesLabel());
        }

        <L extends ProgressionMessagesLabel> P newProgression(double progress, FluxSink<ReactiveResult> reactiveResultFluxSink, L progressionMessagesLabel);

        default void complete() {
            fluxSink().next(new ReactiveTypeProgress(1D));
            fluxSink().complete();
        }

        default void pushMessage(final String subLabel, final Map<String, Object> params) {
            fluxSink().next(new ReactiveTypeInfo(progressionMessagesLabel().withSubLabel(subLabel).label(), params));
        }

        default <R> void pushResult(final R result) {
            fluxSink().next(new ReactiveTypeResult(result));
        }

        P withSubLabel(String viewCreation);

        P up();


        default void pushError(final ValidationError error) {
            fluxSink().next(new ReactiveTypeError(error));
        }

        default void pushError(final IOException e) {
            fluxSink().next(new ReactiveTypeError(e));
        }

        default void pushError(final ValidationCheckResult validationCheckResult) {
            record ValidationCheckResultError(List<ValidationCheckResult> validationCheckResults) {
                ValidationCheckResultError(final List<ValidationCheckResult> validationCheckResults) {
                    this.validationCheckResults = List.copyOf(validationCheckResults);
                }
            }
            fluxSink().next(
                    new ReactiveTypeError(
                            new ValidationCheckResultError(List.of(validationCheckResult))
                    )
            );
        }

        default void pushError(final Exception e) {
            fluxSink().next(
                    new ReactiveTypeError(e)
            );
        }

        default void pushError(final ConfigurationException configurationException) {
            pushError(new ValidationError(configurationException, null));
        }

        default void pushError(final ConfigurationException configurationException, final Map<String, Object> params) {
            pushError(new ValidationError(configurationException, params));
        }
    }

    public sealed interface ChangeOrCreateApplicationProgression<P extends ChangeOrCreateApplicationProgression> extends Progression<P> permits ChangeApplicationProgression, CreateApplicationProgression {
    }

    public sealed interface ProgressionMessagesLabel permits ChangeApplicationProgressionMessagesLabel, CreateApplicationProgressionMessagesLabel, GetApplicationProgressionMessagesLabel {
        String COMPOSITION_LABEL = "%s.%s";

        String label();

        default <P extends ProgressionMessagesLabel> P withSubLabel(final String subLabel) {
            return newProgressionMessageLabel(COMPOSITION_LABEL.formatted(label(), subLabel));
        }

        <P extends ProgressionMessagesLabel> P newProgressionMessageLabel(String formatted);

        default <P extends ProgressionMessagesLabel> P up() {
            return newProgressionMessageLabel(label().replaceAll("\\.[^\\.]*", ""));
        }
    }

    public record DefaultCounter(double progress) implements Counter {
        public DefaultCounter {
            if (progress < 0 || progress > 100) {
                throw new IllegalArgumentException("progress is between 0 and 100");
            }
        }
    }

    public record GetApplicationProgression(Counter counter,
                                            FluxSink<ReactiveResult> fluxSink,
                                            GetApplicationProgressionMessagesLabel progressionMessagesLabel) implements Progression<GetApplicationProgression> {
        public GetApplicationProgression(final double counter, final FluxSink<ReactiveResult> fluxSink) {
            this(new DefaultCounter(counter), fluxSink, new GetApplicationProgressionMessagesLabel());
        }

        public GetApplicationProgression(final double counter, final FluxSink<ReactiveResult> fluxSink, final GetApplicationProgressionMessagesLabel applicationProgressionMessagesLabel) {
            this(new DefaultCounter(counter), fluxSink, applicationProgressionMessagesLabel);
        }

        @Override
        public <L extends ProgressionMessagesLabel> GetApplicationProgression newProgression(final double counter, final FluxSink<ReactiveResult> reactiveResultFluxSink, final L progressionMessagesLabel) {
            return new GetApplicationProgression(new DefaultCounter(counter), fluxSink, (GetApplicationProgressionMessagesLabel) progressionMessagesLabel);
        }

        @Override
        public GetApplicationProgression withSubLabel(final String viewCreation) {
            return new GetApplicationProgression(counter(), fluxSink(), progressionMessagesLabel().withSubLabel(viewCreation));
        }

        @Override
        public GetApplicationProgression up() {
            return new GetApplicationProgression(counter(), fluxSink(), progressionMessagesLabel().up());
        }
    }

    public record CreateApplicationProgression(Counter counter,
                                               FluxSink<ReactiveResult> fluxSink,
                                               CreateApplicationProgressionMessagesLabel progressionMessagesLabel) implements ChangeOrCreateApplicationProgression<CreateApplicationProgression> {
        /**
         * the % of job when configuration is read and befor create views.
         */
        public static final DefaultCounter PROGRESSION_FOR_READING_CONFIGURATION = new DefaultCounter(.5F);

        public CreateApplicationProgression(final double counter, final FluxSink<ReactiveResult> fluxSink) {
            this(new DefaultCounter(counter), fluxSink, new CreateApplicationProgressionMessagesLabel());
        }

        @Override
        public <L extends ProgressionMessagesLabel> CreateApplicationProgression newProgression(final double progress, final FluxSink<ReactiveResult> reactiveResultFluxSink, final L progressionMessagesLabel) {
            return new CreateApplicationProgression(new DefaultCounter(progress), fluxSink, (CreateApplicationProgressionMessagesLabel) progressionMessagesLabel);
        }

        public CreateApplicationProgression withSubLabel(final String viewCreation) {
            return new CreateApplicationProgression(counter(), fluxSink(), progressionMessagesLabel().withSubLabel(viewCreation));
        }

        public CreateApplicationProgression up() {
            return new CreateApplicationProgression(counter(), fluxSink(), progressionMessagesLabel().up());
        }
    }

    public record ChangeApplicationProgression(Counter counter,
                                               FluxSink<ReactiveResult> fluxSink,
                                               ChangeApplicationProgressionMessagesLabel progressionMessagesLabel) implements ChangeOrCreateApplicationProgression<ChangeApplicationProgression> {
        /**
         * the % of job when configuration is read and befor create views.
         */
        public static final DefaultCounter PROGRESSION_FOR_READING_CONFIGURATION = new DefaultCounter(.5D);

        public ChangeApplicationProgression(final double counter, final FluxSink<ReactiveResult> fluxSink) {
            this(new DefaultCounter(counter), fluxSink, new ChangeApplicationProgressionMessagesLabel());
        }

        @Override
        public <L extends ProgressionMessagesLabel> ChangeApplicationProgression newProgression(final double progress, final FluxSink<ReactiveResult> reactiveResultFluxSink, final L progressionMessagesLabel) {
            return new ChangeApplicationProgression(new DefaultCounter(progress), fluxSink, (ChangeApplicationProgressionMessagesLabel) progressionMessagesLabel);

        }

        @Override
        public ChangeApplicationProgression withSubLabel(final String viewCreation) {
            return new ChangeApplicationProgression(counter(), fluxSink(), progressionMessagesLabel().withSubLabel(viewCreation));
        }

        @Override
        public ChangeApplicationProgression up() {
            return new ChangeApplicationProgression(counter(), fluxSink(), progressionMessagesLabel().up());
        }
    }

    public record CreateApplicationProgressionMessagesLabel(String label) implements ProgressionMessagesLabel {

        public CreateApplicationProgressionMessagesLabel() {
            this("application.createConfiguration");
        }

        @Override
        public <P extends ProgressionMessagesLabel> P newProgressionMessageLabel(final String label) {
            return (P) new CreateApplicationProgressionMessagesLabel(label);
        }
    }

    public record ChangeApplicationProgressionMessagesLabel(String label) implements ProgressionMessagesLabel {

        public ChangeApplicationProgressionMessagesLabel() {
            this("application.ChangeConfiguration");
        }

        @Override
        public <P extends ProgressionMessagesLabel> P newProgressionMessageLabel(final String label) {
            return (P) new ChangeApplicationProgressionMessagesLabel(label);
        }
    }

    public record GetApplicationProgressionMessagesLabel(String label) implements ProgressionMessagesLabel {

        public GetApplicationProgressionMessagesLabel() {
            this("application.getApplication");
        }

        @Override
        public <P extends ProgressionMessagesLabel> P newProgressionMessageLabel(final String label) {
            return (P) new CreateApplicationProgressionMessagesLabel(label);
        }
    }
}