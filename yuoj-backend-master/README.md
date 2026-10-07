# YuOJ 判题系统后端

> 作者：han

基于 Spring Boot 的 OJ 在线判题系统后端，提供用户管理、题目管理、代码提交与自动判题等接口。

## 技术栈

- Spring Boot 2.7
- MyBatis Plus
- MySQL
- Redis（Session 分布式存储）
- Knife4j（接口文档）

## 快速开始

1. 修改 `src/main/resources/application.yml` 中的 MySQL、Redis 配置。
2. 执行 `sql/create_table.sql` 初始化数据库。
3. 按需启动代码沙箱服务，并在 `application.yml` 的 `codesandbox.type` 中指定沙箱类型（example / remote / thirdParty）。
4. 启动 `MainApplication`，访问 `http://localhost:8121/api/doc.html` 查看接口文档。

## 主要功能

- 用户注册、登录、注销、信息管理与权限控制
- 题目创建、编辑、删除、分页检索与脱敏展示
- 代码提交、判题策略与代码沙箱对接
