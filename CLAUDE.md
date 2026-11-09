@AGENTS.md

Claude Code는 위 AGENTS.md의 규칙을 그대로 읽고 따릅니다.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`KimYeongBean/iter-pleos`), managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: root `GLOSSARY.md` + `docs/adr/`. See `docs/agents/domain.md`.

### Project docs

Overall planning and development docs live in the Notion **Iter** page. Read them with the Notion MCP tools before planning or specifying work. See `docs/agents/project-docs.md`.
