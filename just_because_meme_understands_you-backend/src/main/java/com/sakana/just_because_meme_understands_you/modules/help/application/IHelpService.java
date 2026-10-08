package com.sakana.just_because_meme_understands_you.modules.help.application;

import com.sakana.just_because_meme_understands_you.api.user.vo.HelpDocVO;

/**
 * 帮助中心文档。
 */
public interface IHelpService {

    /**
     * 获取帮助文档（Markdown）。
     */
    HelpDocVO getDoc();
}
