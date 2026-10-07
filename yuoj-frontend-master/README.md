# YUOJ 在线判题系统 - 前端

根据后端接口文档（Knife4j：`http://localhost:8121/api/doc.html`）生成的前端项目。

## 技术栈

- Vue 3 + Vue Router 4 + Pinia
- Vite 4（构建）
- Arco Design Vue（UI 组件库）
- Axios（HTTP 请求）
- Monaco Editor（代码编辑器）
- marked + DOMPurify（Markdown 渲染）

## 功能

- 用户：注册、登录（Session Cookie）、个人中心
- 题目：题库浏览/搜索/标签筛选、题目详情、在线答题（Java / C++ / Go）、实时判题结果
- 提交：提交记录列表、代码与判题详情查看、自动刷新
- 管理端（仅 admin 角色可见）：题目管理（含测试用例、判题配置、题解）、用户管理（增删改、角色设置）
- 路由权限：未登录访问受限页面跳登录；非管理员访问管理页面显示 403

## 与后端的约定

- 后端接口统一前缀 `/api`，前端请求基于 `/api`（本地开发由 Vite 代理，生产由 Nginx 反代）
- 登录态基于 Cookie（Session），请求带 `withCredentials`
- 后端统一返回 `{ code, data, message }`，`code === 0` 为成功；`40100` 表示未登录/登录过期
- 角色：`user`（普通用户）、`admin`（管理员）
- 提交状态：`0` 待判题、`1` 判题中、`2` 成功、`3` 失败
- 判题配置单位：时间 `ms`，内存/栈 `KB`

## 本地开发

要求 Node.js 16+（推荐 18/20）。

```bash
# 1. 安装依赖
npm install

# 2. 启动开发服务器（默认 http://localhost:5173）
npm run dev
```

开发服务器已配置代理：`/api` → `http://localhost:8121`。
如果后端不在本机，修改 [vite.config.js](./vite.config.js) 中 `server.proxy['/api'].target`。

确保后端已启动且接口文档可访问：`http://localhost:8121/api/doc.html`。

## 构建

```bash
npm run build
```

产物输出到 `dist/` 目录，是纯静态文件，可放到任意 Web 服务器。

## 部署上线步骤

前端是纯静态资源，但接口走同源 `/api` 反向代理时才能正确携带登录 Cookie，
因此推荐“Nginx 托管静态文件 + 反代后端接口”的方案，或直接用仓库内的 Docker 方案。

### 方案一：Nginx 部署（推荐，前后端同机）

1. 本地执行 `npm run build`，得到 `dist/` 目录。
2. 把 `dist/` 上传到服务器，例如 `/opt/yuoj/frontend`。
3. 确认后端已在服务器上运行并监听 `127.0.0.1:8121`。
4. 在服务器安装 Nginx，并写入配置（示例见仓库根目录 [nginx.conf](./nginx.conf)，注意把
   `root` 改为你的实际前端目录）：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/yuoj/frontend;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8121;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        client_max_body_size 20m;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

5. 校验并重载 Nginx：

```bash
nginx -t
nginx -s reload
```

6. 访问 `http://your-domain.com` 即可。

> 说明：后端接口本身带有 `/api` 前缀，所以上面 `proxy_pass` 不带末尾 `/`，
> 请求 `/api/question/list/page` 会被原样转发到 `127.0.0.1:8121/api/question/list/page`。

### 方案二：Docker 部署

镜像内置 Nginx 并默认把 `/api` 代理到宿主机 `127.0.0.1:8121`。
如果后端在宿主机上（而非容器内），启动时加 `--network host` 或把后端地址换成宿主机可访问的地址：

```bash
# 构建镜像
docker build -t yuoj-frontend .

# 启动（后端在宿主机 127.0.0.1:8121 时）
docker run -d --name yuoj-frontend -p 80:80 --network host yuoj-frontend
```

如果你的后端不在宿主机、而是在其他服务器/容器，修改 [nginx.conf](./nginx.conf)
里的 `proxy_pass http://127.0.0.1:8121;` 为对应地址后重新构建。

### 方案三：前后端完全分离 / 部署到静态托管

如果使用 Vercel / OSS + CDN / 云托管等纯静态平台，前端代码无法与后端同源，
需要满足以下条件才能正常登录：

1. 后端开启 CORS，且允许携带凭证：
   `Access-Control-Allow-Origin` 为具体域名（不能用 `*`），
   `Access-Control-Allow-Credentials: true`；
2. Cookie 的 `SameSite` 与跨域、HTTPS 要求匹配（跨站通常需要 `SameSite=None; Secure`）；
3. 在请求层把 `/api` 指向后端地址（需另行配置环境变量或修改 `src/api/request.js` 的 baseURL）。

条件较多、易踩坑，非必要不推荐。

## 目录结构

```text
.
├─ src
│  ├─ api/                    # axios 请求封装 + 用户/题目接口
│  ├─ components/             # 通用组件（Markdown、代码编辑器、状态标签、题目表单）
│  ├─ constants/              # 语言/状态/角色等常量
│  ├─ router/                 # 路由与权限守卫
│  ├─ store/                  # Pinia 用户状态
│  ├─ styles/                 # 全局样式
│  ├─ utils/                  # 格式化等工具函数
│  └─ views/                  # 页面（首页、题库、做题、提交记录、用户、管理端）
├─ nginx.conf                 # Nginx 部署示例
├─ Dockerfile                 # Docker 构建示例
└─ vite.config.js             # Vite 配置（含开发代理）
```

## 常见问题

1. 登录后刷新页面掉登录：检查是否通过同源 `/api` 访问（开发走 Vite 代理、生产走 Nginx 反代），
   不要用跨域直连后端。
2. 管理员看不到“题目管理/用户管理”菜单：需要把该用户角色改为 `admin`（由后端/数据库设置）。
3. 提交后一直“判题中”：确认后端判题服务（沙箱/判题机）已启动；前端最多等待约 45 秒后提示超时。
4. 想新增支持语言：在 `src/constants/question.js` 中增加语言选项与默认代码模板，
   同时需要后端判题服务支持该语言。
