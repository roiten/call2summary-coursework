# GigaAM офлайн — минимальный Android-прототип (gigastt)

Выбор аудиофайла → «Распознать» → текст на экране + «Копировать».
Распознавание идёт **на телефоне** через [gigastt](https://github.com/ekhodzitsky/gigastt)
(GigaAM v3 RNN-T INT8, ONNX Runtime, CPU). Без серверов и облачных API.
Интернет нужен один раз — скачать веса (~305 МБ). После этого работает офлайн.

## Как это устроено
- `gigastt-ffi` JNI-функций не содержит (`ffi/android/GigasttBridge.kt` в репозитории — скелет), поэтому
  используется крейт **`gigastt-uniffi`**: он генерирует Kotlin-класс `Engine` (`transcribeFile(path)`).
- `build_native.sh` собирает `libgigastt_uniffi.so` + `libonnxruntime.so` для **arm64-v8a** и генерирует Kotlin-биндинги
  по рецепту CI самого gigastt (зафиксирован коммит `47f5cf5`, v2.22.0).
- Веса: GitHub Release `models-v3-2026-10-09`, 4 файла, SHA-256 проверяется при загрузке,
  хранятся в `filesDir/gigastt_models`.

## 0. Проверьте телефон
Международные Pura 80 (Pro/Ultra) работают на **EMUI 15 (Android)** — APK ставится.
Китайские — на **HarmonyOS 5.1**, обычные Android-APK там не работают.

## 1. Сборка (Linux / macOS / WSL)
Нужно: JDK 17+, Android SDK (platform 34) и NDK r26+, Rust stable ≥ 1.94 (rustup), `protoc`,
`cargo install --locked --version 4.1.2 cargo-ndk`, python3 ≥ 3.11, git, curl, unzip, Gradle 8.7+.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<версия>
./build_native.sh            # нативные .so + Kotlin-биндинги (долго: компиляция Rust)
gradle :app:assembleDebug    # -> app/build/outputs/apk/debug/app-debug.apk
```
(Gradle-wrapper не включён: либо `gradle wrapper --gradle-version 8.7`, либо открыть проект в Android Studio.)

**Без локальной установки Rust/NDK:** залить проект в свой GitHub-репозиторий и запустить workflow
`Build debug APK` (Actions → Run workflow), скачать артефакт `app-debug.apk`.

## 2. Установка
На телефоне разрешите установку из неизвестных источников, затем:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
(или скопируйте APK на телефон и откройте).

## 3. Использование
1. Запустите приложение → **«Загрузить модель»** (один раз, по Wi-Fi). Дождитесь «Модель готова».
2. **«Выбрать аудиофайл»** → файл (WAV, MP3, M4A/AAC, FLAC, OGG и др.; русская речь).
3. **«Распознать»** → текст появится ниже; **«Копировать текст»**.
4. Для проверки офлайн-режима включите авиарежим и распознайте ещё раз.

## Проверено / не проверено
Проверено (в песочнице, где собирать нельзя):
- прочитаны исходники и документация gigastt; JNI-пути нет, UniFFI-путь есть;
- имена файлов весов, URL релиза, размеры и SHA-256 всех 4 файлов совпадают с репозиторием (скачаны и сверены);
- API `Engine`/`transcribeFile`/`Transcript`/ошибки сверены с `gigastt-uniffi/src/lib.rs`;
- декодер использует расширение файла как подсказку (поэтому оно сохраняется при копировании из `content://`);
- XML, YAML и shell-синтаксис корректны.

НЕ проверено:
- **APK не собран**: в песочнице нет Rust/NDK/Android SDK/Gradle, Maven и Google-репозитории недоступны;
- Kotlin-код **не компилировался**; пакет сгенерированного кода предполагается `uniffi.gigastt_uniffi`
  (скрипт это проверит и сообщит, если иначе);
- сборка `build_native.sh` и workflow не запускались; в gigastt arm64-сборка и Gradle-сборка AAR
  проходили в их CI, но **запуск на устройстве авторы тоже не тестировали**;
- работа на Pura 80 (EMUI 15), скорость, расход памяти, качество на ваших записях, очень длинные файлы.
