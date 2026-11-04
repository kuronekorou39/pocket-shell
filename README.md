<p align="center">
  <img src="docs/logo.svg" width="112" alt="Pocket Shell のアイコン">
</p>

<h1 align="center">Pocket Shell</h1>

<p align="center">パソコンなしで、スマホから自分自身に adb のコマンドを送るアプリ</p>

---

権限の付与など、Android には adb のコマンドでしか変えられない設定があります。Pocket Shell は「ワイヤレスデバッグ」を使って、スマホの中から自分自身にコマンドを送ります。パソコンもケーブルも要りません。

> [!WARNING]
> コマンドは adb と同じ強い権限で動きます。意味の分からないコマンドは実行しないでください。

<table>
  <tr>
    <td align="center"><img src="docs/images/run.png" width="200" alt="実行のタブ"><br>実行</td>
    <td align="center"><img src="docs/images/log.png" width="200" alt="記録のタブ"><br>記録</td>
    <td align="center"><img src="docs/images/edit.png" width="200" alt="レシピの編集"><br>レシピの編集</td>
    <td align="center"><img src="docs/images/color.png" width="200" alt="色の選択"><br>色</td>
  </tr>
</table>

## できること

- よく使うコマンドを「レシピ」にして、ボタン1つで実行
- USB デバッグ・ワイヤレスデバッグを、画面のスイッチでオンオフ
- 結果は「記録」のタブで見て、コピーできる
- 関連アプリが入っていれば、専用のボタンが出る（例: [Bento Home](https://github.com/kuronekorou39/bento-home) の計測用プロセスの起動）

## 入れ方

1. [Releases](https://github.com/kuronekorou39/pocket-shell/releases) から、最新の `pocket-shell-….apk` をスマホでダウンロードして開きます。
2. 開発者向けオプションを出し、ワイヤレスデバッグでペアリングします。手順は **[使い方](docs/setup.md)** にあります。

Google Play を通していないので、Play プロテクトやウイルス対策アプリが警告を出すことがあります。

動作環境は Android 14 以降で、Wi-Fi への接続が必要です。

## 資料

- [使い方](docs/setup.md): 初めの設定、スイッチ、つながらないとき、プライバシー
- [運用手順](docs/operations.md): ビルド・署名・公開（開発者向け）

## ライセンス

[MIT](LICENSE)
