package fr.inra.oresing.rest.reactive;

import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.model.configuration.ValidationError;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

public class ReactiveEventHelper {

    private final Consumer<ReactiveResult> sink;
    private double currentProgress = 0.0;
    private final String baseLabel;

    public ReactiveEventHelper(Consumer<ReactiveResult> sink) {
        this(sink, "");
    }

    public ReactiveEventHelper(Consumer<ReactiveResult> sink, String baseLabel) {
        this.sink = sink;
        this.baseLabel = baseLabel;
    }

    public ReactiveEventHelper withSubLabel(String subLabel) {
        String newLabel = baseLabel.isEmpty() ? subLabel : baseLabel + "." + subLabel;
        ReactiveEventHelper newHelper = new ReactiveEventHelper(sink, newLabel);
        newHelper.currentProgress = this.currentProgress;
        return newHelper;
    }
    
    public ReactiveEventHelper up() {
        int lastDotIndex = baseLabel.lastIndexOf('.');
        String newLabel = (lastDotIndex == -1) ? "" : baseLabel.substring(0, lastDotIndex);
        ReactiveEventHelper newHelper = new ReactiveEventHelper(sink, newLabel);
        newHelper.currentProgress = this.currentProgress;
        return newHelper;
    }

    public void pushProgress(double progress) {
        this.currentProgress = progress;
        sink.accept(new ReactiveTypeProgress(progress));
    }

    public void incrementAndPush(DoubleUnaryOperator increment) {
        this.currentProgress = increment.applyAsDouble(this.currentProgress);
        sink.accept(new ReactiveTypeProgress(this.currentProgress));
    }

    public void pushMessage(String subLabel, Map<String, String> params) {
        String fullLabel = baseLabel.isEmpty() ? subLabel : baseLabel + "." + subLabel;
        sink.accept(new ReactiveTypeInfo(fullLabel, params));
    }
    
    public void pushMessage(String subLabel) {
        pushMessage(subLabel, Map.of());
    }

    public <R> void pushResult(R result) {
        sink.accept(new ReactiveTypeResult(result));
    }

    public void pushError(ValidationError error) {
        sink.accept(new ReactiveTypeError(error));
    }

    public void pushError(IOException e) {
        sink.accept(new ReactiveTypeError(e));
    }

    public void pushError(Exception e) {
        sink.accept(new ReactiveTypeError(e));
    }

    public void pushError(ValidationCheckResult validationCheckResult) {
        record ValidationCheckResultError(List<ValidationCheckResult> validationCheckResults) {
            ValidationCheckResultError(final List<ValidationCheckResult> validationCheckResults) {
                this.validationCheckResults = List.copyOf(validationCheckResults);
            }
        }
        sink.accept(
                new ReactiveTypeError(
                        new ValidationCheckResultError(List.of(validationCheckResult))
                )
        );
    }

    public void pushError(ConfigurationException configurationException) {
        pushError(new ValidationError(configurationException, null));
    }

    public void pushError(ConfigurationException configurationException, Map<String, Object> params) {
        pushError(new ValidationError(configurationException, params));
    }
    
    public void complete() {
        pushProgress(1.0);
        // Note: We don't call sink.complete() here because the Consumer might be a simple wrapper around next()
        // The completion should be handled by the caller who owns the FluxSink or via a specific signal if needed.
        // However, based on previous code, complete() was sending progress 1.0.
    }
    
    public double getProgress() {
        return currentProgress;
    }
}