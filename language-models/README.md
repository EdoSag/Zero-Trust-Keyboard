# Language models for ZT Keyboard

The files of this release are the language models ZT Keyboard can download for writing help
(Settings > Writing help): finishing a sentence and proofreading. The model runs on the
phone with [llama.cpp](https://github.com/ggml-org/llama.cpp); nothing typed is sent anywhere.

| File | Model | Licence |
|---|---|---|
| `qwen2.5-0.5b-instruct-q4_0.gguf` | [Qwen2.5-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF) by the Qwen team, Alibaba Cloud, unchanged | Apache-2.0 |

`language-models.tsv` lists every file with its size and sha256; the app checks both before
it uses a file. The files are copied unchanged from the sources pinned in
`language-models/sources.tsv` of the repository, by the "Language models" workflow.
