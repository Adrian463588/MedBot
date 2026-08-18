# Evidence-gated journeys

These journeys are specifications, not executed reports. They use no fabricated coordinates, model output, document text, or photo result.

Run only after a real debug APK is installed on an identified emulator or physical device. If any real prerequisite is absent, record `BLOCKED` and stop. A passing unit test, Compose shell, screenshot, or model registry entry is not physical acceptance.

Windows starting checks:

```powershell
adb devices -l
adb shell getprop ro.build.version.sdk
```

Required explicit evidence for a future physical run:

- readable model file with `.litertlm` or `.gguf` extension;
- user-selected readable medical document URI;
- user-selected readable photo URI;
- fresh device/API/OEM details and a fresh screenshot captured during that run.

No result file is committed here. Use `medbot_evidence_gate.xml` as the source specification and attach only observed output from the actual run.
