# RESTful 接口清单

本清单记录本次改造后的接口。后端实际路由以 `/v1` 开头；两个前端默认请求基址为 `/api/v1`，本地代理会移除 `/api` 后转发给后端。所有旧路由均已移除，不提供兼容转发。

请求和响应仍使用项目现有 JSON 格式 `{ code, message, data }`。分页参数以 `page`、`size` 为主；具体业务字段沿用现有 DTO/VO。

## 用户端

| 方法 | 路由 | 用途 / 参数 |
| --- | --- | --- |
| GET | `/v1/captchas` | 获取图形验证码 |
| POST | `/v1/auth/sessions` | 登录 |
| PUT | `/v1/auth/sessions/current` | 刷新当前登录会话 |
| DELETE | `/v1/auth/sessions/current` | 退出当前会话 |
| GET | `/v1/auth/oauth/github/authorizations` | 获取 GitHub OAuth 授权地址；`redirect` 查询参数 |
| GET | `/v1/auth/oauth/github/callbacks` | GitHub OAuth 回调；接收 OAuth 回调参数 |
| POST | `/v1/auth/oauth/sessions` | 用 OAuth ticket 创建登录会话 |
| POST | `/v1/users` | 注册用户 |
| POST | `/v1/registration-verification-codes` | 发送注册验证码 |
| POST | `/v1/auth/password-reset-requests` | 发起密码重置 |
| POST | `/v1/auth/password-reset-verifications` | 校验密码重置验证码 |
| PUT | `/v1/auth/password-resets` | 提交新密码 |
| GET | `/v1/users/{userId}` | 查询用户资料 |
| GET | `/v1/users/{userId}/memes` | 查询用户发布的梗；支持分页和状态筛选 |
| GET | `/v1/users/{userId}/favorites` | 查询用户公开收藏；支持 `folderId`、分页 |
| GET | `/v1/users/me` | 查询当前用户资料 |
| PUT | `/v1/users/me` | 更新当前用户资料 |
| POST | `/v1/users/me/avatar` | 更新当前用户头像 |
| GET | `/v1/users/me/settings` | 查询账号设置 |
| GET | `/v1/users/me/security` | 查询账号安全信息 |
| PUT | `/v1/users/me/password` | 登录后修改密码 |
| DELETE | `/v1/users/me/sessions` | 退出所有设备 |
| GET | `/v1/home` | 获取首页聚合数据；支持 `hotLimit`、`tagLimit`、`feedSort`、`feedSize` |
| GET | `/v1/home/images` | 获取首页轮播图 |
| GET | `/v1/home/popular-tags` | 获取热门标签；支持 `limit` |
| GET | `/v1/memes` | 查询梗列表；支持分页、排序、标签等筛选 |
| GET | `/v1/meme-searches` | 搜索梗；支持 `keyword`、`mostViews`、`mostLikes`、`mostComments` |
| GET | `/v1/memes/{memeId}` | 查询梗详情 |
| GET | `/v1/tags` | 查询可用标签 |
| POST | `/v1/memes` | 发布梗 |
| DELETE | `/v1/memes/{memeId}` | 软删除本人发布的梗 |
| POST | `/v1/memes/{memeId}/restorations` | 提交恢复梗的请求 |
| DELETE | `/v1/memes/{memeId}?permanent=true` | 永久删除已下架梗 |
| POST | `/v1/meme-views` | 上报浏览事件；请求体携带 `memeId`、`source` |
| GET | `/v1/memes/{memeId}/views/count` | 查询单个梗浏览量 |
| GET | `/v1/meme-view-counts` | 批量查询浏览量；`memeIds` 为逗号分隔列表 |
| PUT | `/v1/users/me/liked-memes/{memeId}` | 点赞梗 |
| DELETE | `/v1/users/me/liked-memes/{memeId}` | 取消梗点赞 |
| GET | `/v1/users/me/liked-memes/{memeId}` | 查询梗点赞状态 |
| GET | `/v1/users/me/liked-memes` | 批量查询点赞状态；`memeIds` 为逗号分隔列表 |
| PUT | `/v1/users/me/favorites/{memeId}` | 收藏梗；请求体含 `folderId` |
| DELETE | `/v1/users/me/favorites/{memeId}` | 取消收藏 |
| GET | `/v1/users/me/favorites/{memeId}` | 查询收藏状态 |
| PATCH | `/v1/users/me/favorites/{memeId}` | 移动单个收藏；请求体含 `targetFolderId` |
| PATCH | `/v1/users/me/favorites` | 批量移动收藏；请求体含 `targetFolderId`、`memeIds` |
| PUT | `/v1/users/me/favorites/order` | 调整收藏梗顺序 |
| GET | `/v1/users/me/favorite-folders` | 查询当前用户收藏夹 |
| GET | `/v1/users/{userId}/favorite-folders` | 查询用户公开收藏夹 |
| POST | `/v1/users/me/favorite-folders` | 创建收藏夹 |
| PUT | `/v1/users/me/favorite-folders/{folderId}` | 更新收藏夹 |
| DELETE | `/v1/users/me/favorite-folders/{folderId}` | 删除收藏夹 |
| PUT | `/v1/users/me/favorite-folders/order` | 调整收藏夹顺序 |
| PUT | `/v1/users/{userId}/followers/me` | 关注用户 |
| DELETE | `/v1/users/{userId}/followers/me` | 取消关注 |
| GET | `/v1/users/{userId}/followers/me` | 查询当前用户对目标用户的关注状态 |
| GET | `/v1/users/me/following` | 批量查询关注状态；`userIds` 为逗号分隔列表 |
| GET | `/v1/users/{userId}/following` | 查询用户关注列表 |
| GET | `/v1/users/{userId}/followers` | 查询用户粉丝列表 |
| GET | `/v1/users/me/followed-tags` | 查询当前用户关注的标签 |
| PUT | `/v1/users/me/followed-tags/{tagId}` | 关注标签 |
| DELETE | `/v1/users/me/followed-tags/{tagId}` | 取消关注标签 |
| GET | `/v1/users/me/suppressions` | 查询屏蔽用户列表 |
| GET | `/v1/users/me/suppressions/{targetId}` | 查询与目标用户的屏蔽状态 |
| PUT | `/v1/users/me/suppressions/{targetId}` | 屏蔽用户 |
| DELETE | `/v1/users/me/suppressions/{targetId}` | 取消屏蔽用户 |
| GET | `/v1/memes/{memeId}/comments` | 查询梗的根评论；支持分页和排序 |
| GET | `/v1/comments/{rootId}/replies` | 查询根评论回复 |
| GET | `/v1/comments/{commentId}/context` | 查询评论定位上下文 |
| POST | `/v1/memes/{memeId}/comments` | 创建评论或回复；请求体含 `rootId`、`parentId`、`content`、`imageUrls` |
| DELETE | `/v1/comments/{commentId}` | 删除当前用户自己的评论（软删除） |
| PUT | `/v1/comments/{commentId}/likes/me` | 点赞评论 |
| DELETE | `/v1/comments/{commentId}/likes/me` | 取消评论点赞 |
| GET | `/v1/comments/{commentId}/likes/me` | 查询评论点赞状态 |
| GET | `/v1/comment-like-statuses` | 批量查询评论点赞状态；`commentIds` 为逗号分隔列表 |
| POST | `/v1/memes/{memeId}/reports` | 举报梗 |
| POST | `/v1/comments/{commentId}/reports` | 举报评论 |
| GET | `/v1/memes/{memeId}/knowledge` | 查询梗知识卡片 |
| POST | `/v1/memes/{memeId}/knowledge/corrections` | 提交知识纠错 |
| GET | `/v1/trends` | 查询趋势内容 |
| GET | `/v1/notifications` | 查询站内通知；支持分页、`type`、`tab` |
| GET | `/v1/notifications/counts` | 查询通知未读数 |
| PATCH | `/v1/notifications/{id}` | 将单条通知标记为已读 |
| PATCH | `/v1/notifications` | 批量标记已读；支持 `tab` |
| DELETE | `/v1/notifications/{id}` | 删除单条通知 |
| DELETE | `/v1/notifications` | 批量删除通知；请求体可含 `ids`、`clearAll`、`clearReadOnly` |
| GET | `/v1/uploads/policies` | 查询上传策略；支持 `fileType` |
| GET | `/v1/feedback/categories` | 查询反馈分类 |
| POST | `/v1/feedback` | 提交反馈 |
| GET | `/v1/help` | 查询帮助文档 |
| GET | `/v1/ai/sessions` | 查询当前用户的 AI 会话 |
| GET | `/v1/ai/sessions/{sessionId}/messages` | 查询 AI 会话消息 |
| DELETE | `/v1/ai/sessions/{sessionId}` | 删除 AI 会话 |
| POST | `/v1/ai/searches` | 创建 AI 搜索流；SSE 请求体含 `query`，可选 `sessionId`、`requestId` |

## 管理端

| 方法 | 路由 | 用途 / 参数 |
| --- | --- | --- |
| GET | `/v1/admin/home-images` | 查询首页轮播图 |
| POST | `/v1/admin/home-images` | 创建轮播图 |
| PUT | `/v1/admin/home-images/{id}` | 更新轮播图 |
| PATCH | `/v1/admin/home-images/{id}` | 更新轮播图状态 |
| DELETE | `/v1/admin/home-images/{id}` | 删除轮播图 |
| GET | `/v1/admin/knowledge-documents` | 分页查询知识文档 |
| GET | `/v1/admin/knowledge-documents/{id}` | 查询单篇知识文档 |
| POST | `/v1/admin/knowledge-documents` | 创建知识文档 |
| POST | `/v1/admin/knowledge-documents/uploads` | 上传并解析知识文档 |
| PUT | `/v1/admin/knowledge-documents/{id}` | 更新知识文档 |
| DELETE | `/v1/admin/knowledge-documents/{id}` | 删除知识文档 |
| POST | `/v1/admin/knowledge-documents/{id}/indexing-jobs` | 为知识文档创建索引任务 |
| GET | `/v1/admin/memes` | 分页查询梗及审核队列 |
| GET | `/v1/admin/memes/{id}` | 查询梗详情 |
| PATCH | `/v1/admin/memes/{id}` | 修改梗状态；请求体 `status` 为 `APPROVED`、`REJECTED`、`OFFLINE` 或 `ONLINE` |
| GET | `/v1/admin/meme-knowledge-corrections` | 查询知识纠错队列 |
| PATCH | `/v1/admin/meme-knowledge-corrections/{id}` | 处理知识纠错 |
| GET | `/v1/admin/reports` | 查询举报队列 |
| PATCH | `/v1/admin/reports/{id}` | 处理举报 |
| GET | `/v1/admin/sensitive-words` | 查询敏感词 |
| POST | `/v1/admin/sensitive-words` | 新增敏感词 |
| PUT | `/v1/admin/sensitive-words/{id}` | 更新敏感词 |
| PATCH | `/v1/admin/sensitive-words/{id}` | 更新敏感词状态 |
| DELETE | `/v1/admin/sensitive-words/{id}` | 删除敏感词 |
| GET | `/v1/admin/tags` | 查询标签 |
| POST | `/v1/admin/tags` | 新建标签 |
| PUT | `/v1/admin/tags/{id}` | 更新标签 |
| DELETE | `/v1/admin/tags/{id}` | 删除标签 |
| GET | `/v1/admin/users` | 查询用户 |
| PATCH | `/v1/admin/users/{id}` | 修改用户状态或角色；请求体必须且只能包含其中一项 |
| POST | `/v1/admin/ai/crawl-jobs` | 创建 AI 爬取任务；支持 `limit` |
| POST | `/v1/admin/ai/ingestion-jobs` | 创建 AI 知识回填任务；支持 `limit` |
| POST | `/v1/admin/ai/ingestion-jobs/memes/{memeId}` | 为指定梗创建 AI 入库任务 |
| POST | `/v1/admin/ai/ingestion-jobs/memes/{memeId}/approvals` | 创建指定梗的知识审核/入库任务 |
| GET | `/v1/admin/ai/operations` | 查询 AI 运维状态 |

## 主要方法变更

- 登录、刷新、退出统一为会话资源：`POST /auth/sessions`、`PUT /auth/sessions/current`、`DELETE /auth/sessions/current`。
- 对用户、梗、评论、收藏、关注、通知和管理资源使用复数资源名，并把 ID 放入路径。
- 关注、点赞、收藏加入关系使用 `PUT`，移除使用 `DELETE`；局部状态调整使用 `PATCH`。
- 梗详情、评论、查询状态和管理状态更新不再使用旧的动作式路由。
- 前端和管理端都已改为调用新路由；旧 API 地址不会重定向到新接口。
