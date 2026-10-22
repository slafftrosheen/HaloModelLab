# C6 capability coverage matrix — source inspection

Inspection basis: authorized Halo worktree `C:\Users\slavd\HaloEco\Halo-main-hml`, commit `75da9ca49bfeea2da6fd914dde5380e8f6745e9d`; Lab protocol v1.0. This matrix records source APIs and Lab bridge availability. It is not device capability evidence.

| Capability | Existing Halo owner/API | Lab v1 status | Exact Lab-side blocker | Fixture/measurement gap |
|---|---|---|---|---|
| FAST text | `providers/litert/.../LiteRtLocalProvider.benchmarkModel`; `app/.../lab/worker/HaloModelBenchService.startTextProbe` | Fixed FAST/CPU smoke wired in source; per-model runtime check still required | Only `fast-smoke/1/short-ready` is accepted | One text fixture; does not qualify general instruction following |
| CHAT | `LiteRtLocalProvider.generate` | Contract only | Halo bridge advertises no generic prompt execution; capability snapshot supports only fixed FAST/CPU | No repeated-turn isolation runner |
| THINK | `LiteRtLocalProvider.generate` | Contract only | Same fixed-case bridge restriction | Arithmetic fixture contract only; no hidden chain-of-thought |
| ACTION | `LiteRtLocalProvider.probeToolCalling` | Contract only | No tool-call transport or fixture continuation operation | Pure validator compares fixture calls; no calls are dispatched |
| VISION | `LiteRtLocalProvider.probeVision` | Unsupported by Lab | No bounded image request, image artifact lease, or preprocessing identity in protocol | Halo has a synthetic vision probe; it is not connected to Lab evidence |
| STT | `providers/sherpa-stt/.../SherpaOfflineSpeechRecognitionProvider.recognize(AudioBuffer)` | Unsupported by Lab | No file-audio request contract or speech-pack lease | No pinned/licensed silence, short-speech, or long-speech fixture set |
| TTS | `SupertonicSpeechProvider.synthesizeToPcm`; `KokoroSpeechProvider.synthesizeToPcm` | Unsupported by Lab | No PCM synthesis request/result contract, voice-pack lease, or pack identity in Lab protocol | No bounded waveform metric policy; no playback is requested |
| Embeddings | `providers/litert/.../LiteRtEmbeddingProvider.embed` and `embedMedia` | Unsupported by Lab | No vector execution response or dimension/finiteness/repeatability metric contract | No pinned retrieval set or dimension policy in Lab |
| VAD | `ReloadableSileroVad` and `SherpaSpeechActivityEstimator` | Unsupported by Lab | No file-based labeled-segment request/result path | No pinned speech/silence intervals or tolerance fixture |

The current Lab real catalog shows these statuses with a reason. Halo API presence is not presented as Lab execution support. The protocol and separate worker remain text-only; C6 must add any further media operations through existing Halo owners, with their artifact leases, bounded inputs, cancellation, cleanup, and source/licensing evidence. No mic/camera permission or Lab weight cache is added.

C6 source acceptance is partial: coverage and concrete blockers are represented in the Lab registry/UI, but no multimodal case is claimed runnable. Build, automated tests after this C6 edit, and phone verification remain NOT_RUN.
