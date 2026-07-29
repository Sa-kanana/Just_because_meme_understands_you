# 管理端 API 文档

OpenAPI 文件：[`admin-openapi.yaml`](./admin-openapi.yaml)

## 导入 Apifox

管理端文档已写入 Apifox 项目 **`just_because_meme_understands_you-admin`**（projectId `8634632`）。

本地 OpenAPI 源文件仍为 [`admin-openapi.yaml`](./admin-openapi.yaml)，可用 CLI 再次同步：

```bash
apifox import --project 8634632 --format openapi --file ./docs/admin-openapi.yaml
```

历史业务项目 projectId `7875316`（`just_because_meme_understands_you`）为用户端接口，勿与管理端混淆。

## 约定

- 成功：`code === 1`
- 鉴权：`Authorization: Bearer <token>`
- `/admin/**` 仅 `ROLE_ADMIN`
- 分页：`page` 默认 1，`size` 默认 10、上限 50
- Long 型 id JSON 为字符串

## 知识库 RAG

管理端「知识库」对应：

| 层 | 说明 |
|----|------|
| 对外 API | `GET/POST/PUT/DELETE /admin/knowledge`、`POST /admin/knowledge/upload`（MD/PDF）、`POST /admin/knowledge/{id}/reindex` |
| MySQL SSOT | 表 `admin_knowledge` |
| Agent | `POST /ingest/knowledge`（LangChain 切块 + embedding → `knowledge_vector_chunk`） |
| 检索 | Agent 工具 `search_admin_knowledge` |