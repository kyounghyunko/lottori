# Lottori サンプル

[English](README.md) · [한국어](README.ko.md) · **日本語**

サンプルアプリを開くと、`paper-flight.json` から描画した **Paper flight** が 5.2 秒周期で繰り返し再生されます。kyounghyunko が ChatGPT を使ってこのアニメーションを制作しました。 [折り鶴の JSON](../docs/assets/paper-flight.json) · [Android エミュレータ (MP4)](../docs/assets/paper-flight-android.mp4) · [iOS シミュレータ (MP4)](../docs/assets/paper-flight-ios.mp4)

ビルド時にサンプルの JSON が `commonMain` の Compose Resources に組み込まれます。Android と iOS の共通画面は `files/paper-flight.json` から折り鶴を読み込みます。**Moving circle** を選んで **50%** をタップすると、プレビューの中央に赤い円が表示されます。**Play** と **Pause** で再生と一時停止を切り替えられます。**Trim path** を選ぶと、代替アイコンと未対応ノードの JSON パスが表示されます。**Damaged JSON** と **ZIP** では、それぞれ異なる入力エラーを確認できます。[サンプルファイル](../fixtures)

## Android で実行する

JDK 21 と Android SDK 37 をインストールし、Android エミュレータを起動してください。シリアル番号を起動中のエミュレータに合わせ、リポジトリのルートで次のコマンドを実行します。

```sh
export ANDROID_SERIAL=emulator-5554
./gradlew :sample:androidApp:assembleDebug
"$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" install -r sample/androidApp/build/outputs/apk/debug/androidApp-debug.apk
"$ANDROID_HOME/platform-tools/adb" -s "$ANDROID_SERIAL" shell am start -n com.ycgc.lottori.sample/.MainActivity
python3 sample/scripts/android-smoke.py "$ANDROID_SERIAL" sample/build/smoke/android
```

最後のコマンドはサンプル画面のボタンをタップし、円の中心のピクセル、ファイルごとの診断、代替アイコンの画面を検査してスクリーンショットを保存します。

## iOS で実行する

Apple Silicon 搭載 Mac に JDK 21 と、iOS シミュレータランタイムを含む Xcode をインストールしてください。Xcode プロジェクトを開き、**LottoriSample** スキームと iPhone シミュレータを選択して **Run** を実行します。ビルド時に Kotlin フレームワークとサンプルファイルが用意されます。[Xcode プロジェクト](iosApp/LottoriSample.xcodeproj)

XCTest で UI テストを実行するには、`xcrun simctl list devices available` に表示されるシミュレータ ID を `IOS_UDID` に指定します。リポジトリのルートで次のコマンドを実行してください。

```sh
xcodebuild -project sample/iosApp/LottoriSample.xcodeproj -scheme LottoriSample \
  -destination "platform=iOS Simulator,id=$IOS_UDID" -parallel-testing-enabled NO \
  -derivedDataPath sample/build/xcode -resultBundlePath sample/build/smoke.xcresult test
```

XCTest の結果バンドルには、進行度 50% のフレームと代替アイコンのスクリーンショットが含まれます。再実行するときは結果バンドルに新しいパスを指定してください。XcodeGen の設定を変更する場合は、`sample/iosApp/project.yml` を編集し、そのディレクトリで `xcodegen generate` を実行します。

| 画面 | Android エミュレータ | iOS シミュレータ |
| --- | --- | --- |
| 進行度を 50% に固定 | [移動する円](../docs/screenshots/android/moving-050.png) | [移動する円](../docs/screenshots/ios/moving-050.png) |
| 未対応のトリムパス | [代替アイコン](../docs/screenshots/android/trim-fallback.png) | [代替アイコン](../docs/screenshots/ios/trim-fallback.png) |
