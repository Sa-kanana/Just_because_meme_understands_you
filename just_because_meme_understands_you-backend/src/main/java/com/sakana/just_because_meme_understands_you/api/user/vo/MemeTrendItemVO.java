package com.sakana.just_because_meme_understands_you.api.user.vo;

import java.util.List;

public record MemeTrendItemVO(Long memeId, String name, String image, long views, long likes,
                              long comments, long score, List<String> tags) {
}
