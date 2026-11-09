# Project docs: Notion

The overall planning and development docs for Iter live in Notion, not in this repo.
Read them from the Notion **Iter** page with the Notion MCP tools (`notion-fetch`, `notion-search`).

- Root page: [Iter — 하루 주행 데이터를 그래프 아트와 레이싱 맵으로 기록하는 앱](https://app.notion.com/p/40d8297f075b81b28b06dbce9bfd4b9c)
- Every doc is a row of the root page's `문서` database (`collection://5e3796bf-36a9-4022-8216-5f40f62abb6f`). If a page below moved or a new one was added, fetch the root page and follow its `문서` list.

## Which page to read

| Need | Notion page |
|---|---|
| Problem, target users, feature priority, app flow | [기획](https://app.notion.com/p/40e8297f075b815c9e8cef67fa21867a) |
| New ideas (not decided) | [브레인스토밍](https://app.notion.com/p/40e8297f075b8139b07cdcaf19641653) |
| Screen list, screen layout, wireframes | [디자인](https://app.notion.com/p/40e8297f075b817c9f4afbe8aaf7665f) |
| Modules, data structures, flows, implementation units, edge cases, privacy | [개발](https://app.notion.com/p/40d8297f075b8106addce3ec4f1c2624) |
| Pleos basics, SDK features, signals, permissions | [Pleos 참고](https://app.notion.com/p/40e8297f075b81ba8b96d5f8e9c3f576) |
| Schedule, risks, open questions, decision log | [운영](https://app.notion.com/p/40e8297f075b8168a210c911dd39cbd2) |
| Rules for agents working in Notion | [AI 에이전트 작업 규칙](https://app.notion.com/p/40e8297f075b817f950efe33a3eed947) |

## When to read

- Before `/to-spec`, `/to-tickets`, `/triage`, `/grill-with-docs`, or planning any issue: read 기획 and 개발 for the area you touch, and check 운영's decision log and open questions.
- Before UI work: read 디자인. Before Pleos SDK work: read Pleos 참고.

## Rules

- **Notion is read-only from repo work.** Do not edit Notion while working on a repo issue unless a human asks. If you must, first read and follow [AI 에이전트 작업 규칙](https://app.notion.com/p/40e8297f075b817f950efe33a3eed947) (most pages are comment-only for agents).
- Only items in 운영's decision log (`결정사항 로그`) are decided. Treat 기획/개발 content with status `초안` or `논의 중`, and anything in 브레인스토밍, as not yet decided.
- Repo docs win for code contracts: `GLOSSARY.md`, `docs/interfaces.md`, and `docs/adr/` are what code must follow. If Notion and the repo disagree, don't pick one silently — stop and ask a human, and note it in the issue.
- Don't copy Notion content into the repo. Link to the Notion page instead.
- This repo is public. Don't paste Notion content that is internal (team member details, schedules, unreleased decisions) into issues, PRs, or commits; link instead.
