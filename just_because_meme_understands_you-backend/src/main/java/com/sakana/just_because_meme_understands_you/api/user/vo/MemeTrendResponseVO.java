package com.sakana.just_because_meme_understands_you.api.user.vo;

import java.time.LocalDate;
import java.util.List;

public record MemeTrendResponseVO(int days, LocalDate startDate, LocalDate endDate,
                                 List<MemeTrendItemVO> topMemes, List<MemeTrendDayVO> daily,
                                 MemeTrendTopicVO weeklyTopic, boolean historyReady) {
}
