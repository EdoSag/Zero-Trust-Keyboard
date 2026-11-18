# Voice typing models

Speech models for voice typing in ZT Keyboard. The app's full edition can download them
in Settings > Voice typing; in both editions a model can also be added from a file.

The files are copies of models published by others, fetched and checked against a pinned
sha256 by the "Voice models" workflow of this repository:

| Model | Source | License |
| --- | --- | --- |
| `ggml-ivrit-large-v3-turbo*.he.bin` | [ivrit-ai/whisper-large-v3-turbo-ggml](https://huggingface.co/ivrit-ai/whisper-large-v3-turbo-ggml), Whisper large-v3-turbo tuned for Hebrew by ivrit.ai. The `q5_0` file is the same model quantized with whisper.cpp to about a third of the size. | Apache-2.0 |
| the other `ggml-*.bin` files | [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp), OpenAI Whisper models in ggml format | MIT |

`voice-models.tsv` lists the files with their sizes and sha256; the app checks every
download against it. `sources.tsv` in this folder decides what is mirrored.
