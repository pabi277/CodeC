# Level 6 — Optional on-device model

**Status: proposed; separate technical investigation required. Offline inference is optional.**

## User value

A user may download a compatible model and use it without sending prompts/project contents to a cloud model provider. API mode remains available and is not silently replaced.

## Runtime candidates to evaluate

- [llama.cpp](https://github.com/ggml-org/llama.cpp): native C/C++ inference, broad GGUF model ecosystem, flexible quantization. Android JNI integration and native packaging are meaningful maintenance and APK/build costs.
- [Google LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM): Android/Kotlin APIs, model/runtime constraints and hardware delegates that may fit a native app. Validate the exact supported Android versions, model formats, device delegates, tool-call behavior, and release stability.

Do not add both runtimes by default. Prototype/evaluate candidates against CodeC's ABI matrix, minSdk, existing C/C++/JNI build, release APK size, R8/packaging, thermal behavior, and app lifecycle. Pick one only after evidence. Neither the user's `pkg` environment nor an Android device's system AI capability should be assumed to be an app-usable inference runtime.

## Capability check should be empirical

A preflight can inspect supported ABI, Android/runtime compatibility, available memory/storage, and model metadata. These signals are only estimates. Actual load and a short local benchmark are needed before recommending a model. Available RAM changes as other apps and Android compete for memory; model weights are not the full memory footprint because context/KV cache and runtime buffers also consume memory.

Use honest outcomes such as **Ready**, **May be slow**, **Insufficient storage**, **Not compatible**, or **Needs a real-device test**. Keep model download opt-in, show file size and storage requirement, verify downloads, allow deletion, and never download a multi-gigabyte model in the background without consent.

## Coding-agent suitability is a separate test

A model that can chat is not necessarily a dependable coding agent. Evaluate local models for:

- correct structured tool-call selection and arguments;
- following project scope and refusing disallowed tools;
- useful multi-file reasoning within a realistic context window;
- edit proposal quality and ability to use compiler/run feedback;
- first-token latency, generation speed, memory pressure, battery/thermal impact, and stability.

The app's policy engine remains authoritative even if a model emits malformed or unsafe tool calls. If the local model does not reliably follow the tool contract, keep it available for explanation/read-only tasks rather than agent execution.

## Offline promise

After the model is installed, local inference should make no AI-provider network request. Separate optional operations such as model catalog refresh/download from inference, clearly label them, and verify offline behavior with network monitoring. Do not claim the full app is offline if other user-triggered services are used.

## Acceptance checks

- Device preflight does not overstate capability and fails gracefully under memory pressure.
- Users can skip, pause/resume if supported, verify, delete, and retry model downloads.
- Local prompts stay local; API fallback never occurs without user selection and disclosure.
- Unsupported ABI/device/runtime/model combinations have a clear explanation.
- Measure on representative low/mid/high-tier phones, not only emulator or one flagship.
