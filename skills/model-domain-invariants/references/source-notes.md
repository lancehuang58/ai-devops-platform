# 參考來源與適用範圍

分析日期：2026-10-21。參考 dddcleankanban master 的 commit `9bd34d03a32f266d705313b5347479cd3fad6972`。
以下規則是對原始碼的分析與本專案的取捨，不宣稱參考專案已具備所有可靠性機制。

| 原始碼 | 可參考的設計 |
|---|---|
| [pom.xml](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/pom.xml) | dddcore、account、board、team、monomain 五個 Maven modules |
| [AggregateRoot.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/dddcore/src/main/java/ntut/csie/sslab/ddd/model/AggregateRoot.java) | Aggregate 收集 DomainEvent |
| [CreateBoardUseCase.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/board/src/main/java/ntut/csie/sslab/kanban/board/usecase/port/in/create/CreateBoardUseCase.java) | Use-case input interface |
| [CreateBoardService.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/board/src/main/java/ntut/csie/sslab/kanban/board/usecase/service/CreateBoardService.java) | 注入 Repository 與 event bus；案例協調 |
| [BoardRepositorySpringJpaAdapter.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/board/src/main/java/ntut/csie/sslab/kanban/board/adapter/out/repository/BoardRepositorySpringJpaAdapter.java) | JPA adapter 和 domain mapping |
| [NotifyBoardGoogleEventBusAdapter.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/board/src/main/java/ntut/csie/sslab/kanban/board/adapter/in/eventbus/NotifyBoardGoogleEventBusAdapter.java) | 事件 adapter 轉呼叫 use-case interface |
| [BoardDomainEventTest.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/board/src/test/java/ntut/csie/sslab/kanban/board/entity/BoardDomainEventTest.java) | Domain 行為與事件斷言 |
| [MonoMain.java](https://gitlab.com/TeddyChen/dddcleankanban/-/blob/9bd34d03a32f266d705313b5347479cd3fad6972/monomain/src/main/java/ntut/csie/sslab/ezkanban/main/springboot/web/MonoMain.java) | 組裝多模組為單一應用 |

## 採用時的修正

- 原專案 build dependency：account → dddcore；board → account／dddcore；team → board／account／dddcore；monomain 組裝。不是本專案 model/service/web 的物理分層。
- 原 CreateBoardService 先 save 再 memory publish；沒有因此保證 outbox 原子提交、crash recovery 或 exactly-once。
- GoogleEventBusAdapter.postAll 先清 aggregate events 再 publish；本專案不能照此當作可靠發送策略。
- 部分 use-case tests 繼承 Spring/JPA 測試基類；不全部是 unit tests。本專案將 integration tests 集中 web。
- 原 POM 有 testFailureIgnore=true，team event handlers 有 To be handled；不沿用這些行為或宣稱範例全功能完整。
- 本專案維持 Qwen、固定 CHANGE 流程、中央 Dispatcher、MCP > API > 公司 VM gateway、禁止 Agent 直連 SSH。MQ/ST 獨立，真實服務測試尚待環境。

在 ai-devops-platform 請另讀 doc/design/gradle-hexagonal-architecture.md、doc/design/dddcleankanban-architecture-analysis.md，以及 SD-15；需求政策以當前 SRS／SA 為準。
