package com.sakana.just_because_meme_understands_you.service.help;

import com.sakana.just_because_meme_understands_you.vo.HelpDocVO;

/**
 * 帮助中心文档。
 */
public interface IHelpService {

    /**
     * 获取帮助文档（Markdown）。
     */
    HelpDocVO getDoc();
}
