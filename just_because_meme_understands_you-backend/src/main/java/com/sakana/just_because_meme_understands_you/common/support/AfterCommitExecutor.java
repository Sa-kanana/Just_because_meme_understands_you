package com.sakana.just_because_meme_understands_you.common.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Run side effects after the current transaction commits.
 *
 * <p>If there is no active transaction, or this is already invoked from an
 * {@code afterCommit} callback (synchronization still active but the transaction
 * has ended), run immediately. This avoids nested afterCommit registrations that
 * would never fire.</p>
 */
@Component
public class AfterCommitExecutor {

    public void execute(Runnable task) {
        if (task == null) {
            return;
        }
        // No sync, or already past commit (nested call from afterCommit): run now.
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
