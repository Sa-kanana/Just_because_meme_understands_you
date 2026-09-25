USE `meme`;

create table admin_knowledge
(
    id           bigint                             not null
        primary key,
    title        varchar(200)                       not null comment '标题',
    content      mediumtext                         not null comment '正文',
    category     varchar(64)                        null comment '分类',
    tags         varchar(500)                       null comment '标签逗号分隔',
    content_hash varchar(64)                        not null comment '内容哈希幂等',
    status       tinyint  default 1                 not null comment '1发布 0删除',
    creator_id   bigint                             null,
    create_time  datetime default CURRENT_TIMESTAMP not null,
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment '管理员知识库（SSOT）';

create index idx_admin_knowledge_hash
    on admin_knowledge (content_hash);

create index idx_admin_knowledge_status
    on admin_knowledge (status);

create table ai_chat_message
(
    id             bigint                             not null comment '雪花 ID'
        primary key,
    session_id     bigint                             not null comment '会话 ID',
    role           varchar(16)                        not null comment 'user/assistant/system',
    content        text                               not null comment '消息正文',
    request_id     varchar(64)                        null comment '关联一次流式请求',
    token_estimate int                                null comment '估算 token 数',
    create_time    datetime default CURRENT_TIMESTAMP not null
)
    comment 'AI 搜索消息';

create index idx_ai_chat_message_request
    on ai_chat_message (request_id);

create index idx_ai_chat_message_session
    on ai_chat_message (session_id, create_time);

create table ai_chat_session
(
    id          bigint                                 not null comment '雪花 ID'
        primary key,
    user_id     bigint                                 not null comment '所属用户',
    title       varchar(128) default '新对话'          not null comment '会话标题',
    is_deleted  tinyint      default 0                 not null comment '0=未删 1=已删',
    create_time datetime     default CURRENT_TIMESTAMP not null,
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment 'AI 搜索会话';

create index idx_ai_chat_session_user
    on ai_chat_session (user_id, is_deleted, update_time);

create table home_image
(
    id           bigint auto_increment comment '主键ID'
        primary key,
    title        varchar(100)                       null comment '轮播图标题（可选，用于悬停显示）',
    img_url      varchar(500)                       not null comment '图片存放地址（可以是OSS链接或本地路径）',
    target_type  tinyint  default 0                 null comment '跳转类型：0-无跳转，1-内部文章/梗ID，2-外部链接',
    target_value varchar(500)                       null comment '跳转目标值（如文章ID或URL）',
    sort_order   int      default 0                 null comment '排序权重（数值越大越靠前）',
    status       tinyint  default 1                 null comment '状态：0-下线，1-上线',
    create_time  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '首页轮播图表';

create table meme
(
    id                  bigint auto_increment comment 'id唯一标识'
        primary key,
    introduction        varchar(255)  null comment '介绍',
    name                varchar(255)  null comment '名字',
    image               varchar(255)  null comment '图片url',
    page_views          int default 0 null comment '浏览量',
    likes               int default 0 null comment '点赞数',
    comments            int default 0 null comment '评论数',
    status              tinyint       not null comment '状态（1.正常，2.审核中，3.下架）',
    release_time        datetime      null comment '发布时间',
    update_time         datetime      null comment '更新时间',
    user_id             bigint        not null comment '发布者用户ID',
    offline_at          datetime      null comment '下架时间',
    offline_from_status tinyint       null comment '下架前状态 1或2',
    appeal_reject_count int default 0 not null comment '申诉驳回次数',
    offline_reason      varchar(512)  null comment '下架/锁定原因',
    purged_at           datetime      null comment '彻底删除时间'
)
    comment '梗';

create index idx_meme_user_id
    on meme (user_id);

create index idx_release_time
    on meme (release_time);

create index idx_status
    on meme (status);

create index idx_status_comments
    on meme (status asc, comments desc, id desc);

create index idx_status_likes
    on meme (status asc, likes desc, id desc);

create index idx_status_page_views
    on meme (status asc, page_views desc, id desc);

create index idx_status_release
    on meme (status asc, release_time desc, id desc);

create index idx_status_release_time
    on meme (status, release_time);

create index idx_status_views
    on meme (status asc, page_views desc, id desc);

create index idx_user_create
    on meme (user_id asc, release_time desc);

create index idx_user_status_release
    on meme (user_id asc, status asc, release_time desc);

create table meme_comment
(
    id          bigint                             not null comment '分布式唯一 ID (雪花算法)'
        primary key,
    meme_id     bigint                             not null comment '所属梗/内容的 ID',
    user_id     bigint                             not null comment '发评人 ID',
    root_id     bigint   default 0                 not null comment '根评论 ID (0:自己是根评论, >0:属于某条根评论的子评论)',
    parent_id   bigint   default 0                 not null comment '父评论 ID (实现嵌套被回复人)',
    content     text                               not null comment '评论内容',
    reply_count int      default 0                 not null comment '子评论总数',
    likes       int      default 0                 not null comment '点赞数/神评依据',
    is_deleted  tinyint  default 0                 not null comment '逻辑删除(0:正常, 1:已删除)',
    create_time datetime default CURRENT_TIMESTAMP not null comment '发布时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '梗的相关评论' collate = utf8mb4_general_ci;

create index idx_meme_root_hot
    on meme_comment (meme_id asc, root_id asc, is_deleted asc, likes desc, create_time desc);

create index idx_meme_root_likes_time
    on meme_comment (meme_id, root_id, likes, create_time);

create index idx_meme_root_new
    on meme_comment (meme_id asc, root_id asc, is_deleted asc, create_time desc);

create index idx_root_create_time
    on meme_comment (root_id, create_time);

create index idx_root_replies
    on meme_comment (root_id, is_deleted, create_time);

create table meme_comment_images
(
    id              bigint auto_increment
        primary key,
    meme_comment_id bigint        not null comment '关联的评论 ID',
    url             varchar(512)  not null comment '图片 URL',
    sort_order      int default 0 null comment '图片在当前评论中的排序'
)
    comment '评论相关的图片';

create index idx_comment_id
    on meme_comment_images (meme_comment_id);

create table meme_resource
(
    id            bigint auto_increment
        primary key,
    meme_id       bigint                             not null,
    resource_url  varchar(500)                       not null,
    resource_type tinyint  default 1                 not null comment '1=OSS图片 2=外链 3=视频',
    title         varchar(128)                       null comment '展示标题，外链必填',
    sort_order    int      default 0                 not null comment '展示排序，越小越靠前',
    status        tinyint  default 1                 not null comment '1=正常 0=审核隐藏',
    create_time   datetime default CURRENT_TIMESTAMP not null
)
    comment '梗相关的链接';

create index idx_meme_sort
    on meme_resource (meme_id, status, sort_order);

create table meme_tag
(
    id               int auto_increment comment '唯一标识'
        primary key,
    name             varchar(255)  not null comment '标签名',
    related_quantity int default 0 not null comment '相关梗的数量'
)
    comment '梗的标签';

create index idx_related_quantity
    on meme_tag (related_quantity desc, id desc);

create table meme_tag_relation
(
    meme_id     bigint unsigned not null comment '梗ID',
    meme_tag_id bigint unsigned not null comment '标签ID',
    primary key (meme_id, meme_tag_id)
)
    comment '梗与其标签的关联表';

create index idx_tag_id
    on meme_tag_relation (meme_tag_id);

create table sensitive_word
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    word        varchar(64)                           not null comment '敏感词/违规词汇（如：小黑子）',
    category    varchar(32) default 'GENERAL'         not null comment '敏感词类型（POLITICS:政治, PORN:色情, ABUSE:辱骂, AD:广告, CUSTOM:自定义玩梗）',
    action_type tinyint     default 1                 not null comment '命中的处理策略（1:直接替换为***, 2:需要人工审核, 3:直接拒绝发评）',
    status      tinyint     default 1                 not null comment '状态（0:禁用, 1:启用）',
    creator     varchar(32) default ''                not null comment '创建人/运营人员',
    create_time datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_word
        unique (word)
)
    comment '敏感词库表' collate = utf8mb4_general_ci;

create index idx_status_category
    on sensitive_word (status, category);

create table user
(
    id          bigint                                not null comment '主键'
        primary key,
    nickname    varchar(50)                           not null comment '昵称',
    avatar      varchar(255)                          null comment '头像',
    gender      tinyint                               null comment '性别 0-未知, 1-男, 2-女',
    birthday    date                                  null comment '生日',
    signature   varchar(255)                          null comment '个性标签',
    status      tinyint     default 1                 null comment '0:禁用, 1:正常',
    role        varchar(20) default 'ROLE_USER'       null comment '角色（ROLE_USER,ROLE_ADMIN）',
    create_time datetime    default CURRENT_TIMESTAMP null,
    update_time datetime                              null comment '最后修改时间',
    constraint uk_nickname
        unique (nickname)
)
    comment '用户基础信息表';

create table user_auth
(
    id                  bigint auto_increment
        primary key,
    user_id             bigint       not null,
    identity_type       varchar(20)  not null comment 'email, github, wechat',
    identifier          varchar(100) not null comment '邮箱或OpenID',
    password            varchar(255) null comment '密码或Token',
    password_changed_at datetime     null comment '最近改密时间',
    constraint uk_identity
        unique (identity_type, identifier)
)
    comment '用户账号表';

create index idx_user_id
    on user_auth (user_id);

create table user_comment_like
(
    id          bigint            not null
        primary key,
    user_id     bigint            not null,
    comment_id  bigint            not null,
    is_deleted  tinyint default 0 not null,
    create_time datetime          not null,
    update_time datetime          not null,
    constraint uk_user_comment
        unique (user_id, comment_id)
);

create index idx_comment_active
    on user_comment_like (comment_id, is_deleted);

create table user_favorite
(
    id          bigint                             not null comment '主键ID (雪花算法)'
        primary key,
    user_id     bigint                             not null comment '用户ID (关联user_info.id)',
    meme_id     bigint                             not null comment '梗的ID (关联meme.id)',
    folder_id   bigint   default 0                 not null comment '收藏夹ID，0表示默认收藏夹',
    sort_order  int      default 0                 not null comment '收藏夹内排序',
    is_deleted  tinyint  default 0                 not null comment '逻辑删除标记（0:未删除，1:已删除）',
    create_time datetime default CURRENT_TIMESTAMP not null comment '收藏时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_meme_deleted
        unique (user_id, meme_id, is_deleted)
)
    comment '用户收藏表' collate = utf8mb4_general_ci;

create index idx_user_folder
    on user_favorite (user_id, folder_id);

create index idx_user_folder_sort
    on user_favorite (user_id, folder_id, is_deleted, sort_order);

create table user_favorite_folder
(
    id          bigint                             not null comment '主键（雪花）'
        primary key,
    user_id     bigint                             not null comment '所属用户',
    name        varchar(64)                        not null comment '收藏夹名称',
    description varchar(255)                       null comment '简介',
    is_public   tinyint  default 1                 not null comment '是否公开：0私密 1公开',
    sort_order  int      default 0                 not null comment '收藏夹列表排序',
    meme_count  int      default 0                 not null comment '冗余计数',
    is_deleted  tinyint  default 0                 not null comment '逻辑删除',
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_user_name_deleted
        unique (user_id, name, is_deleted)
)
    comment '用户收藏夹';

create index idx_user_sort
    on user_favorite_folder (user_id, is_deleted, sort_order);

create table user_like
(
    id          bigint                             not null comment '主键（雪花）'
        primary key,
    user_id     bigint                             not null comment '点赞用户',
    meme_id     bigint                             not null comment '被赞梗 id',
    is_deleted  tinyint  default 0                 not null comment '0有效 1已取消',
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint uk_user_meme_deleted
        unique (user_id, meme_id, is_deleted)
)
    comment '用户点赞梗表';

create index idx_meme_active
    on user_like (meme_id, is_deleted);

create index idx_user_active
    on user_like (user_id asc, is_deleted asc, create_time desc);

create table user_notification
(
    id          bigint                  not null comment '雪花ID'
        primary key,
    user_id     bigint                  not null comment '接收者',
    actor_id    bigint                  null comment '触发者；系统消息可为NULL',
    type        varchar(32)             not null comment '通知类型枚举',
    title       varchar(128) default '' not null comment '标题摘要',
    content     varchar(512) default '' not null comment '正文摘要',
    target_type varchar(32)             null comment 'meme|comment|user|system',
    target_id   bigint                  null comment '跳转目标主键',
    ref_id      bigint                  null comment '辅引用：如评论id、关系id',
    extra_json  varchar(1024)           null comment '扩展JSON（梗封面、审核原因等）',
    group_key   varchar(64)             null comment '聚合键，如 like_meme:{memeId}',
    is_read     tinyint      default 0  not null comment '0未读 1已读',
    is_deleted  tinyint      default 0  not null comment '0正常 1删除',
    create_time datetime                not null,
    update_time datetime                not null
)
    comment '用户消息通知表';

create index idx_group
    on user_notification (user_id, group_key, is_deleted);

create index idx_user_list
    on user_notification (user_id asc, is_deleted asc, create_time desc, id desc);

create index idx_user_type
    on user_notification (user_id asc, type asc, is_deleted asc, create_time desc);

create index idx_user_unread
    on user_notification (user_id asc, is_deleted asc, is_read asc, create_time desc);

create table user_relation
(
    id           bigint                             not null comment '主键ID'
        primary key,
    from_user_id bigint                             not null comment '关注者ID (动作发起人)',
    to_user_id   bigint                             not null comment '被关注者ID (梗主)',
    create_time  datetime default CURRENT_TIMESTAMP null comment '关注时间',
    is_deleted   tinyint  default 0                 not null,
    update_time  datetime                           null,
    constraint uk_from_to
        unique (from_user_id, to_user_id),
    constraint uk_relation
        unique (from_user_id, to_user_id)
)
    comment '用户关注关系表';

create index idx_from_create
    on user_relation (from_user_id asc, create_time desc);

create index idx_from_user
    on user_relation (from_user_id);

create index idx_to_create
    on user_relation (to_user_id asc, create_time desc);

create index idx_to_user
    on user_relation (to_user_id);

create table user_stats
(
    user_id        bigint        not null comment '关联user的id'
        primary key,
    meme_count     int default 0 null comment '发布的梗数量',
    like_received  int default 0 null comment '获得的获赞总数',
    favorite_count int default 0 null comment '收藏的梗数量',
    follow_count   int default 0 null comment '关注人数',
    fans_count     int default 0 null comment '粉丝人数'
)
    comment '用户社交数据统计表';


