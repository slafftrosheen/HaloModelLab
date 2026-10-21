# Halo integration map — read-only source inspection

Inspection date: 2026-10-22 (Europe/Riga)

Halo checkout inspected: `C:\Users\slavd\HaloEco\Halo-main`, HEAD `2d7b650faa3ade3c00f504f13bf9f91495d6b73d`. The checkout contained pre-existing modifications and untracked files at inspection time. Inspection did not modify Halo.

| Existing symbol | Current source location | Integration responsibility | Boundary to preserve |
|---|---|---|---|
| `LocalModelCatalogRepository` | `app/src/main/java/com/halo/app/ai/LocalModelCatalogRepository.kt` | Projection over `AiModelCatalogRepository` and `LiteRtModelManager` | LiteRT listing does not imply speech, embedding, STT, or VAD coverage. |
| `LiteRtModelManager` | `providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelManager.kt` | Existing LiteRT inventory, verification, and acquisition owner | Halo remains the sole owner of bytes; Lab must not copy/cache weights. |
| `LocalRoleEvidenceController` | `app/src/main/java/com/halo/app/ai/LocalRoleEvidenceController.kt` | Existing scoped role evidence and explicit recommendation application | A lab finding must not silently reassign a production role. |
| `SupertonicPackStore` and `SupertonicSpeechProvider` | `app/src/main/java/com/halo/app/audio/SupertonicPackStore.kt`; `SupertonicSpeechProvider.kt` | Existing speech pack owner and synthesis provider | Expose inventory/leases through owners; do not move packs or make a parallel TTS runtime. |
| `HaloApplication` | `app/src/main/java/com/halo/app/HaloApplication.kt` | Main application startup and process-aware dependency initialization | A future `:modelbench` process needs an audited minimal bootstrap; none is implemented by this Lab checkout. |
| `EngineGraph` and `ManualDependencyGraph` | `app/src/main/java/com/halo/app/di/EngineGraph.kt`; `ManualDependencyGraph.kt` | Current adapter/dependency construction seams | Worker must reuse actual local adapters through factories without Activity/ViewModel routing. |
| Halo manifest and `app/build.gradle.kts` | `app/src/main/AndroidManifest.xml`; `app/build.gradle.kts` | Actual package, variant, permissions, components, dependencies | The manifest package/application ID must be reread from the authorised working tree before bridge work. |

These are names and locations observed in the current dirty checkout, not proof that a bridge, shared admission gate, model lease, supervisor, or worker exists. No compatibility claim is made from the reference snapshot alone. No Halo signing certificate digest or trust policy was obtained or stored.

## Blocked boundary

This continuation has no owner-provided editable Halo target. Lab-side domain contracts, local validation, schemas, tests, profile definitions, qualification, and reporting may proceed. Implementing a real bridge endpoint, artifact mutation leases, production runtime admission, process bootstrap, native worker/supervisor, or existing-adapter integration requires Halo write access. Stop at that boundary with `BLOCKED_HALO_WRITE_ACCESS`; do not simulate a connected bridge or real measurements.
