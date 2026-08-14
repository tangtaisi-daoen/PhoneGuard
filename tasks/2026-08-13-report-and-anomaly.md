# 使用报告与异常中心任务

- [x] 审计现有 usage/events 数据链路和 KidSafe 可借鉴范围
- [x] 完成本阶段规格
- [x] 新增报告聚合、趋势和异常去重纯 Kotlin 逻辑
- [x] 新增报告页摘要、Top 应用、分类和趋势展示
- [x] 新增异常中心分级、合并和建议动作
- [x] 抑制被控端心跳重复上报同类异常
- [x] 运行测试、双端构建和 lint
- [x] 完成五轴评审

## 验证记录

- `gradlew test :admin:assembleDebug :kid:assembleDebug lint --no-daemon`：通过（2026-08-13）。
- 报告查询取最多 100 条后按 `reportedAt/date` 本地排序，避免依赖 CloudBase 默认顺序。
- 未修改 CloudBase schema，未新增敏感权限或第三方依赖。
