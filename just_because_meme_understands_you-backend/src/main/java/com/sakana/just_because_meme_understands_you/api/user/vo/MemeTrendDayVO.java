package com.sakana.just_because_meme_understands_you.api.user.vo;

import java.time.LocalDate;

public record MemeTrendDayVO(LocalDate date, long views, long likes, long comments, long score) {
}
