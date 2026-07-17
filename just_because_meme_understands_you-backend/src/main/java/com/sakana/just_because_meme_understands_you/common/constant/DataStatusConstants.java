package com.sakana.just_because_meme_understands_you.common.constant;

/**
 * Soft-delete flags for data rows.
 *
 * <p>NOT_DELETED=0 only means "not soft-deleted". It is NOT the same as
 * business "active/normal = 1" used by user/meme status enums.</p>
 */
public final class DataStatusConstants {

    public static final int NOT_DELETED = 0;
    public static final int DELETED = 1;

    private DataStatusConstants() {
    }
}
