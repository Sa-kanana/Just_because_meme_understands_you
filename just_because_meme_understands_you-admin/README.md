# just_because_meme_understands_you-admin

「只因梗懂你」管理端（Vue 3 + Arco Design Vue + Pinia + Vue Router）。

## 开发

```bash
npm install
npm run serve
```

默认地址：`http://localhost:5174`，API 经 `/api` 代理到 `http://localhost:8080`。

## 登录

复用用户端 `POST /login`。仅 `ROLE_ADMIN` 可进入后台。本地可将某用户改为管理员：

```sql
UPDATE user SET role = 'ROLE_ADMIN' WHERE id = <your_user_id>;
```

## 接口文档

管理 API OpenAPI：仓库根目录 [`docs/admin-openapi.yaml`](../docs/admin-openapi.yaml)，可导入 Apifox。
