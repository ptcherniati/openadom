package fr.inra.oresing.rest.reactive;

import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

/**
 * Aide a la publication d'evenements reactifs ( progress / info / error /
 * result ) sur le flux NDJSON consomme par le frontend .
 *
 * <h2>Robustesse de l'emission ( Task #307 )</h2>
 *
 * <p>Les emissions vers le sink Reactor sont best-effort : si le
 * subscriber a deja disparu ( {@code Flux} annule , client HTTP
 * deconnecte , pool heavy sature au moment du premier push ) ,
 * l'exception est avalee et logguee en {@code DEBUG} . La transaction
 * applicative continue son cours .
 *
 * <p>Avant ce comportement defensif , une emission echouee remontait
 * comme exception au caller ( ex. {@code createApplication} ) , qui
 * la convertissait en {@code OreSiTechnicalException} et faisait
 * echouer la creation . En CI sous charge , cela produisait des flakes
 * du type "Failed to send ReactiveTypeProgress[result=0.0]" alors que
 * le travail metier etait par ailleurs parfaitement valide .
 *
 * <p>Trade-off : on accepte de perdre des evenements UI ( progress ,
 * messages info ) quand le client est parti , plutot que de casser le
 * commit applicatif . Aucun effet sur l'integrite des donnees ; le
 * client peut toujours requeter l'etat final via l'API REST classique .
 *
 * @author R.YAHIAOUI
 */
public class ReactiveEventHelper {

    private static final Logger log = LoggerFactory.getLogger(ReactiveEventHelper.class);

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

    /**
     * Best-effort dispatch vers le sink . Toute exception levee par le
     * subscriber ( typiquement parce qu'il s'est cancellise ou ferme )
     * est avalee + logguee en DEBUG . Cf Javadoc de classe pour la
     * justification ( Task #307 ) .
     *
     * <p>Centralisation : tous les push* du helper passent par cette
     * methode . Ajouter un nouveau type d'evenement = 1 ligne
     * {@code safeEmit(new ReactiveTypeX(...))} sans avoir a repliquer
     * la logique try/catch ( DRY ) .
     */
    private void safeEmit(ReactiveResult event) {
        try {
            sink.accept(event);
        } catch (Throwable t) {
            log.debug("Reactive event dropped ({}) : subscriber unavailable - {}",
                    event.getClass().getSimpleName(), t.toString());
        }
    }

    public void pushProgress(double progress) {
        this.currentProgress = progress;
        safeEmit(new ReactiveTypeProgress(progress));
    }

    public void incrementAndPush(DoubleUnaryOperator increment) {
        this.currentProgress = increment.applyAsDouble(this.currentProgress);
        safeEmit(new ReactiveTypeProgress(this.currentProgress));
    }

    public void pushMessage(String subLabel, Map<String, String> params) {
        String fullLabel = baseLabel.isEmpty() ? subLabel : baseLabel + "." + subLabel;
        safeEmit(new ReactiveTypeInfo(fullLabel, params));
    }

    public void pushMessage(String subLabel) {
        pushMessage(subLabel, Map.of());
    }

    public <R> void pushResult(R result) {
        safeEmit(new ReactiveTypeResult(result));
    }

    public void pushError(ValidationError error) {
        safeEmit(new ReactiveTypeError(error));
    }

    public void pushError(IOException e) {
        safeEmit(new ReactiveTypeError(e));
    }

    public void pushError(Exception e) {
        safeEmit(new ReactiveTypeError(e));
    }

    public void pushError(ValidationCheckResult validationCheckResult) {
        record ValidationCheckResultError(List<ValidationCheckResult> validationCheckResults) {
            ValidationCheckResultError(final List<ValidationCheckResult> validationCheckResults) {
                this.validationCheckResults = List.copyOf(validationCheckResults);
            }
        }
        safeEmit(new ReactiveTypeError(
                new ValidationCheckResultError(List.of(validationCheckResult))
        ));
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
