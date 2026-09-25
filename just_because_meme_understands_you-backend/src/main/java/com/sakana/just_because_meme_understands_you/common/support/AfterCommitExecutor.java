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
        // 判断 1：如果没有激活同步器，或者当前没有处于实际的数据库事务中
        // （例如当前方法没有加 @Transactional，或者已经处于 afterCommit 回调内部）
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            // 条件满足：说明不需要/无法等待事务提交，直接同步运行任务！
            task.run();
            return;
        }
        // 判断 2：当前正处于一个有效的数据库事务中
        // 注册一个事务同步回调监听器
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 只有当事务真正成功 Commit 后，Spring 会自动回调这里，执行我们的任务
                task.run();
            }
        });
    }
}
