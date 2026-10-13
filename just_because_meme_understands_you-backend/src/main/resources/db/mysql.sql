USE `meme`;

create table admin_knowledge
(
    id           bigint                             not null comment '知识文档主键'
        primary key,
    title        varchar(200)                       not null comment '标题',
    content      mediumtext                         not null comment '正文',
    category     varchar(64)                        null comment '分类',
    tags         varchar(500)                       null comment '标签逗号分隔',
    content_hash varchar(64)                        not null comment '内容哈希幂等',
    content_sha256 char(64)                           null comment '标准化正文 SHA-256 去重指纹',
    index_status varchar(24) default 'PENDING'      not null comment '向量索引状态：PENDING、PROCESSING、READY、FAILED、DELETE_PENDING、DELETED',
    index_revision bigint unsigned default 0        not null comment '单调递增的向量操作版本',
    index_task_id bigint unsigned                   null comment '最近一次向量操作对应的发件箱事件 ID',
    index_error  varchar(1000)                      null comment '最近一次向量操作错误摘要',
    status       tinyint  default 1                 not null comment '1发布 0删除',
    creator_id   bigint                             null comment '创建管理员 ID',
    create_time  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_admin_knowledge_content_sha256 (content_sha256)
)
    comment '管理员知识库（SSOT）';

create index idx_admin_knowledge_hash
    on admin_knowledge (content_hash);

create index idx_admin_knowledge_status
    on admin_knowledge (status);

create table integration_outbox_event
(
    id              bigint unsigned auto_increment not null comment '发件箱事件主键',
    aggregate_type  varchar(64)                    not null comment '业务聚合类型',
    aggregate_id    bigint unsigned                not null comment '业务聚合标识',
    event_type      varchar(100)                   not null comment '集成事件类型',
    payload         json                           not null comment '事件载荷快照',
    status          varchar(20) default 'PENDING'  not null comment '投递状态：待处理、处理中、重试、已完成或失败',
    attempts        int unsigned default 0         not null comment '已尝试投递次数',
    created_at      datetime(3) default CURRENT_TIMESTAMP(3) not null comment '事件创建时间',
    next_attempt_at datetime(3) default CURRENT_TIMESTAMP(3) not null comment '下次允许投递时间',
    locked_at       datetime(3)                    null comment '本次处理租约时间',
    delivered_at    datetime(3)                    null comment '成功投递时间',
    last_error      varchar(1000)                  null comment '最近一次投递错误摘要',
    primary key (id),
    key idx_outbox_status_retry (status, next_attempt_at, id),
    key idx_outbox_aggregate (aggregate_type, aggregate_id, id)
)
    comment '模块集成事务发件箱';

create table ai_chat_message
(
    id             bigint                             not null comment '雪花 ID'
        primary key,
    session_id     bigint                             not null comment '会话 ID',
    role           varchar(16)                        not null comment 'user/assistant/system',
    status         varchar(16) default 'COMPLETED'    not null comment '消息状态：COMPLETED已完成、STOPPED用户停止、FAILED生成失败',
    content        text                               not null comment '消息正文',
    request_id     varchar(64)                        null comment '关联一次流式请求',
    token_estimate int                                null comment '估算 token 数',
    create_time    datetime default CURRENT_TIMESTAMP not null comment '消息创建时间'
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
    create_time datetime     default CURRENT_TIMESTAMP not null comment '会话创建时间',
    update_time datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '会话更新时间'
)
    comment 'AI 搜索会话';

create index idx_ai_chat_session_user
    on ai_chat_session (user_id, is_deleted, update_time);

alter table ai_chat_message
    add constraint fk_ai_chat_message_session
        foreign key (session_id) references ai_chat_session (id)
            on delete restrict on update restrict;

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
    page_views          int default 0 not null comment '浏览量',
    likes               int default 0 not null comment '点赞数',
    comments            int default 0 not null comment '评论数',
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
    id              bigint auto_increment comment '评论图片主键'
        primary key,
    meme_comment_id bigint        not null comment '关联的评论 ID',
    url             varchar(512)  not null comment '图片 URL',
    sort_order      int default 0 null comment '图片在当前评论中的排序',
    key idx_comment_id (meme_comment_id),
    constraint fk_meme_comment_images_comment
        foreign key (meme_comment_id) references meme_comment (id)
            on delete restrict on update restrict
)
    comment '评论相关的图片';

create table meme_resource
(
    id            bigint auto_increment comment '梗资源主键'
        primary key,
    meme_id       bigint                             not null comment '所属梗ID',
    resource_url  varchar(500)                       not null comment '资源地址',
    resource_type tinyint  default 1                 not null comment '1=OSS图片 2=外链 3=视频',
    title         varchar(128)                       null comment '展示标题，外链必填',
    sort_order    int      default 0                 not null comment '展示排序，越小越靠前',
    status        tinyint  default 1                 not null comment '资源状态：1正常可用，0下架隐藏，2内容替换后归档',
    create_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    key idx_meme_sort (meme_id, status, sort_order),
    constraint fk_meme_resource_meme
        foreign key (meme_id) references meme (id)
            on delete restrict on update restrict
)
    comment '梗相关的链接';

create table meme_tag
(
    id               int auto_increment comment '唯一标识'
        primary key,
    name             varchar(255)  not null comment '标签名',
    category         varchar(24) default 'UNCATEGORIZED' not null comment '标签所属分类代码',
    sort_order       int default 0 not null comment '分类内展示顺序，数值越小越靠前',
    related_quantity int default 0 not null comment '相关梗的数量'
)
    comment '梗的标签';

create index idx_related_quantity
    on meme_tag (related_quantity desc, id desc);

create index idx_meme_tag_name
    on meme_tag (name);

create index idx_meme_tag_category_order
    on meme_tag (category, sort_order, id);

create table meme_tag_relation
(
    meme_id         bigint not null comment '梗ID',
    meme_tag_id     int    not null comment '标签ID',
    is_primary      tinyint default 0 not null comment '是否为该梗的主分类标签，1是0否',
    primary_meme_id bigint null comment '主标签唯一约束辅助列，由应用在主标签关联中写入梗ID',
    primary key (meme_id, meme_tag_id),
    unique key uk_meme_tag_primary_per_meme (primary_meme_id),
    constraint fk_meme_tag_relation_meme foreign key (meme_id) references meme (id) on delete cascade,
    constraint fk_meme_tag_relation_tag foreign key (meme_tag_id) references meme_tag (id) on delete cascade
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
    risk_level  varchar(16) default 'NORMAL'          not null comment '风险等级（NORMAL:允许或替换, REVIEW:人工审核, BLOCK:直接拒绝）',
    match_type  varchar(20) default 'NORMAL'          not null comment '匹配方式（NORMAL:标准化后按字面匹配）',
    replacement varchar(20)                           null comment 'NORMAL 等级命中后的替换文本，空值表示保留原文',
    version     bigint      default 0                 not null comment '该敏感词记录的修订版本',
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

create table sensitive_word_dictionary_meta
(
    id         tinyint     not null comment '单例元数据行主键，固定为1'
        primary key,
    version    bigint      default 0                 not null comment '全局词库版本，每次管理变更递增',
    updated_at datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '全局词库最近更新时间'
)
    comment '敏感词词库全局版本表' collate = utf8mb4_general_ci;

-- 中文说明：以下种子值初始化词库单例元数据，版本号从0开始。
insert into sensitive_word_dictionary_meta (
    id,      -- 中文说明：元数据单例主键，目标字段 sensitive_word_dictionary_meta.id，固定值为1
    version  -- 中文说明：全局词库版本，目标字段 sensitive_word_dictionary_meta.version，初始值为0
)
values (1, 0);

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
    create_time datetime    default CURRENT_TIMESTAMP null comment '用户创建时间',
    update_time datetime                              null comment '最后修改时间',
    deleted_at  datetime                              null comment '逻辑删除时间，NULL表示未删除',
    constraint uk_nickname
        unique (nickname)
)
    comment '用户基础信息表';

create index idx_user_deleted_create_time
    on user (deleted_at, create_time, id);

create table `role`
(
    id          bigint auto_increment not null comment '角色主键'
        primary key,
    code        varchar(32)           not null comment '角色代码：USER、ADMIN、SUPER_ADMIN',
    name        varchar(64)           not null comment '角色中文名称',
    create_time datetime default CURRENT_TIMESTAMP not null comment '角色创建时间',
    constraint uk_role_code
        unique (code)
)
    comment '系统角色定义表';

-- 中文说明：来源为平台内置角色清单，分别写入角色代码和中文显示名称。
insert into `role` (
    code,  -- 中文说明：角色代码，目标字段 role.code
    name   -- 中文说明：角色中文名称，目标字段 role.name
)
values ('USER', '普通用户'), ('ADMIN', '管理员'), ('SUPER_ADMIN', '超级管理员');

create table user_role
(
    user_id     bigint   not null comment '关联的用户主键',
    role_id     bigint   not null comment '关联的角色主键',
    create_time datetime default CURRENT_TIMESTAMP not null comment '角色分配时间',
    primary key (user_id, role_id),
    key idx_user_role_role (role_id, user_id),
    constraint fk_user_role_user
        foreign key (user_id) references user (id)
            on delete restrict on update restrict,
    constraint fk_user_role_role
        foreign key (role_id) references `role` (id)
            on delete restrict on update restrict
)
    comment '用户角色关联表';

-- 初始化用户角色关联时，来源字段 user.id 写入目标字段 user_role.user_id。
-- 初始化用户角色关联时，来源字段 role.id 写入目标字段 user_role.role_id。
insert into user_role (user_id, role_id)
select user.id, `role`.id
from user
join `role` on `role`.code = 'USER';

create table user_auth
(
    id                  bigint auto_increment comment '账号记录主键'
        primary key,
    user_id             bigint       not null comment '所属用户ID',
    identity_type       varchar(20)  not null comment 'email, github, wechat',
    identifier          varchar(100) not null comment '邮箱或OpenID',
    password            varchar(255) null comment '密码或Token',
    password_changed_at datetime     null comment '最近改密时间',
    key idx_user_id (user_id),
    constraint uk_identity
        unique (identity_type, identifier),
    constraint fk_user_auth_user
        foreign key (user_id) references user (id)
            on delete restrict on update restrict
)
    comment '用户账号表';

create table user_comment_like
(
    id          bigint            not null comment '评论点赞记录主键'
        primary key,
    user_id     bigint            not null comment '点赞用户ID',
    comment_id  bigint            not null comment '被点赞评论ID',
    is_deleted  tinyint default 0 not null comment '逻辑删除标记：0有效，1已取消',
    create_time datetime          not null comment '点赞记录创建时间',
    update_time datetime          not null comment '点赞记录更新时间',
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
    constraint uk_user_meme
        unique (user_id, meme_id)
)
    comment '用户收藏表' collate = utf8mb4_general_ci;

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
    create_time datetime default CURRENT_TIMESTAMP not null comment '收藏夹创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '收藏夹更新时间',
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
    create_time datetime default CURRENT_TIMESTAMP not null comment '点赞创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '点赞更新时间',
    constraint uk_user_meme
        unique (user_id, meme_id)
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
    source_event_id bigint unsigned     null comment '来源 Outbox 事件 ID；为空表示非 Outbox 通知',
    is_read     tinyint      default 0  not null comment '0未读 1已读',
    is_deleted  tinyint      default 0  not null comment '0正常 1删除',
    create_time datetime                not null comment '通知创建时间',
    update_time datetime                not null comment '通知更新时间',
    unique key uk_user_notification_source_event (source_event_id)
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
    is_deleted   tinyint  default 0                 not null comment '逻辑删除状态：0有效，1已取消',
    update_time  datetime                           null comment '关系更新时间',
    constraint uk_from_to
        unique (from_user_id, to_user_id)
)
    comment '用户关注关系表';

create index idx_from_create
    on user_relation (from_user_id asc, create_time desc);

create index idx_to_create
    on user_relation (to_user_id asc, create_time desc);

create table user_stats
(
    user_id        bigint        not null comment '关联user的id'
        primary key,
    like_received  int default 0 not null comment '获得的获赞总数',
    favorite_count int default 0 not null comment '收藏的梗数量',
    follow_count   int default 0 not null comment '关注人数',
    fans_count     int default 0 not null comment '粉丝人数',
    constraint fk_user_stats_user
        foreign key (user_id) references user (id)
            on delete restrict on update restrict
)
    comment '用户社交数据统计表';

create table user_stats_orphan_archive
(
    archive_id     bigint auto_increment comment '归档记录主键'
        primary key,
    source_user_id bigint       not null comment '来源 user_stats.user_id，原用户ID',
    meme_count     int          not null comment '来源 user_stats.meme_count，发布梗数量',
    like_received  int          not null comment '来源 user_stats.like_received，获得点赞数量',
    favorite_count int          not null comment '来源 user_stats.favorite_count，收藏数量',
    follow_count   int          not null comment '来源 user_stats.follow_count，关注数量',
    fans_count     int          not null comment '来源 user_stats.fans_count，粉丝数量',
    archived_at    datetime     not null default CURRENT_TIMESTAMP comment '统计记录归档时间',
    constraint uk_user_stats_orphan_source_user
        unique (source_user_id)
)
    comment '无法关联用户的历史统计记录归档表';

create table user_tag_follow
(
    user_id    bigint   not null comment '关注用户ID',
    tag_id     int      not null comment '被关注的标签ID',
    create_time datetime not null default CURRENT_TIMESTAMP comment '关注创建时间',
    primary key (user_id, tag_id),
    constraint fk_tag_follow_tag foreign key (tag_id) references meme_tag (id) on delete cascade,
    index idx_tag_follow_tag (tag_id, user_id)
) comment '用户关注梗标签';

create table meme_report_archive
(
    id              bigint auto_increment primary key comment '举报记录主键',
    meme_id         bigint       not null comment '被举报梗ID',
    reporter_user_id bigint      not null comment '举报人用户ID',
    reason          varchar(32)  not null comment '举报原因编码',
    detail          varchar(500) null comment '举报补充说明',
    status          varchar(16)  not null default 'PENDING' comment '处理状态',
    result          varchar(16)  null comment '处理结果编码',
    handled_by      bigint       null comment '处理管理员用户ID',
    handling_note   varchar(500) null comment '管理员处理备注',
    created_at      datetime     not null default CURRENT_TIMESTAMP comment '举报创建时间',
    handled_at      datetime     null comment '举报处理完成时间',
    unique key uq_meme_reporter (meme_id, reporter_user_id),
    key idx_report_status_created (status, created_at, id)
) engine=InnoDB default charset=utf8mb4 comment='历史梗举报记录归档，仅用于审计查询';

create table community_report
(
    id               bigint auto_increment primary key comment '主键ID',
    target_type      varchar(16)  not null comment '举报对象类型：梗或评论',
    target_id        bigint       not null comment '被举报对象ID',
    meme_id          bigint       not null comment '举报对象所属梗ID',
    comment_id       bigint       null comment '被举报评论ID，梗举报时为空',
    reporter_user_id bigint       not null comment '举报人用户ID',
    reason           varchar(32)  not null comment '举报原因编码',
    detail           varchar(500) null comment '举报补充说明',
    status           varchar(16)  not null default 'PENDING' comment '处理状态：待处理或已处理',
    result           varchar(24)  null comment '处理结果编码',
    handled_by       bigint       null comment '处理管理员用户ID',
    handling_note    varchar(500) null comment '管理员处理备注',
    created_at       datetime     not null default CURRENT_TIMESTAMP comment '举报创建时间',
    handled_at       datetime     null comment '举报处理完成时间',
    unique key uq_community_report_target_reporter (target_type, target_id, reporter_user_id),
    key idx_community_report_status_created (status, created_at, id),
    key idx_community_report_meme (meme_id, created_at),
    key idx_community_report_comment (comment_id)
) engine=InnoDB default charset=utf8mb4 comment='统一梗与评论举报队列';

create table user_suppression
(
    id             bigint      auto_increment primary key comment '主键ID',
    owner_user_id  bigint      not null comment '发起静音或屏蔽的用户ID',
    target_user_id bigint      not null comment '被静音或屏蔽的用户ID',
    type           varchar(16) not null comment '关系类型：静音或屏蔽',
    created_at     datetime    not null default CURRENT_TIMESTAMP comment '关系创建时间',
    unique key uq_user_suppression (owner_user_id, target_user_id, type),
    key idx_user_suppression_target (target_user_id, type),
    key idx_user_suppression_owner (owner_user_id, created_at)
) engine=InnoDB default charset=utf8mb4 comment='用户静音与屏蔽关系';

create table meme_daily_snapshot
(
    id            bigint auto_increment primary key comment '快照主键',
    meme_id       bigint   not null comment '所属梗ID',
    snapshot_date date     not null comment '快照日期',
    page_views    bigint   not null default 0 comment '累计浏览量',
    likes         bigint   not null default 0 comment '累计点赞数',
    comments      bigint   not null default 0 comment '累计评论数',
    created_at    datetime not null default CURRENT_TIMESTAMP comment '快照写入时间',
    unique key uk_meme_snapshot_day (meme_id, snapshot_date),
    key idx_snapshot_date (snapshot_date, meme_id),
    constraint fk_meme_daily_snapshot_meme
        foreign key (meme_id) references meme (id)
            on delete restrict on update restrict
) comment '梗每日互动计数快照';

create table meme_weekly_topic
(
    iso_week_year     smallint     not null comment 'ISO 周对应的年份',
    iso_week_number   tinyint      not null comment 'ISO 周号',
    tag_id            int          not null comment '选中的标签 ID',
    tag_name          varchar(255) not null comment '标签名快照',
    interaction_score bigint       not null default 0 comment '近 7 天互动增长分',
    is_fallback       tinyint(1)   not null default 0 comment '是否因趋势历史不足而回退',
    created_at        datetime     not null default current_timestamp comment '主题确定时间',
    primary key (iso_week_year, iso_week_number)
) engine=InnoDB default charset=utf8mb4 comment='每周热梗主题';

-- 中文说明：以下结构构成 Flyway 2026101205 当前基准；历史版本迁移只供已有数据库校验与追溯。
create table meme_edit_revision
(
    id                bigint auto_increment comment '修改稿记录ID'
        primary key,
    meme_id           bigint                                not null comment '被修改的梗ID',
    editor_user_id    bigint                                not null comment '提交修改的作者用户ID',
    base_content_hash char(64)                              not null comment '提交时正式内容的SHA-256摘要',
    content_json      json                                  not null comment '梗名称、介绍、封面、标签和资源的修改内容快照',
    status            varchar(16) default 'PENDING'         not null comment '审核状态：PENDING、APPROVED、REJECTED或CANCELED',
    review_note       varchar(500)                          null comment '审核驳回或取消说明',
    reviewed_by       bigint                                null comment '处理该修改稿的管理员用户ID',
    created_at        datetime    default CURRENT_TIMESTAMP not null comment '修改稿提交时间',
    reviewed_at       datetime                              null comment '修改稿审核完成时间',
    key idx_meme_edit_revision_meme_status (meme_id, status),
    key idx_meme_edit_revision_status_created (status, created_at, id)
) engine=InnoDB default charset=utf8mb4 comment='梗内容修改审核记录';

create table file_asset
(
    id                bigint auto_increment comment '文件资产主键'
        primary key,
    object_key        varchar(512)                          not null comment 'OSS对象键，不包含访问域名',
    owner_id          bigint                                null comment '上传者用户ID，历史回填资产可为空',
    scene             varchar(32)                           not null comment '上传业务场景，例如avatar、meme或comment',
    original_filename varchar(255)                          null comment '客户端声明的原始文件名，仅用于展示和审计',
    content_type      varchar(128)                          null comment '文件MIME类型，历史资产未知时为空',
    file_size         bigint unsigned                       null comment '文件字节数，历史资产未知时为空',
    status            varchar(24) default 'TEMP'             not null comment '资产状态：TEMP、ACTIVE、PENDING_DELETE、DELETING或DELETED',
    uploaded_at       datetime                              null comment '服务端确认OSS上传成功的时间',
    activated_at      datetime                              null comment '资产首次绑定业务引用的时间',
    delete_after      datetime                              null comment '允许执行OSS删除的最早时间',
    deleted_at        datetime                              null comment 'OSS对象确认删除的时间',
    created_at        datetime    default CURRENT_TIMESTAMP not null comment '资产记录创建时间',
    updated_at        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '资产记录最后更新时间',
    unique key uk_file_asset_object_key (object_key),
    key idx_file_asset_status (status, delete_after, id),
    key idx_file_asset_temp_created (status, created_at, id) comment '按资产状态和创建时间扫描过期TEMP对象'
) engine=InnoDB default charset=utf8mb4 comment='OSS文件资产及其生命周期状态';

create table file_asset_ref
(
    id         bigint auto_increment comment '文件资产引用主键'
        primary key,
    asset_id   bigint                                not null comment '被业务引用的文件资产ID',
    biz_type   varchar(32)                           not null comment '引用方业务类型，例如USER、MEME或BANNER',
    biz_id     bigint                                not null comment '引用方业务记录ID',
    field_key  varchar(128)                          not null comment '业务字段位置，例如AVATAR、COVER或资源行ID',
    created_at datetime    default CURRENT_TIMESTAMP not null comment '业务引用创建时间',
    unique key uk_file_asset_ref_business_field (biz_type, biz_id, field_key),
    key idx_file_asset_ref_asset (asset_id),
    constraint fk_file_asset_ref_asset foreign key (asset_id) references file_asset (id) on delete restrict
) engine=InnoDB default charset=utf8mb4 comment='文件资产到业务字段的多态引用';

create table file_delete_task
(
    id              bigint auto_increment comment '文件删除任务主键'
        primary key,
    asset_id        bigint                                not null comment '待删除的文件资产ID',
    object_key      varchar(512)                          not null comment '创建任务时的OSS对象键快照',
    reason          varchar(64)                           not null comment '触发删除任务的业务原因',
    status          varchar(16) default 'WAITING'          not null comment '任务状态：WAITING、PROCESSING、DONE、CANCELED或FAILED',
    retry_count     tinyint unsigned default 0             not null comment '已执行的删除尝试次数',
    next_attempt_at datetime                              not null comment '下一次允许执行任务的时间',
    lease_owner     varchar(128)                          null comment '当前领取任务的应用实例标识',
    lease_until     datetime                              null comment '当前任务租约过期时间',
    started_at      datetime                              null comment '最近一次开始处理的时间',
    finished_at     datetime                              null comment '任务完成、取消或终止的时间',
    last_error      varchar(1000)                         null comment '最近一次删除失败的错误摘要',
    created_at      datetime    default CURRENT_TIMESTAMP not null comment '任务创建时间',
    updated_at      datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '任务最后更新时间',
    key idx_file_delete_due (status, next_attempt_at, id),
    key idx_file_delete_lease (status, lease_until, id),
    key idx_file_delete_asset (asset_id),
    constraint fk_file_delete_task_asset foreign key (asset_id) references file_asset (id) on delete restrict
) engine=InnoDB default charset=utf8mb4 comment='事务内创建并由定时Worker处理的OSS删除任务';

create table file_orphan_candidate
(
    id                bigint auto_increment comment 'OSS孤儿候选主键'
        primary key,
    object_key        varchar(512)                          not null comment 'OSS中发现但资产表未登记的对象键',
    status            varchar(16) default 'DISCOVERED'      not null comment '候选状态：DISCOVERED、REFERENCED、QUEUED或SKIPPED',
    first_seen_at     datetime                              not null comment '首次发现该对象的时间',
    last_seen_at      datetime                              not null comment '最近一次仍发现该对象的时间',
    confirmed_at      datetime                              null comment '满足最短复查间隔后的确认时间',
    last_check_result varchar(500)                          null comment '最近一次对账检查结果摘要',
    created_at        datetime    default CURRENT_TIMESTAMP not null comment '候选记录创建时间',
    updated_at        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '候选记录最后更新时间',
    unique key uk_file_orphan_object_key (object_key),
    key idx_file_orphan_review (status, first_seen_at, last_seen_at)
) engine=InnoDB default charset=utf8mb4 comment='OSS与数据库对账发现的孤儿对象候选';

create table file_asset_backfill_state
(
    backfill_key  varchar(64)                           not null comment '本次历史资产回填任务的唯一名称',
    source_name   varchar(128)                          not null comment '当前扫描的数据源及业务字段名称',
    last_source_id bigint                               null comment '当前数据源最近完成回填的主键游标',
    status        varchar(16) default 'PENDING'          not null comment '回填状态：PENDING、RUNNING、COMPLETED或FAILED',
    lease_owner   varchar(128)                          null comment '当前持有回填租约的应用实例标识',
    lease_until   datetime                              null comment '回填租约过期时间',
    started_at    datetime                              null comment '本次回填开始时间',
    completed_at  datetime                              null comment '全部数据源回填完成时间',
    last_error    varchar(1000)                         null comment '最近一次回填失败的错误摘要',
    updated_at    datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '回填状态最后更新时间',
    primary key (backfill_key, source_name),
    key idx_file_backfill_status (status, lease_until, backfill_key)
) engine=InnoDB default charset=utf8mb4 comment='历史文件资产回填检查点和多实例执行租约';

create table meme_vector_reconciliation_job
(
    id               bigint auto_increment comment '校准任务主键'
        primary key,
    active_slot      tinyint                               null comment '活动任务唯一占位；活动任务为1，终态为空',
    status           varchar(16) default 'PENDING'          not null comment '任务状态：PENDING、RUNNING、FAILED或COMPLETED',
    phase            varchar(20) default 'SYNC_PUBLISHED'   not null comment '当前阶段：同步已发布梗或清理无效向量',
    published_cursor bigint      default 0                  not null comment 'Java已发布梗扫描到的最后一个梗ID',
    vector_cursor    varchar(64)                           null comment 'MemeAgent向量ID分页的下一页游标',
    published_scanned bigint     default 0                  not null comment 'Java已扫描的已发布梗数量',
    upsert_queued    bigint      default 0                  not null comment '已写入outbox的梗索引更新事件数量',
    vector_scanned   bigint      default 0                  not null comment '已扫描的向量库梗ID数量',
    removal_queued   bigint      default 0                  not null comment '已写入outbox的无效向量删除事件数量',
    created_by       bigint                                not null comment '发起校准任务的管理员用户ID',
    lease_owner      varchar(64)                           null comment '当前处理任务的worker实例标识',
    lease_until      datetime(3)                           null comment '当前worker租约到期时间',
    failure_stage    varchar(64)                           null comment '失败时保留的安全阶段标识',
    created_at       datetime(3) default CURRENT_TIMESTAMP(3) not null comment '任务创建时间',
    updated_at       datetime(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3) comment '任务最后更新时间',
    started_at       datetime(3)                           null comment '任务首次开始处理时间',
    finished_at      datetime(3)                           null comment '任务失败或完成时间',
    unique key uq_mvrj_active_slot (active_slot),
    key idx_mvrj_status_lease (status, lease_until, id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci comment='管理员手动触发的梗向量库双向校准任务';

-- 中文说明：新库跳过历史标签迁移，因此在当前基准中直接初始化标准标签目录。
-- 中文说明：来源为平台维护的标准标签清单；各目标列逐项注释，相关梗数量均从0开始。
insert into meme_tag (
    name,             -- 中文说明：标准标签名称，目标字段 meme_tag.name
    category,         -- 中文说明：标签分类代码，目标字段 meme_tag.category
    sort_order,       -- 中文说明：分类内展示顺序，目标字段 meme_tag.sort_order
    related_quantity  -- 中文说明：标签关联梗数量，目标字段 meme_tag.related_quantity，初始值为0
)
values
    ('B站', 'SOURCE', 1, 0), ('抖音', 'SOURCE', 2, 0), ('微博', 'SOURCE', 3, 0), ('贴吧', 'SOURCE', 4, 0), ('知乎', 'SOURCE', 5, 0), ('小红书', 'SOURCE', 6, 0), ('游戏', 'SOURCE', 7, 0), ('动漫', 'SOURCE', 8, 0), ('影视', 'SOURCE', 9, 0),
    ('谐音', 'FORMAT', 1, 0), ('空耳', 'FORMAT', 2, 0), ('缩写', 'FORMAT', 3, 0), ('表情包', 'FORMAT', 4, 0), ('名场面', 'FORMAT', 5, 0), ('句式梗', 'FORMAT', 6, 0), ('热词', 'FORMAT', 7, 0), ('弹幕', 'FORMAT', 8, 0), ('鬼畜', 'FORMAT', 9, 0), ('二创', 'FORMAT', 10, 0), ('口头禅', 'FORMAT', 11, 0), ('梗图', 'FORMAT', 12, 0),
    ('搞笑', 'EMOTION', 1, 0), ('抽象', 'EMOTION', 2, 0), ('无语', 'EMOTION', 3, 0), ('震惊', 'EMOTION', 4, 0), ('破防', 'EMOTION', 5, 0), ('治愈', 'EMOTION', 6, 0), ('怀旧', 'EMOTION', 7, 0), ('阴阳', 'EMOTION', 8, 0), ('尴尬', 'EMOTION', 9, 0), ('悲伤', 'EMOTION', 10, 0), ('热血', 'EMOTION', 11, 0), ('可爱', 'EMOTION', 12, 0), ('自嘲', 'EMOTION', 13, 0),
    ('聊天', 'SCENARIO', 1, 0), ('评论区', 'SCENARIO', 2, 0), ('校园', 'SCENARIO', 3, 0), ('职场', 'SCENARIO', 4, 0), ('恋爱', 'SCENARIO', 5, 0), ('友情', 'SCENARIO', 6, 0), ('追星', 'SCENARIO', 7, 0), ('消费', 'SCENARIO', 8, 0), ('旅游', 'SCENARIO', 9, 0), ('考试', 'SCENARIO', 10, 0), ('加班', 'SCENARIO', 11, 0), ('社交', 'SCENARIO', 12, 0),
    ('人物', 'CONTENT', 1, 0), ('明星', 'CONTENT', 2, 0), ('动物', 'CONTENT', 3, 0), ('猫', 'CONTENT', 4, 0), ('二次元', 'CONTENT', 5, 0), ('游戏角色', 'CONTENT', 6, 0), ('生活', 'CONTENT', 7, 0), ('社会热点', 'CONTENT', 8, 0),
    ('经典老梗', 'ERA', 1, 0), ('00年代', 'ERA', 2, 0), ('2010-2015', 'ERA', 3, 0), ('2016-2020', 'ERA', 4, 0), ('2021-2023', 'ERA', 5, 0), ('2024', 'ERA', 6, 0), ('2025', 'ERA', 7, 0), ('2026', 'ERA', 8, 0),
    ('全网爆火', 'PROPAGATION', 1, 0), ('B站热门', 'PROPAGATION', 2, 0), ('短视频热门', 'PROPAGATION', 3, 0), ('年度热词', 'PROPAGATION', 4, 0), ('弹幕梗', 'PROPAGATION', 5, 0), ('地域梗', 'PROPAGATION', 6, 0), ('方言梗', 'PROPAGATION', 7, 0), ('圈内梗', 'PROPAGATION', 8, 0), ('过气梗', 'PROPAGATION', 9, 0),
    ('夸赞', 'TONE', 1, 0), ('吐槽', 'TONE', 2, 0), ('反讽', 'TONE', 3, 0), ('调侃', 'TONE', 4, 0), ('鼓励', 'TONE', 5, 0), ('拒绝', 'TONE', 6, 0), ('惊讶', 'TONE', 7, 0), ('抱怨', 'TONE', 8, 0), ('嘲讽', 'TONE', 9, 0);
