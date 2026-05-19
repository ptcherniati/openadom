package fr.inra.oresing.domain.cancel;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Token observe pour annuler une operation longue ( publish, unpublish,
 * import cascade ) sans coupler les couches metier au coordinator du
 * workflow . Le {@link #isCancelled()} delegue a un {@link BooleanSupplier}
 * inject par le caller ( typiquement
 * {@code () -> publishLifecycleCoordinator.isCancelled(parentCid)} ) .
 *
 * <p><b>Pourquoi un type domain dedie</b> :
 * <ul>
 *   <li>{@code Supplier<Boolean>} brut est opaque ( pas de naming , box ) ;</li>
 *   <li>permet de centraliser {@link #throwIfCancelled} ( 1 point de
 *       generation de {@link CancellationException} ) ;</li>
 *   <li>{@link #NONE} sentinel pour les call-sites sans cancel ( pas de
 *       null check ) ;</li>
 *   <li>testable : {@code CancellationToken.of(() -> true)} dans un test
 *       suffit a verifier qu'un checkpoint coupe l'execution .</li>
 * </ul>
 *
 * <p><b>Immutable + thread-safe</b> : le supplier sous-jacent doit l'etre
 * ( typiquement un lambda lisant un {@code volatile} ou un
 * {@code ConcurrentMap.contains} ) .
 *
 * @author R.YAHIAOUI
 */
public final class CancellationToken {

    public static final CancellationToken NONE = new CancellationToken(() -> false);

    private final BooleanSupplier check;

    private CancellationToken(BooleanSupplier check) {
        this.check = Objects.requireNonNull(check, "check");
    }

    public static CancellationToken of(BooleanSupplier check) {
        return new CancellationToken(check);
    }

    public boolean isCancelled() {
        return check.getAsBoolean();
    }

    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("Operation cancelled by user");
        }
    }

    public void throwIfCancelled(String stage) {
        if (isCancelled()) {
            throw new CancellationException("Operation cancelled by user at stage : " + stage);
        }
    }
}
