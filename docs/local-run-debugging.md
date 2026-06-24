# 服务端与前端本地运行调试指南

本文只覆盖本地开发时如何启动、联调、断点和验证。完整能力说明见 `docs/start.md`。

## 1. 环境检查

后端需要 JDK 17+ 和 Maven 3.9 或兼容版本。仓库没有 Maven wrapper，直接使用本机 `mvn`。

```bash
java -version
mvn -version
```

如果 `mvn -version` 里显示 Java 8，先在当前 shell 切到 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

Homebrew JDK 17 的常见路径：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

前端依赖当前 Vite 7，建议 Node `20.19+` 或 `22.12+`：

```bash
node -v
npm -v
```

## 2. 启动后端

最短启动路径：

```bash
mvn spring-boot:run
```

默认端口是 `8080`，默认模型 provider 是 `echo`，默认 Agent 是 `personal-assistant`。本地 smoke 不需要模型 API key，默认数据源是 H2 内存库。

如果希望显式使用个人本地目录配置：

```bash
mvn -Dspring-boot.run.profiles=personal spring-boot:run
```

只做离线启动 smoke 时可以跳过测试编译：

```bash
mvn -o -Dmaven.test.skip=true spring-boot:run
```

常用本地配置文件：

- `src/main/resources/application.yml`：默认端口、H2、模型 provider、Agent 配置。
- `src/main/resources/application-personal.yml`：个人本地 session 和 workspace 目录。
- `src/main/resources/application-development.yml`：development 覆盖配置。
- `src/main/resources/application-production.yml`：生产 profile 外部存储和观测配置。

切到真实模型 provider 时再配置对应 key：

```bash
export DASHSCOPE_API_KEY=...
export OPENAI_API_KEY=...
```

## 3. 后端 Smoke

启动成功后先验证基础接口：

```bash
curl -i 'http://localhost:8080/api/console/user?ownerId=owner-a&agentId=personal-assistant'
curl -s 'http://localhost:8080/api/sessions?ownerId=owner-a&agentId=personal-assistant'
curl -s 'http://localhost:8080/api/diagnostics/readiness/scenario'
curl -s 'http://localhost:8080/api/diagnostics/readiness/readiness-checks'
```

非流式聊天：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerId": "owner-a",
    "agentId": "personal-assistant",
    "sessionId": "session-a",
    "message": "帮我总结一下个人 Agent 能做什么",
    "knowledgeEnabled": false,
    "knowledgeLimit": 0
  }'
```

流式聊天：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerId": "owner-a",
    "agentId": "personal-assistant",
    "sessionId": "session-stream-a",
    "message": "请用三句话介绍个人 Agent 能力",
    "knowledgeEnabled": false,
    "knowledgeLimit": 0
  }'
```

本地请求不强制传 owner header；如果传 `X-Owner-Id`，它必须和请求里的 `ownerId` 一致。

## 4. 后端断点调试

IDE 直接调试：

1. 用 Maven 导入项目。
2. Project SDK 选择 JDK 17。
3. 新建 Spring Boot 或 Application 配置。
4. Main class 填 `com.harnessagent.HarnessAgentApplication`。
5. Working directory 填仓库根目录。
6. Active profiles 按需填 `personal` 或 `development`。

远程 attach 到应用 JVM：

```bash
mvn -Dspring-boot.run.profiles=personal \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
  spring-boot:run
```

然后在 IDE 里创建 Remote JVM Debug，host `localhost`，port `5005`。如果希望应用启动前等待断点，把 `suspend=n` 改成 `suspend=y`。

调试单个测试类：

```bash
mvn test -Dtest=ChatServiceTest
```

## 5. 启动前端

首次安装依赖推荐使用 lockfile：

```bash
cd web
npm ci
```

如果只是沿用现有 README 的本地安装方式，也可以使用：

```bash
cd web
npm install
```

启动 Vite：

```bash
npm run dev
```

Vite 默认端口是 `5173`，访问 `http://localhost:5173`。开发服务器监听 `0.0.0.0`，并把 `/api/**` 代理到 `http://localhost:8080`。真实联调时需要先启动后端。

如果后端端口不是 `8080`，同步调整 `web/vite.config.ts` 里的 proxy target。

## 6. 前端调试

默认本地身份是：

```text
ownerId: owner-a
agentId: personal-assistant
identityProvider: INTERNAL
```

浏览器调试重点看：

- DevTools Network 过滤 `/api`。
- 请求 body 或 query 里的 `ownerId`、`agentId`。
- 请求 header 里的 `X-Owner-Id`、`X-Identity-Provider`，部分 Agent 接口还有 `X-Agent-Id`。
- Console 中的前端异常。
- Sources 中直接给 TypeScript/React 源码打断点，Vite 默认支持 sourcemap 和 HMR。

如果 `5173` 被占用，可以临时换端口：

```bash
npm run dev -- --port 5174
```

换端口后浏览器改访问 `http://localhost:5174`。Playwright 配置默认使用 `5173`，跑浏览器测试时建议保持默认端口。

## 7. 前端测试与调试

单元测试：

```bash
cd web
npm run test:unit
```

单测 watch：

```bash
npx vitest --watch
```

调试指定单测：

```bash
npx vitest run src/api/client.test.ts
node --inspect-brk ./node_modules/vitest/vitest.mjs run
```

浏览器测试：

```bash
npm run test:browser
```

Playwright 会按 `web/playwright.config.ts` 自动启动 Vite，baseURL 是 `http://127.0.0.1:5173`。测试里会 mock 大部分后端 API，所以通常不依赖真实后端。

Playwright 调试：

```bash
npx playwright test --debug
npx playwright test tests/console.spec.ts --project=chromium-desktop --headed
npx playwright test tests/console.spec.ts --project=chromium-mobile --headed
npx playwright test --ui
npx playwright show-trace test-results/**/trace.zip
```

如果本机没有 Playwright 浏览器：

```bash
npx playwright install chromium
```

## 8. 推荐联调顺序

1. 终端 A 在仓库根目录启动后端：`mvn spring-boot:run`。
2. 终端 B 在 `web/` 启动前端：`npm run dev`。
3. 打开 `http://localhost:5173`。
4. 保持顶部身份为 `owner-a` 和 `personal-assistant`，先发一条普通聊天。
5. DevTools Network 检查 `/api/chat` 或 `/api/chat/stream` 请求是否到达 `localhost:8080`。
6. 如果要断后端，在 IDE attach 到 `5005` 后重发同一条请求。

## 9. 常见问题

- `invalid flag: --release` 或 `class file version 61.0`：当前 Maven 仍在使用 Java 8，重新切到 JDK 17。
- `/api` 请求失败：先确认后端在 `localhost:8080`，再看 Vite proxy target。
- UI 身份不一致：确认页面顶部 Owner/Agent、请求 body/query、`X-Owner-Id` 和 `X-Agent-Id` 一致。
- Node 版本不满足：升级到 Node `20.19+` 或 `22.12+`。
- `npm ci` 下载失败：确认当前 npm registry 能访问 lockfile 里的 registry。
- Playwright 跑到错误页面：确认 `5173` 上运行的是当前仓库的 Vite 服务。
- H2 数据消失：默认本地 H2 是内存库，重启后数据会重置；长期本地数据看 `.harness-agent/` 下的 local-json 状态。

## 10. 提交前验证

只改后端：

```bash
mvn test
```

只改前端：

```bash
cd web
npm run test:unit
npm run test:browser
npm run build
```

涉及个人术语或文档时：

```bash
node scripts/validate-personal-terms.mjs
```
