package org.aitu.se.facade;

/**
 * Progress of the orchestrated order chain, persisted by the facade.
 *
 * <p>{@link #STARTED} is written <em>before</em> step A, so an interruption between "order id
 * created" and "stock reserved" is still visible to recovery.
 */
public enum ChainState {
    STARTED,          // correlation id created, step A not confirmed yet
    PENDING_B,        // step A committed, waiting for the kitchen
    PENDING_C,        // step B committed, waiting for the checkout
    COMPLETED,        // every step committed
    COMPENSATED_AT_A, // rolled back inside step A
    COMPENSATED_AT_B, // rolled back at step B (step A undone)
    COMPENSATED_AT_C, // rolled back at step C (steps B and A undone)
    REJECTED          // aborted before anything was committed
}
