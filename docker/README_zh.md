本目录提供一套用于本地联调的 docker-compose 环境，包含上游服务和浏览器控制台。

## 使用步骤
- 进入目录并启动：
  - `cd docker`
  - 将 `.env.example` 复制为 `.env` 并按需修改
  - 可直接参考 `config.multi-route.example.json`，这是一个面向 upstream `9000/9001/9002` 的 `4.0` 多路由配置示例
  - `docker compose -f docker-compose.client.yml up -d`（控制台 + 上游）
  - 或 `docker compose -f docker-compose.upstream.yml up -d upstream`（仅上游）
- 控制台地址：`http://localhost:8080`
- 在 IDE 中运行 Intercept Wave 插件时，建议使用以下端口：
  - HTTP：8888(/api)、8889(/order-api)、8890(/pay-api)
  - WS：8891、8892、8893
  - 上游服务由容器提供：HTTP 9000/9001/9002，WS 9003/9004/9005

## 环境变量（`.env`）
- 控制台容器通过 `.env`（`env_file`）读取运行时配置。这些 URL 会在浏览器中使用，因此这里的 `localhost` 指向的是你的宿主机，也就是插件运行的位置。
- 默认示例参见 `.env.example`（基于 localhost），内容包括：
  - `HTTP_1`、`HTTP_2`、`HTTP_3`：指向本机插件的 HTTP 入口
  - `WS_1`、`WS_2`、`WS_3`：指向本机插件的 WS 入口
  - `WS_TOKEN`：控制台用于 WS 连接的 token

## 说明
- 如果你确实想使用 `host.docker.internal`，请注意它主要是容器侧的主机别名；宿主机上的浏览器通常无法解析这个域名。为了让浏览器稳定访问插件，更推荐在 `.env` 中使用 `localhost`。
