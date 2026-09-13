# Contributing to KiwiRAG

感谢你愿意改进 KiwiRAG。请先通过 Issue 描述问题、目标与预期行为；较大的功能或架构变更
建议在编码前完成方案讨论。

## 本地检查

提交代码前，请根据改动范围运行相应检查：

```bash
make lint
make test

cd frontend
npm install
npm run test
npm run build
```

涉及检索、Prompt、上下文预算、拒答策略或 Agentic 路径的改动，还应执行对应评测并说明：

- 使用的数据集和配置；
- 与基线相比的指标变化；
- 是否存在成本、延迟或正确性退化；
- 原始结果和报告所在路径。

## Pull Request 要求

- 每个 PR 聚焦一个清晰目标，避免混入无关格式化或重构。
- 说明问题、实现方式、验证结果、风险和回滚办法。
- 新行为需要相应测试；修复缺陷时优先添加可复现该缺陷的回归测试。
- 不要提交 `.env`、API Key、Token、私有地址、用户文档或未经脱敏的日志。
- 修改用户可见行为、启动方式或配置项时，同步更新 README 或相关文档。

## Commit 建议

推荐使用简洁的 Conventional Commits 风格，例如：

```text
feat(retrieval): add metadata filter
fix(ingestion): recover expired parse lease
docs(readme): clarify minimal setup
test(eval): add multi-turn regression case
```

## 评测原则

KiwiRAG 不以复杂度作为目标。新增 Pipeline、Agent 步骤或模型调用只有在预先定义的端到端指标中
显示出足以覆盖延迟、成本和可靠性代价的收益，才适合进入默认路径。
