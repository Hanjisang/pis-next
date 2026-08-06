# PIS Next

Next-generation Pathology Information System.

当前阶段：P19 诊断与报告已完成并通过关闭审查。

P19 已实现诊断任务、责任链、初诊草稿与诊断版本、复诊/复核、报告草稿、结构化报告业务版本、独立审核、增强认证参考边界、签发事实、补充、更正、撤回和重新签发关系。实现入口见 [`docs/implementation/p19-implementation-scope.md`](docs/implementation/p19-implementation-scope.md) 与 [`docs/reviews/p19-consistency-review.md`](docs/reviews/p19-consistency-review.md)。

P15-P18 的登记接收、取材蜡块、组织处理包埋和技术医嘱保持回归兼容。P19 不实现实际切片/染色、数字切片、AI、冰冻执行、医院接口、PDF 生产、CA/电子签章供应商或生产部署，因此当前系统仍不可用于临床生产。

Windows 本地验证：

```powershell
$env:PIS_DB_PASSWORD = 'change-me-local-only'
.\scripts\verify.ps1
```

项目文档入口：[`docs/index.md`](docs/index.md)。
