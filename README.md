# He AI Code Mother（合智图 AI 代码母机）

> 基于 Spring Boot 3 + Vue 3 + LangChain4j 构建的 AI 驱动代码生成平台，支持多种代码生成模式与 SSE 流式输出。

---

## 项目简介

**He AI Code Mother** 是一个全栈 AI 代码生成平台。用户通过自然语言描述需求，平台调用大语言模型自动生成可运行的前端代码，并支持在线编辑、预览与一键部署。平台内置多种代码生成模式，覆盖从简单的单页 HTML 到复杂的 Vue 工程项目，适合快速原型开发与低代码场景。

---

## 核心功能

| 功能 | 说明 |
|------|------|
| **AI 代码生成** | 通过对话式交互，由 AI 自动生成前端代码，支持流式实时输出（SSE） |
| **多种生成模式** | 原生 HTML 单文件模式、原生多文件模式、Vue 工程模式，灵活满足不同场景 |
| **应用管理** | 用户可创建、编辑、删除自己的应用，支持分页查询与名称搜索 |
| **在线编辑器** | 内置代码编辑视图，支持代码实时预览与手动修改 |
| **应用部署** | 支持一键部署应用，生成可访问的部署 URL |
| **精选应用广场** | 首页展示管理员推荐的优质应用，供用户浏览与参考 |
| **对话记忆** | 基于 Redis 的对话记忆存储，AI 可保持多轮上下文连贯性 |
| **用户体系** | 注册、登录、权限管理（普通用户 / 管理员），基于 Spring Session + Redis 实现分布式会话 |
| **管理员后台** | 管理员可管理所有用户应用，设置推荐优先级、封面图等 |
| **API 文档** | 集成 Knife4j，提供完整的 OpenAPI 3.0 接口文档，开箱即用 |

---

## 技术亮点

### 后端
- **Spring Boot 3.5** + **Java 21**，使用最新 LTS 特性（`switch` 模式匹配等）
- **LangChain4j 1.1**：通过 `@SystemMessage` 注解加载 Prompt 模板，AiService 接口极简调用大模型
- **Reactor / SSE 流式输出**：`Flux<String>` 配合 `ServerSentEvent` 实现 token 级实时推送，无需轮询
- **门面模式（Facade）**：`AiCodeGeneratorFacade` 统一封装生成 + 解析 + 保存全流程，对外暴露单一入口
- **策略 + 模板方法模式**：`CodeParserExecutor` / `CodeFileSaverExecutor` 根据生成类型动态分发，新增模式只需扩展不需修改
- **MyBatis-Flex 1.11**：灵活的 ORM 框架，支持链式 `QueryWrapper`，内置代码生成器
- **Redis 双重用途**：Spring Session 分布式会话 + LangChain4j 对话记忆（`RedisChatMemoryStore`）
- **AOP 权限校验**：`@AuthCheck` 注解 + `AuthInterceptor` 切面，无侵入式角色鉴权
- **HikariCP** 高性能数据库连接池
- **Knife4j** 接口文档，支持中文界面与在线调试

### 前端
- **Vue 3.5** + **TypeScript** + **Vite 7**，现代化工程化体系
- **Ant Design Vue 4**：企业级 UI 组件库
- **Pinia 3**：轻量状态管理，`userStore` 管理登录态
- **Vue Router 5**：路由守卫实现登录拦截与权限校验
- **Axios**：封装请求拦截器，统一处理认证与错误
- **markdown-it + highlight.js**：AI 输出内容的 Markdown 渲染与代码高亮
- **SSE 客户端**：原生 `EventSource` 接收流式 AI 输出，实时渲染

---

## 项目结构

```
he-ai-code-mother/
├── src/main/java/com/hezitu/heaicodemother/
│   ├── ai/                     # AI 服务接口与模型定义（LangChain4j）
│   ├── annotation/             # 自定义注解（@AuthCheck）
│   ├── aop/                    # AOP 切面（权限拦截）
│   ├── common/                 # 通用响应封装、分页、工具类
│   ├── config/                 # CORS、Jackson、Redis 等配置
│   ├── constant/               # 常量定义
│   ├── controller/             # REST 控制层
│   ├── core/                   # 核心门面、代码解析器、文件保存器
│   │   ├── parser/             # 代码解析策略（HTML / 多文件）
│   │   └── saver/              # 代码文件保存策略
│   ├── exception/              # 全局异常处理
│   ├── model/                  # 实体、DTO、VO、枚举
│   ├── service/                # 业务逻辑层
│   └── mapper/                 # 数据访问层（MyBatis-Flex）
├── src/main/resources/
│   ├── prompt/                 # AI Prompt 模板文件
│   └── application.yml         # 主配置文件
he-ai-code-mother-frontend/
├── src/
│   ├── api/                    # 后端接口调用（user.ts / app.ts）
│   ├── components/             # 全局组件（Header、Footer）
│   ├── layouts/                # 页面布局
│   ├── router/                 # 路由配置与守卫
│   ├── stores/                 # Pinia 状态管理
│   └── views/                  # 页面视图组件
└── package.json
```

---

## 环境要求

| 依赖 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Node.js | 20.19.0+ 或 22.12.0+ |
| npm | 9+ |

---

## 快速部署

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd he-ai-code-mother
```

### 2. 配置数据库与 Redis

编辑 `src/main/resources/application.yml`，修改以下配置项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yu_ai_code_mother?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root          # 修改为你的数据库用户名
    password: 123456        # 修改为你的数据库密码
  data:
    redis:
      host: localhost
      port: 6379
      password:             # 如有密码请填写
```

> 数据库无需手动创建，首次启动时会自动创建 `yu_ai_code_mother` 数据库（需配置相应建表 SQL）。

### 3. 配置大模型 API Key

在 `application.yml`（或对应的 `application-local.yml`）中配置 OpenAI 兼容接口信息：

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: your-api-key-here
      model-name: gpt-4o          # 或其他兼容模型
      base-url: https://api.openai.com/v1  # 如使用代理地址请替换
```

### 4. 启动后端服务

```bash
# 使用 Maven Wrapper
./mvnw spring-boot:run

# 或使用本地 Maven
mvn spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

后端服务默认启动在 `http://localhost:8848/api`

API 文档地址：`http://localhost:8848/api/doc.html`（Knife4j 界面）

### 5. 启动前端服务

```bash
cd he-ai-code-mother-frontend

# 安装依赖
npm install

# 开发模式启动
npm run dev
```

前端默认启动在 `http://localhost:5173`

### 6. 生产构建（前端）

```bash
cd he-ai-code-mother-frontend
npm run build
```

构建产物在 `dist/` 目录，可部署到 Nginx 或直接由 Spring Boot 的静态资源控制器（`StaticResourceController`）托管。

---

## Docker 部署（推荐生产环境）

### 前置准备

确保已安装 Docker 与 Docker Compose。

### docker-compose.yml 示例

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: yu_ai_code_mother
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  backend:
    build: .
    ports:
      - "8848:8848"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/yu_ai_code_mother?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: 123456
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      - mysql
      - redis

volumes:
  mysql_data:
```

```bash
docker-compose up -d
```

---

## API 接口概览

| 模块 | 接口 | 说明 |
|------|------|------|
| 用户 | `POST /api/user/register` | 用户注册 |
| 用户 | `POST /api/user/login` | 用户登录 |
| 用户 | `POST /api/user/logout` | 退出登录 |
| 应用 | `POST /api/app/add` | 创建应用 |
| 应用 | `POST /api/app/update` | 更新应用 |
| 应用 | `POST /api/app/delete` | 删除应用 |
| 应用 | `POST /api/app/my/list/page/vo` | 我的应用列表 |
| 应用 | `POST /api/app/good/list/page/vo` | 精选应用列表 |
| 应用 | `GET /api/app/chat/gen/code` | SSE 流式代码生成 |
| 应用 | `POST /api/app/deploy` | 应用部署 |
| 聊天记录 | `POST /api/chat-history/...` | 聊天记录查询 |
| 管理员 | `POST /api/app/admin/...` | 管理员应用管理 |

> 完整接口文档请访问 `http://localhost:8848/api/doc.html`

---

## 代码生成模式说明

| 模式 | 枚举值 | 说明 |
|------|--------|------|
| 原生 HTML 模式 | `html` | 生成单个完整 HTML 文件，含内联 CSS 与 JS，无外部依赖 |
| 原生多文件模式 | `multi_file` | 生成 HTML / CSS / JS 分离的多文件结构 |
| Vue 工程模式 | `vue_project` | 生成完整 Vue 项目结构代码，支持多轮对话迭代 |

---

## 前端页面路由

| 路径 | 页面 | 权限 |
|------|------|------|
| `/` | 首页（精选应用广场） | 公开 |
| `/login` | 登录页 | 公开 |
| `/register` | 注册页 | 公开 |
| `/my-apps` | 我的应用列表 