package com.sakana.just_because_meme_understands_you.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub OAuth 回调后的一次性 ticket 与回跳路径。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubOAuthTicketVO {

    private String ticket;

    private String redirectPath;
}
