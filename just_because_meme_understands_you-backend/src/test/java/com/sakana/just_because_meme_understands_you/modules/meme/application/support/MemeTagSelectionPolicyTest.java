package com.sakana.just_because_meme_understands_you.modules.meme.application.support;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 中文注释：验证主标签与普通标签的数量、互斥和存在性约束。 */
class MemeTagSelectionPolicyTest {

    @Test
    void acceptsOptionalTagsAndKeepsPrimarySeparateFromSecondaryTags() {
        MemeTagSelectionPolicy.Selection empty = MemeTagSelectionPolicy.normalizeAndValidate(
                null, null, Set.of());
        assertNull(empty.primaryTagId());
        assertEquals(List.of(), empty.secondaryTagIds());
        assertEquals(List.of(), empty.allTagIds());

        MemeTagSelectionPolicy.Selection secondaryOnly = MemeTagSelectionPolicy.normalizeAndValidate(
                null, List.of(2), Set.of(2));
        assertNull(secondaryOnly.primaryTagId());
        assertEquals(List.of(2), secondaryOnly.secondaryTagIds());
        assertEquals(List.of(2), secondaryOnly.allTagIds());

        MemeTagSelectionPolicy.Selection primaryOnly = MemeTagSelectionPolicy.normalizeAndValidate(
                1, List.of(), Set.of(1));
        assertEquals(1, primaryOnly.primaryTagId());
        assertEquals(List.of(), primaryOnly.secondaryTagIds());
        assertEquals(List.of(1), primaryOnly.allTagIds());
    }

    @Test
    void acceptsAtMostSevenDistinctSecondaryTags() {
        MemeTagSelectionPolicy.Selection selection = MemeTagSelectionPolicy.normalizeAndValidate(
                1, List.of(2, 3, 4, 5, 6, 7, 8), Set.of(1, 2, 3, 4, 5, 6, 7, 8));

        assertEquals(1, selection.primaryTagId());
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8), selection.secondaryTagIds());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), selection.allTagIds());
    }

    @Test
    void rejectsMoreThanSevenSecondaryTags() {
        assertThrows(BizException.class, () -> MemeTagSelectionPolicy.normalizeAndValidate(
                1, List.of(2, 3, 4, 5, 6, 7, 8, 9), Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
    }

    @Test
    void rejectsMissingRepeatedOrUnknownTagIds() {
        assertThrows(BizException.class, () -> MemeTagSelectionPolicy.normalizeAndValidate(
                0, List.of(), Set.of()));
        assertThrows(BizException.class, () -> MemeTagSelectionPolicy.normalizeAndValidate(
                2, List.of(2, 3, 4), Set.of(2, 3, 4)));
        assertThrows(BizException.class, () -> MemeTagSelectionPolicy.normalizeAndValidate(
                1, List.of(2, 2, 3), Set.of(1, 2, 3)));
        assertThrows(BizException.class, () -> MemeTagSelectionPolicy.normalizeAndValidate(
                1, List.of(2, 3, 4), Set.of(1, 2, 3)));
    }
}
