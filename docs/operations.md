# Pocket Shell — 運用手順

## ビルドと署名

| 種類 | パッケージ | 署名 | 用途 |
|---|---|---|---|
| デバッグ版 | `com.rou39.pocketshell.debug` | Android SDK のデバッグ鍵 | 開発・実機確認 |
| リリース版 | `com.rou39.pocketshell` | リリース鍵（下記） | 配布（GitHub の Releases） |

```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

### リリース鍵

- 鍵: `C:\Users\kuronekorou39\.pocket-shell\release.jks`（PKCS12、別名 `pocket-shell`、RSA 4096、有効期限100年）
- パスワード: プロジェクト直下の `keystore.properties` と、鍵と同じフォルダの `keystore.properties` に記載
- `keystore.properties` と `*.jks` は `.gitignore` 済みで、リポジトリには入らない
- 証明書 SHA-256: `d445f1a381136a42b3f784724032e705e6e4f8a8f13bae1c7f732b549be4ce87`

**鍵とパスワードは別の場所にも控えること。** 失うと、配布したリリース版を同じパッケージのまま更新できなくなる。

`keystore.properties` が無い環境では、リリース版は未署名でビルドされる。

## 公開

1. `app/build.gradle.kts` の `versionCode` を上げ、`versionName` を決める。
2. `./gradlew assembleRelease` でビルドし、`apksigner verify --print-certs` で上の証明書と一致することを確かめる。
3. `pocket-shell-<versionName>.apk` に名前を変えて、GitHub の Releases に `v<versionName>` で上げる。
