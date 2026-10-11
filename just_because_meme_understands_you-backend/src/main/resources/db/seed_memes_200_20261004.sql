-- 2026-10-04 基于公开网络资料整理的 200 条网络梗/流行表达种子数据
-- 本文件仅包含一条 INSERT 语句，可在 DataGrip 中将光标放在语句内后执行。
-- 词条是梗库候选集合，不是实时热度排名；词义会随平台和语境变化。
-- 按 meme.name 全局去重，重复执行时已有同名梗会跳过。
-- 图片未确认授权且未上传至项目存储，故 image 使用 NULL；互动数不伪造，初始化为 0。
-- 发布者优先选择状态正常的管理员；没有管理员时回退到最早创建的正常用户。
--
-- 来源（Firecrawl 检索并抓取；盘点文章只反映各自样本，不代表全网统计）：
-- 2026网络表达与用法：https://www.lingoace.com/zh/blog/internet-slang/
-- 2026上半年背手负鼠、哭哭马、AI水果、养龙虾等盘点：https://www.adquan.com/article/361636
-- 2025年度网络流行语/媒体热词：https://www.news.cn/book/20251212/83e4aed3aff145959bf5a06243280302/c.html
-- 中文网络流行语词典：https://studycli.org/zh-CN/learn-chinese/chinese-internet-slang/
-- 2026中文热梗盘点：https://www.wukongsch.com/blog/zh/chinese-popular-internet-slangs-post-59740/
--
-- 连接到 MySQL 后即可执行；表名已限定为 meme 数据库。
INSERT INTO `meme`.`meme`
    (`introduction`, `name`, `image`, `page_views`, `likes`, `comments`,
     `status`, `release_time`, `update_time`, `user_id`,
     `offline_at`, `offline_from_status`, `appeal_reject_count`,
     `offline_reason`, `purged_at`)
SELECT seed.`introduction`,
       seed.`name`,
       NULL,
       0,
       0,
       0,
       1,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       selected_user.`user_id`,
       NULL,
       NULL,
       0,
       NULL,
       NULL
FROM (
    SELECT '背手负鼠' AS `name`, '负鼠背爪站立的表情图，用来表现旁观、沉思或努力镇定。' AS `introduction`
    UNION ALL SELECT '哭哭马', '哭脸马玩偶的二创形象，用来表达委屈、疲惫或强颜欢笑。'
    UNION ALL SELECT '卡急马', '把焦急等待的状态套进马年谐音表达，常配马的表情图。'
    UNION ALL SELECT '愁马', '以愁眉苦脸的马形象表达烦恼、无语或心累。'
    UNION ALL SELECT '哭笑马', '把哭和笑放在同一张马脸上，形容遇事哭笑不得。'
    UNION ALL SELECT 'SBTI', '仿照MBTI制作的娱乐型人格测试，用夸张标签调侃自我认知。'
    UNION ALL SELECT '雪山救狐狸', '围绕雪山救援情境设置选择题或故事，引发网友讨论。'
    UNION ALL SELECT 'AI水果', '用AI生成水果拟人短剧，常以狗血剧情和反转吸引二创。'
    UNION ALL SELECT '养龙虾', '把安装、部署OpenClaw智能体戏称为养龙虾，源自其龙虾标志。'
    UNION ALL SELECT '嘉豪', '网络上对模仿特定网红或行为风格者的调侃称呼，含义依语境变化。'
    UNION ALL SELECT '少了尊重', '借综艺表达吐槽被忽视、评价不公或没有受到应有重视。'
    UNION ALL SELECT '我浪姐了', '把参加综艺后的疲惫与“我累竭了”关联，调侃精力耗尽。'
    UNION ALL SELECT '割四赔五', '用数字谐音调侃承诺落空、约定反悔或越做越亏。'
    UNION ALL SELECT '马上破防', '借马年语感表达情绪即将失守，也可形容刚开始就被戳中。'
    UNION ALL SELECT '含机量爆表', '形容某个场景里AI、机器或自动化内容占比特别高。'
    UNION ALL SELECT '卡牌大师', '借游戏角色名调侃善于抽卡、摸牌或关键时刻靠运气的人。'
    UNION ALL SELECT '昙花六现', '借赛事语境调侃短暂高光、表现起伏或意外发挥。'
    UNION ALL SELECT '我罩的', '用霸气口吻表示护着某人或某物，常见于朋友互相撑腰。'
    UNION ALL SELECT '牛来', '把“未来”等词改成带“牛”的祝福或玩笑表达。'
    UNION ALL SELECT '中国人能飞', '以夸张口吻表达对中国科技、航天或运动成绩的惊叹。'
    UNION ALL SELECT '外卖三国杀', '把多个外卖平台的优惠比价戏称为一场策略对局。'
    UNION ALL SELECT '数字游民', '指依靠网络远程工作、地点较自由的人，也常被用作理想生活自嘲。'
    UNION ALL SELECT '谷子', '源自英文goods的二次元周边称呼，常指角色周边商品。'
    UNION ALL SELECT '拉布布', 'Labubu玩偶及潮玩收藏引发的话题，也可泛指相关可爱潮玩。'
    UNION ALL SELECT '杭州六小龙', '杭州多家科技创业企业的合称，常用于讨论AI与科技产业。'
    UNION ALL SELECT '苏超', '江苏省城市足球联赛的简称，赛事带动城市梗和地域互动。'
    UNION ALL SELECT '票根经济', '用赛事、演出或展览票根带动消费的说法，也被网友用于玩梗。'
    UNION ALL SELECT '人形机器人', '机器人技术与赛事的热门话题，常用于讨论未来生活或模仿视频。'
    UNION ALL SELECT 'DeepSeek', '中国AI模型品牌名，网络上也指相关模型体验、生成内容和热议事件。'
    UNION ALL SELECT '野生狗奶', '网络表达之一，常用来形容意外刷到的可爱动物或治愈内容。'
    UNION ALL SELECT '在中国我们不说', '句式模板：把日常表达改成夸张、古风或本土化说法，制造反差。'
    UNION ALL SELECT '县域旅游', '围绕县城和小城旅行、探店与生活体验形成的讨论话题。'
    UNION ALL SELECT '村咖', '开在乡村或村落的咖啡店，常成为周末旅行和拍照目的地。'
    UNION ALL SELECT '韧性', '网络讨论中指遇到困难后恢复、调整并继续前进的能力。'
    UNION ALL SELECT '情绪价值', '交流中获得的理解、支持和愉悦感，也用于讨论关系体验。'
    UNION ALL SELECT '松弛感', '形容不紧绷、不刻意展示的状态，也可调侃刻意营造的从容。'
    UNION ALL SELECT 'AI味', '指文字或图片带有明显生成式AI风格，常用于要求表达更自然。'
    UNION ALL SELECT '人机感', '形容内容或互动过于机械、像机器生成，缺少自然表达。'
    UNION ALL SELECT '手搓', '形容不用复杂工具、亲自从头做出作品或程序，常用于展示动手能力。'
    UNION ALL SELECT '精神离职', '人在岗位上继续工作，心里却已不再投入或早已想离开。'
    UNION ALL SELECT '班味入脑', '形容休息时间仍被工作思维占据，做事也像在处理待办。'
    UNION ALL SELECT '空降疲惫', '临时接到突发任务或安排后产生的疲倦感。'
    UNION ALL SELECT '毛囊报警', '用夸张方式形容熬夜、压力或掉发带来的担忧。'
    UNION ALL SELECT '螺丝拧紧', '形容工作节奏加快、压力加重，像机器零件一样持续运转。'
    UNION ALL SELECT '摸鱼搭子', '一起短暂休息、闲聊或躲开工作压力的同事伙伴。'
    UNION ALL SELECT '自愿流浪', '暂时抛开固定日程去散步、旅行或发呆，表达想透气的心情。'
    UNION ALL SELECT '无效加班', '花了额外时间却没有实际产出，常用来吐槽低效忙碌。'
    UNION ALL SELECT '职场隐身', '尽量少参与办公室社交、保持低存在感以减少额外消耗。'
    UNION ALL SELECT '人生旷野', '表达人生选择不必只有一条标准路径，可以探索不同方向。'
    UNION ALL SELECT '小众且自由', '形容坚持个人偏好、不为迎合大众而改变的生活态度。'
    UNION ALL SELECT '允许一切发生', '提醒自己接纳暂时无法控制的变化，减少反复纠结。'
    UNION ALL SELECT '把日子过成诗', '用浪漫视角看待普通生活中的小事。'
    UNION ALL SELECT '我的身材很曼妙', '借荒诞答复躲开不想回答的问题，靠答非所问制造幽默。'
    UNION ALL SELECT '别太荒谬', '用简短语气吐槽离谱言行，也可表达难以置信。'
    UNION ALL SELECT '赛博丐帮', '戏称网上互相求资源、分享优惠或交换信息的群体。'
    UNION ALL SELECT '低成本快乐', '用很少花费获得满足，比如散步、追剧或吃喜欢的小吃。'
    UNION ALL SELECT '卡点人生', '调侃总在截止时间前才行动，靠最后时刻完成任务。'
    UNION ALL SELECT '反向许愿', '故意说出不希望发生的结果，期待现实反而朝好方向发展。'
    UNION ALL SELECT '电子木鱼', '手机上的敲木鱼工具或音效，用于虚拟祈福和缓解焦虑。'
    UNION ALL SELECT '功德+1', '借敲木鱼的仪式感表示做了好事、积累好运，也常作反讽。'
    UNION ALL SELECT '赛博烧香', '在线向虚拟神像祈愿，表达轻松求好运的心情。'
    UNION ALL SELECT '电子布洛芬', '把能安慰情绪、转移注意力的音乐、视频或游戏比作止痛药。'
    UNION ALL SELECT '悦己消费', '为了自己的喜好和体验消费，不以取悦别人为主要目标。'
    UNION ALL SELECT '社交电量', '把社交精力比作电池，表示还能聊天或已经需要独处充电。'
    UNION ALL SELECT '社交充电', '通过独处、休息或做喜欢的事恢复社交精力。'
    UNION ALL SELECT '社交耗电', '形容频繁交流或参加活动后感到精力被消耗。'
    UNION ALL SELECT '淡人', '自称情绪表达较平淡、对很多事不太激动的人。'
    UNION ALL SELECT '浓人', '自称情绪浓烈、表达直接、容易投入的人。'
    UNION ALL SELECT '钝感力', '形容不被小事轻易影响、能过滤无关评价的心理能力。'
    UNION ALL SELECT '拒绝内耗', '提醒自己停止反复自责和无效纠结，把注意力放回行动。'
    UNION ALL SELECT '内耗', '反复纠结、担忧或自我拉扯，导致精力被消耗。'
    UNION ALL SELECT '精神胜利法', '用积极想象或自我安慰暂时缓解现实挫折，也常带自嘲。'
    UNION ALL SELECT '自洽', '让想法、选择和行为保持一致，也可表示接受个人决定。'
    UNION ALL SELECT '与自己和解', '接受无法改变的过去或现状，减少长期责备自己。'
    UNION ALL SELECT '先爱自己', '提醒把个人健康、感受和边界放在重要位置。'
    UNION ALL SELECT '今天也要好好吃饭', '用日常关怀提醒自己或他人照顾基本生活。'
    UNION ALL SELECT '允许自己慢一点', '接受个人节奏不同，不因比较而过度焦虑。'
    UNION ALL SELECT '人生没有标准答案', '表达选择多样、无需照搬他人路径的观点。'
    UNION ALL SELECT '打工人', '对上班族的自嘲称呼，表达辛苦工作和生活压力。'
    UNION ALL SELECT '干饭人', '热爱吃饭的人，也用于表达准时吃饭、认真吃饭的态度。'
    UNION ALL SELECT '干饭魂', '形容对美食和吃饭的热情，常用于饭点或分享菜品。'
    UNION ALL SELECT '碳水快乐', '吃米面等碳水食物获得的满足感，也常用于美食分享。'
    UNION ALL SELECT '奶茶续命', '夸张表达靠奶茶缓解疲惫或获得情绪满足。'
    UNION ALL SELECT '外卖自由', '表示能够按喜好点外卖，也常用于对生活水平的自嘲。'
    UNION ALL SELECT '吃土', '形容花钱后手头拮据，通常是对消费的夸张说法。'
    UNION ALL SELECT '月光族', '形容收入基本用于当月开销、很难存下钱的人。'
    UNION ALL SELECT '工资到账即消失', '调侃收入发放后很快被房租、账单和日常支出花掉。'
    UNION ALL SELECT '月底吃土', '形容月底预算紧张，只能减少消费。'
    UNION ALL SELECT '报复性熬夜', '白天时间被工作学习占满后，晚上故意晚睡找回私人时间。'
    UNION ALL SELECT '熬夜冠军', '自嘲经常睡得很晚的人，不代表真实比赛或荣誉。'
    UNION ALL SELECT '早八人', '需要早上八点上课或工作的人，也常用于表达起床困难。'
    UNION ALL SELECT '起床困难户', '调侃早上难以离开床铺、容易赖床的状态。'
    UNION ALL SELECT '周一综合征', '周末结束后面对工作或学习时产生的低落和疲惫。'
    UNION ALL SELECT '假期综合征', '长假后重新适应工作学习节奏时出现的疲倦感。'
    UNION ALL SELECT '拖延症晚期', '夸张描述总把任务推到最后才开始的习惯。'
    UNION ALL SELECT 'ddl战士', '直到截止日期临近才集中完成任务的人。'
    UNION ALL SELECT '临时抱佛脚', '考试或任务临近时才匆忙准备。'
    UNION ALL SELECT '学不进去', '表示注意力难以集中或学习效率低下。'
    UNION ALL SELECT '考试周限定皮肤', '调侃考试期间出现的熬夜、焦虑和高强度学习状态。'
    UNION ALL SELECT '临考前突然开窍', '调侃考试临近时思路或记忆忽然变得活跃。'
    UNION ALL SELECT '考完就是胜利', '表达考试结束本身已经值得庆祝，不论结果如何。'
    UNION ALL SELECT '求职人精神状态', '围绕投简历、面试和等待回复产生的自嘲内容。'
    UNION ALL SELECT '简历已读不回', '投递简历后没有收到回应时的无奈表达。'
    UNION ALL SELECT '面试像开盲盒', '调侃面试问题和结果充满不确定性。'
    UNION ALL SELECT '面试官的灵魂拷问', '形容面试中令人难回答、直击经历或能力的问题。'
    UNION ALL SELECT '工牌一戴，班味就来', '调侃开始上班后整个人迅速进入工作状态。'
    UNION ALL SELECT '一个人干一个部门', '自嘲承担了多个岗位或过多职责。'
    UNION ALL SELECT '救火队员', '形容总被安排处理紧急问题、临时故障或危机的人。'
    UNION ALL SELECT '需求像雪花一样飞来', '形容工作要求不断增加、很难保持稳定范围。'
    UNION ALL SELECT '改到满意为止', '调侃修改没有明确终点，直到需求方满意才停止。'
    UNION ALL SELECT '文件名_final_final2', '调侃反复修改后文件版本名越来越长的工作习惯。'
    UNION ALL SELECT '最后的最后版本', '文件命名中不断追加“最终版”的常见职场笑点。'
    UNION ALL SELECT '办公室生存指南', '汇总职场沟通和避坑经验的内容，也常被当作戏谑标题。'
    UNION ALL SELECT '老板画的饼', '比喻管理者承诺的未来好处，实际兑现时间不确定。'
    UNION ALL SELECT '上班如上坟', '夸张形容上班时情绪低落、缺少活力。'
    UNION ALL SELECT '下班不回消息', '强调下班后的私人时间边界，常用于职场态度表达。'
    UNION ALL SELECT '下班快乐', '向结束工作的人表达祝福，也可作为打工人互相鼓励。'
    UNION ALL SELECT '开学综合征', '长假结束后重新适应学校作息产生的疲倦和抗拒。'
    UNION ALL SELECT '尊嘟假嘟', '把“真的假的”故意说成可爱化发音，表达惊讶或质疑。'
    UNION ALL SELECT '不是吧阿sir', '借影视台词表达震惊、无奈或对离谱情况的吐槽。'
    UNION ALL SELECT '这合理吗', '遇到不合常理的事情时提出反问，通常是表达惊讶。'
    UNION ALL SELECT '我不李姐', '把“不理解”改成谐音说法，轻松表达困惑。'
    UNION ALL SELECT '给我整不会了', '形容对方行为或突发状况让自己不知道如何应对。'
    UNION ALL SELECT '好家伙', '简短表达惊讶、佩服或无语，具体情绪由语境决定。'
    UNION ALL SELECT '笑不活了', '形容内容非常好笑，常作为评论区反应。'
    UNION ALL SELECT '蚌埠住了', '谐音“绷不住了”，表示忍不住笑、哭或情绪失控。'
    UNION ALL SELECT '我真的会谢', '夸张表达无奈、烦躁或被某事折腾后的情绪。'
    UNION ALL SELECT '我裂开了', '形容受到打击、尴尬或任务太多时的崩溃感。'
    UNION ALL SELECT '破大防', '表示心理防线受到明显冲击，常用于被戳中痛点或感动。'
    UNION ALL SELECT '瞬间破防', '形容某句话、画面或回忆突然引发强烈情绪。'
    UNION ALL SELECT '狠狠共情', '表示对某段经历或观点有强烈代入感。'
    UNION ALL SELECT '一整个爱住', '表示非常喜欢，常用于分享食物、穿搭或体验。'
    UNION ALL SELECT '这谁顶得住', '面对可爱、诱人或压力很大的事物时表达难以抵抗。'
    UNION ALL SELECT '封神了', '称赞某人或作品表现极好，仿佛达到了顶尖水平。'
    UNION ALL SELECT '杀疯了', '形容某人或作品表现强势、连续出彩，也可夸张调侃。'
    UNION ALL SELECT '高开低走', '形容开头吸引人，后续质量或势头逐渐下降。'
    UNION ALL SELECT '低开高走', '形容起初不被看好，后来表现越来越好。'
    UNION ALL SELECT '含金量还在上升', '指成绩或判断后来被证明更有价值，评价不断提高。'
    UNION ALL SELECT '有点东西', '表示发现对方确实有实力或作品值得肯定。'
    UNION ALL SELECT '懂的都懂', '暗示某些信息不展开说明，常用于玩笑或表达默契。'
    UNION ALL SELECT '展开说说', '邀请对方补充信息，也常用于期待八卦或剧情后续。'
    UNION ALL SELECT '蹲一个后续', '表示等待某件事的更新、结果或后续故事。'
    UNION ALL SELECT '吃瓜群众', '旁观事件、关注后续但不直接参与的人。'
    UNION ALL SELECT '反转文学', '通过连续揭示新背景或新结果制造戏剧效果的叙事方式。'
    UNION ALL SELECT '后续来了', '用于发布事件更新或故事续篇，吸引读者继续关注。'
    UNION ALL SELECT '又双叒叕', '用叠字夸张表示某件事情再次发生很多次。'
    UNION ALL SELECT '此处应有掌声', '在值得称赞或自我感觉良好的时刻幽默地提示鼓掌。'
    UNION ALL SELECT '你品，你细品', '提醒对方注意话语中的隐藏含义，也可引导回味笑点。'
    UNION ALL SELECT '有图有真相', '用图片或截图作为佐证，也常用于半开玩笑地强调可信度。'
    UNION ALL SELECT '夺笋啊', '谐音“多损啊”，调侃某人说话或做事太损。'
    UNION ALL SELECT '这操作属实下饭', '调侃某人表现失误但过程很有看点，常用于游戏或比赛。'
    UNION ALL SELECT '这波在大气层', '形容某个想法或操作出乎意料地高明。'
    UNION ALL SELECT '格局打开', '建议换个更宽广的视角看问题，也常被用来调侃突然上价值。'
    UNION ALL SELECT '真香定律', '先表示不会或不喜欢，之后实际体验又改变立场的常见反转。'
    UNION ALL SELECT '大型社死现场', '形容令人尴尬、希望立刻消失的公开场面。'
    UNION ALL SELECT '瞳孔地震', '通过夸张描述眼神表达震惊或不可置信。'
    UNION ALL SELECT 'CPU烧了', '调侃信息过多或逻辑复杂到让大脑无法处理。'
    UNION ALL SELECT '此地不宜久留', '发现情况不妙或尴尬时，戏剧化表达赶紧离开。'
    UNION ALL SELECT 'flag倒了', '原本设定的预期或承诺没有实现，常用来自我调侃。'
    UNION ALL SELECT 'YYDS', '“永远的神”拼音首字母缩写，用来称赞喜欢的人、物或作品。'
    UNION ALL SELECT 'xswl', '“笑死我了”的拼音首字母缩写，表示觉得好笑。'
    UNION ALL SELECT 'nsdd', '“你说得对”的拼音首字母缩写，有时用于敷衍结束讨论。'
    UNION ALL SELECT 'srds', '“虽然但是”的拼音首字母缩写，用来引出转折。'
    UNION ALL SELECT 'u1s1', '“有一说一”的数字谐音缩写，表示接下来要说实话。'
    UNION ALL SELECT 'bdjw', '“不懂就问”的拼音首字母缩写，用于提出问题或求解释。'
    UNION ALL SELECT 'awsl', '“啊我死了”的拼音缩写，夸张表达被可爱或惊艳内容击中。'
    UNION ALL SELECT 'kdl', '“磕到了”的拼音首字母缩写，表示嗑到角色或情侣互动的甜蜜感。'
    UNION ALL SELECT 'cpdd', '“处CP滴滴”的拼音缩写，用于寻找游戏或网络搭档。'
    UNION ALL SELECT 'emo了', '借用情绪词描述低落、忧郁或突然伤感的状态。'
    UNION ALL SELECT 'i人', '网络上对内向型人格的简写称呼，常用于描述偏好独处的人。'
    UNION ALL SELECT 'e人', '网络上对外向型人格的简写称呼，常用于描述喜欢主动社交的人。'
    UNION ALL SELECT '早F晚E', '调侃白天理性克制、晚上情绪丰富的状态。'
    UNION ALL SELECT '社恐', '口语化描述社交紧张或回避社交的状态，也常被用来轻松自嘲。'
    UNION ALL SELECT '社牛', '形容社交主动、大方且不怕陌生场合的人，常含夸张意味。'
    UNION ALL SELECT '搭子文化', '用“搭子”称呼一起吃饭、运动或学习的伙伴，强调轻量陪伴。'
    UNION ALL SELECT '饭搭子', '一起吃饭、探店或讨论美食的伙伴。'
    UNION ALL SELECT '旅游搭子', '结伴出游但不一定是亲密朋友的旅伴。'
    UNION ALL SELECT '学习搭子', '一起自习、打卡或相互督促学习的伙伴。'
    UNION ALL SELECT '运动搭子', '一起健身、跑步或参加运动的伙伴。'
    UNION ALL SELECT '追剧搭子', '一起追剧、讨论剧情或分享观后感的人。'
    UNION ALL SELECT '游戏搭子', '一起组队玩游戏、交流打法的朋友。'
    UNION ALL SELECT '显眼包', '形容在人群里表现活跃、容易吸引注意的人，常带亲昵调侃。'
    UNION ALL SELECT '氛围感', '指人物、照片或场景呈现出的整体气质和情绪感染力。'
    UNION ALL SELECT '反差萌', '人物外在形象与真实表现差异明显，从而显得可爱。'
    UNION ALL SELECT '社交悍匪', '形容社交主动、胆子大、很快和陌生人熟络的人。'
    UNION ALL SELECT '自来熟', '与人刚认识就表现得亲近热络，也可用于描述善于破冰的人。'
    UNION ALL SELECT '互联网嘴替', '替许多人说出心声、表达共同观点的人或内容。'
    UNION ALL SELECT '爷青回', '看到童年或青春时期熟悉的人事物重新出现时的感叹。'
    UNION ALL SELECT '爷青结', '感叹青春记忆中的事物结束、人物变化或阶段落幕。'
    UNION ALL SELECT 'DNA动了', '熟悉旋律、台词或画面唤起强烈记忆和情绪反应。'
    UNION ALL SELECT '内心OS', '指未说出口、只在心里想的台词或评论。'
    UNION ALL SELECT '锦鲤', '象征好运的鱼形网络形象，常用于转发祝福或求好运。'
    UNION ALL SELECT '欧皇', '调侃运气极佳、抽奖或游戏获得稀有奖励的人。'
    UNION ALL SELECT '非酋', '调侃运气不佳、抽奖总是不中的人。'
    UNION ALL SELECT '薅羊毛', '通过优惠券、活动或规则获得折扣和福利。'
    UNION ALL SELECT '种草', '他人推荐后产生购买、观看或尝试某物的兴趣。'
    UNION ALL SELECT '拔草', '购买或体验后消除好奇或消费欲望，也可表示不再想买。'
    UNION ALL SELECT '安利', '向他人推荐自己喜欢的作品、商品或体验。'
    UNION ALL SELECT '入坑', '开始喜欢或参与某个作品、游戏、爱好或圈子。'
    UNION ALL SELECT '出坑', '不再持续关注某个作品、游戏或爱好。'
) AS seed
CROSS JOIN (
    SELECT COALESCE(
        (SELECT user_account.id
         FROM `meme`.`user` AS user_account
         JOIN `meme`.`user_role` AS assigned_role ON assigned_role.user_id = user_account.id
         JOIN `meme`.`role` AS role_definition ON role_definition.id = assigned_role.role_id
         WHERE user_account.status = 1 AND role_definition.code IN ('ADMIN', 'SUPER_ADMIN')
         ORDER BY CASE role_definition.code WHEN 'SUPER_ADMIN' THEN 0 ELSE 1 END, user_account.id
         LIMIT 1),
        (SELECT id FROM `meme`.`user` WHERE status = 1 ORDER BY create_time, id LIMIT 1)
    ) AS `user_id`
) AS selected_user
LEFT JOIN `meme`.`meme` AS existing
       ON existing.`name` = seed.`name`
WHERE selected_user.`user_id` IS NOT NULL
  AND existing.`id` IS NULL;
