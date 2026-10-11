package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import org.springframework.stereotype.Component;

/** 梗图模块事件端口的事务发件箱适配器。 */
@Component
public class MemeIndexOutboxAdapter implements MemeIndexEventPort {

    static final String UPSERT_EVENT = "MEME_INDEX_UPSERT";
    static final String REMOVE_EVENT = "MEME_INDEX_REMOVE";

    private final OutboxEventWriter writer;

    public MemeIndexOutboxAdapter(OutboxEventWriter writer) {
        this.writer = writer;
    }

    @Override
    public void requestUpsert(long memeId) {
        writer.appendMemeIndexEvent(memeId, UPSERT_EVENT);
    }

    @Override
    public void requestRemoval(long memeId) {
        writer.appendMemeIndexEvent(memeId, REMOVE_EVENT);
    }
}
