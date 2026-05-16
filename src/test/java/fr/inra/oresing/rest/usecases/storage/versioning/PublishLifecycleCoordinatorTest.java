package fr.inra.oresing.rest.usecases.storage.versioning;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du {@link PublishLifecycleCoordinator} - garantit que
 * la propagation cancel parent <-> child est symetrique et que les maps
 * restent synchronises lors du cycle de vie register / unregister /
 * releaseCancellation . Ces invariants sont critiques pour le fix
 * cancel-divergence ( workflow_log vs binaryfile.published ) .
 */
class PublishLifecycleCoordinatorTest {

    @Test
    void getRelatedWorkflows_standalone_returnsSelfOnly() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID cid = UUID.randomUUID();
        assertEquals(Set.of(cid), coordinator.getRelatedWorkflows(cid));
    }

    @Test
    void getRelatedWorkflows_fromParent_returnsParentAndChild() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);

        Set<UUID> related = coordinator.getRelatedWorkflows(parent);
        assertEquals(Set.of(parent, child), related);
    }

    @Test
    void getRelatedWorkflows_fromChild_returnsParentAndChild() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);

        // Critique : que l'utilisateur cancel le parent ou le child depuis
        // oa-live , la propagation atteint l'arbre complet . Sans cet
        // invariant , la divergence "workflow_log=CANCELLED vs binaryfile=
        // published" reapparait .
        Set<UUID> related = coordinator.getRelatedWorkflows(child);
        assertEquals(Set.of(parent, child), related);
    }

    @Test
    void getRelatedWorkflows_nullCid_returnsEmpty() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        assertTrue(coordinator.getRelatedWorkflows(null).isEmpty());
    }

    @Test
    void unregisterChildImport_cleansBothMaps() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);
        coordinator.unregisterChildImport(parent);

        // Apres cleanup : ni parent ni child ne doivent retourner l'arbre .
        assertEquals(Set.of(parent), coordinator.getRelatedWorkflows(parent));
        assertEquals(Set.of(child),  coordinator.getRelatedWorkflows(child));
    }

    @Test
    void releaseCancellation_onParent_cleansBothMaps() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);
        coordinator.markCancelled(parent);
        coordinator.releaseCancellation(parent);

        // Cleanup complet du flag + des 2 directions du mapping .
        assertFalse(coordinator.isCancelled(parent));
        assertEquals(Set.of(parent), coordinator.getRelatedWorkflows(parent));
        assertEquals(Set.of(child),  coordinator.getRelatedWorkflows(child));
    }

    @Test
    void markCancelled_thenIsCancelled_isTrue() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID cid = UUID.randomUUID();
        assertFalse(coordinator.isCancelled(cid));
        coordinator.markCancelled(cid);
        assertTrue(coordinator.isCancelled(cid));
    }

    @Test
    void registerChildImport_thenChildToParentResolution() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);

        // Si je cancel le parent : isCancelled(parent)=true , child NOT YET .
        coordinator.markCancelled(parent);
        assertTrue(coordinator.isCancelled(parent));
        assertFalse(coordinator.isCancelled(child));

        // Le caller ( DashboardService.cancelWorkflow ) doit iterer sur
        // getRelatedWorkflows pour propager . Verifie ici que l'arbre est
        // bien resoluble depuis l'un ou l'autre cid .
        assertEquals(Set.of(parent, child), coordinator.getRelatedWorkflows(parent));
        assertEquals(Set.of(parent, child), coordinator.getRelatedWorkflows(child));
    }

    // ----- isKnownChild ( facade pattern UI filter ) ----------------- //

    @Test
    void isKnownChild_unregistered_isFalse() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        assertFalse(coordinator.isKnownChild(UUID.randomUUID()));
    }

    @Test
    void isKnownChild_registeredChild_isTrue() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);
        assertTrue(coordinator.isKnownChild(child));
    }

    @Test
    void isKnownChild_parent_isFalse() {
        // Le parent n'est pas un child : il doit rester visible en UI .
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);
        assertFalse(coordinator.isKnownChild(parent));
    }

    @Test
    void isKnownChild_afterUnregister_isFalse() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);
        coordinator.unregisterChildImport(parent);
        // Apres cleanup le child n'est plus considere comme un child .
        // Permet a un workflow IMPORT standalone reutilisant le meme UUID
        // ( improbable mais theorique ) de rester visible .
        assertFalse(coordinator.isKnownChild(child));
    }

    @Test
    void isKnownChild_nullCid_isFalse() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        assertFalse(coordinator.isKnownChild(null));
    }

    // ----- getChildImport ( aggregation lookup ) --------------------- //

    @Test
    void getChildImport_returnsChildForKnownParent() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        coordinator.registerChildImport(parent, child);
        assertEquals(java.util.Optional.of(child), coordinator.getChildImport(parent));
    }

    @Test
    void getChildImport_emptyForStandalone() {
        PublishLifecycleCoordinator coordinator = new PublishLifecycleCoordinator();
        assertTrue(coordinator.getChildImport(UUID.randomUUID()).isEmpty());
    }
}
