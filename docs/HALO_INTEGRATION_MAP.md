# Halo integration map — authorized source integration checkpoint

## 2026-10-22 continuation update

The user authorized an editable target at `C:\Users\slavd\HaloEco\Halo-main`. The Halo tree now contains uncommitted source changes for the signature permission + caller/package/signature checks, opt-in consent, AIDL service, model inventory and verified artifact file leases, durable replay journal, separate `:modelbench` process, fail-fast central resource lease and a fixed LiteRT CPU text fixture. The Lab tree contains a matching AIDL definition, bounded gateway source and `AppContainer` exposure. Both `protocol/halo-lab-bridge-v1.json` files currently hash to `264f430a5cd7b399f82130347572c72494a6a51f70a48f0eddcf83bc31090c3e`.

This source handshake advertises only inventory, artifact leases, event replay, and the fixed FAST/CPU fixture. Arbitrary prompt, acquisition, speech, embedding, vision and tool dispatch remain unsupported. The Lab session coordinator/UI/event importer still does not invoke the gateway, so the end-to-end real session remains unavailable. Build / Automated tests / Phone tests are NOT_RUN. Signing compatibility, generated AIDL, runtime behavior, worker death handling, and real measurements remain unverified.

The prior read-only map below records the original source seam and remains useful historical context; its former `BLOCKED_HALO_WRITE_ACCESS` state has been superseded by authorization to edit this checkout. The Halo endpoint and Lab real-run orchestration remain source-in-progress and unverified.

Inspection date: 2026-10-22 (Europe/Riga)

Halo checkout inspected: `C:\Users\slavd\HaloEco\Halo-main`, HEAD `2d7b650faa3ade3c00f504f13bf9f91495d6b73d`. The checkout contained pre-existing modifications and untracked files at inspection time. Inspection did not modify Halo.

| Existing symbol | Current source location | Integration responsibility | Boundary to preserve |
|---|---|---|---|
| `LocalModelCatalogRepository` | `app/src/main/java/com/halo/app/ai/LocalModelCatalogRepository.kt` | Projection over `AiModelCatalogRepository` and `LiteRtModelManager` | LiteRT listing does not imply speech, embedding, STT, or VAD coverage. |
| `AiModelRole` | `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AiRoles.kt` | Existing generic Halo roles are `CHAT`, `FAST`, `REASONING`, `AGENTIC`, and `VISION` | Lab mapping version 1 maps `THINK→REASONING` and `ACTION→AGENTIC`; STT/TTS/EMBEDDING are unsupported by this generic enum. |
| `LiteRtModelManager` | `providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelManager.kt` | Existing LiteRT inventory, verification, and acquisition owner | Halo remains the sole owner of bytes; Lab must not copy/cache weights. |
| `LocalRoleEvidenceController` | `app/src/main/java/com/halo/app/ai/LocalRoleEvidenceController.kt` | Existing scoped role evidence and explicit recommendation application | A lab finding must not silently reassign a production role. |
| `SupertonicPackStore` and `SupertonicSpeechProvider` | `app/src/main/java/com/halo/app/audio/SupertonicPackStore.kt`; `SupertonicSpeechProvider.kt` | Existing speech pack owner and synthesis provider | Expose inventory/leases through owners; do not move packs or make a parallel TTS runtime. |
| `HaloApplication` | `app/src/main/java/com/halo/app/HaloApplication.kt` | Main application startup and process-aware dependency initialization | A future `:modelbench` process needs an audited minimal bootstrap; none is implemented by this Lab checkout. |
| `EngineGraph` and `ManualDependencyGraph` | `app/src/main/java/com/halo/app/di/EngineGraph.kt`; `ManualDependencyGraph.kt` | Current adapter/dependency construction seams | Worker must reuse actual local adapters through factories without Activity/ViewModel routing. |
| Halo manifest and `app/build.gradle.kts` | `app/src/main/AndroidManifest.xml`; `app/build.gradle.kts` | Actual package, variant, permissions, components, dependencies | The manifest package/application ID must be reread from the authorised working tree before bridge work. |

These are names and locations observed in the current dirty checkout, not proof that a bridge, shared admission gate, model lease, supervisor, or worker exists. No compatibility claim is made from the reference snapshot alone. No Halo signing certificate digest or trust policy was obtained or stored.

The Lab's `protocol/lab-client-contract-v1.json` repeats only the client role/backend names and bounds needed to keep persisted plans readable. It is explicitly `CLIENT_DRAFT_ONLY`; C2 must freeze the canonical wire schema with an authorised Halo source before claiming protocol compatibility.

## Blocked boundary

At the initial takeover there was no owner-provided editable Halo target, so that audit stopped at `BLOCKED_HALO_WRITE_ACCESS`. The owner later authorized this checkout; the active integration is described above and remains unverified. No simulated bridge or fabricated measurement is used.
