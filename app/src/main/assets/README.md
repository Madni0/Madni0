# Model drop-in folder

The app ships with a calibrated **heuristic baseline detector** so it works
out of the box. For production-grade accuracy, drop trained TFLite models
here (no code changes needed - they are auto-detected at startup):

| File | Purpose | Input contract | Output contract |
|---|---|---|---|
| `ai_image_detector.tflite` | AI-image probability | `float32 [1, 224, 224, 3]` RGB, 0..1 | `float32 [1, 1]` sigmoid: P(AI) |
| `deepfake_detector.tflite` | Deepfake probability (face crop) | `float32 [1, 224, 224, 3]` RGB, 0..1 | `float32 [1, 1]` sigmoid: P(fake) |

Any open universal AI-image detector (UNFD / UniversalFakeDetect style
backbone, EfficientNet-b4, etc.) can be exported to this contract with a
few lines of TensorFlow Lite conversion code (see docs/ARCHITECTURE.md).

While a model is present it takes priority; the heuristic detector remains
as fallback and for explainability.
