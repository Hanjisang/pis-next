# S04 Authentication & Organization Integration 设计

文档状态：FOUNDATION COMPLETE

真实 LDAP/AD/SSO/OAuth 联调：NOT VERIFIED

## 1. 统一身份链

S04-ID-001：PIS 复用现有 User、Role、Permission、Hospital、Campus，不重建 HR 或组织主数据系统。

```text
External Identity
  → IdentityProviderAdapter
  → ExternalIdentityLink
  → Authenticated User
  → DoctorIdentityResolver
  → DoctorIdentity + OrganizationContext
```

S04-ID-002：外部认证成功不等于获得 PIS 权限。外部 subject 必须先映射到已启用的本地 User，业务责任必须再解析为 DoctorIdentity。

S04-ID-003：DoctorIdentity 统一用于 Grossing doctor、Diagnosis INITIAL/REVIEW/AUDIT、Report signer 和 Assignment target。显示名不能替代稳定身份 ID/doctorCode。

## 2. 组织映射

`auth_user` 关联 Hospital Profile、Campus、Department；`doctor_identity` 关联 User 和 Department。登录结果包含 `OrganizationContext`，作为医院配置和数据范围选择依据。

组织引用不改变 V2 核心对象模型。医院组织目录的同步、停用和调岗通过身份/组织适配器处理。

## 3. Provider Adapter

| Provider | 当前状态 |
|---|---|
| MOCK | 合成自动化可用 |
| LDAP | Adapter shell，未配置 |
| AD | Adapter shell，未配置 |
| SSO | Adapter shell，未配置 |
| OAuth | Adapter shell，未配置 |

`identity_provider_configuration` 保存 Provider 选择和公开配置引用；`external_identity_link` 保存外部 subject 到本地 User 的映射；`external_authentication_event` 保存认证审计结果。密码、Token、Client Secret 和证书不得写入这些普通配置表。

S04-ID-004：未映射 subject、停用 User、停用 DoctorIdentity、组织不匹配或 Provider 未配置时，系统必须拒绝承担医疗责任。

S04-ID-005：映射新增、改绑和停用属于高风险操作，现场实现必须有双人审批或等效审计，不能由认证回调自动创建医生身份。

## 4. 运行环境安全

生产配置固定要求：

- `PIS_REQUIRE_AUTH=true`；
- `PIS_RUNTIME_ENV=production`；
- `production` 必须显式位于 `PIS_TRUSTED_RUNTIME_ENVIRONMENTS`；
- HTTPS 终止后设置 `PIS_AUTH_COOKIE_SECURE=true`；
- `PIS_AUTH_TEST_PASSWORD` 必须为空，不生成合成账号；
- 会话超时、反向代理和审计保存期按医院策略确认。

S04-ID-006：未列入受信白名单的运行环境即使用户已登录，也不能执行业务命令。

当前会话存储适用于单实例基础验证。若生产采用多实例，集中式会话或网关会话方案为待架构确认，不得在未验证状态下直接横向扩容。

## 5. 已验证与未验证

已验证：Mock 外部 subject 映射到既有 User、DoctorIdentity 和 Organization；未映射/未配置 Provider 拒绝；生产环境白名单授权行为；合成 Profile 的外键一致性。

未验证：真实目录查询、组/角色映射、账号停用时效、单点退出、MFA、密码策略、证书轮换、多实例会话和医院安全验收。
