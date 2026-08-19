# `replaceAll`に金額の`$`を渡し、テンプレートレンダリングが失敗する

Java標準ライブラリの`String.replaceAll`と`Matcher.quoteReplacement`を題材に、**ドル記号を含む金額を置換値として渡すとテンプレートレンダリングが失敗する**問題を、失敗するテスト、原因の直接観測、最小修正、回帰テストの順に追うデバッグ教材です。既定ブランチの`main`は成功状態に保ち、意図的に失敗する状態はGit履歴に独立して残します。

## この題材で守る契約

> 既に`"Price: EUR 5.00"`を成功レンダリングした状態で、`"Price: {{amount}}"`へ`"$5.00"`を渡す場合、`"Price: $5.00"`を新しい成功本文として保存し、成功件数を`2`にする。

| 段階 | 実施内容 | 確認すること |
| --- | --- | --- |
| 再現 | ドル記号を含む金額を`replaceAll`の置換値へ渡す | `RENDERED`ではなく`REJECTED_REPLACEMENT`となり、旧本文・旧成功件数が残る |
| 観測 | 生の`$5.00`と`Matcher.quoteReplacement("$5.00")`を比較する | 生の値は存在しないグループ5として解釈され、literal化した値は`$5.00`のまま置換される |
| 修正 | `Matcher.quoteReplacement(amount)`を置換値へ使う | `$`と`\`を置換構文ではなく文字列値として扱える |
| 回帰防止 | 同じサービステストを再実行する | ドル金額と通常金額がどちらも成功し、最新本文・成功件数が更新される |

## 必要な環境

| 項目 | バージョン |
| --- | --- |
| JDK | 21 |
| Maven | 3.8以上 |
| テストランナー | JUnit Jupiter 5.11.4 |
| アプリケーションフレームワーク | 不使用 |

## 最短の開始手順

```bash
mvn --batch-mode clean test
```

検証済みの`main`では、3テストがすべて成功します。

## バグを再現する

```bash
git checkout d5bffc6
mvn --batch-mode test -Dtest=AmountTemplateServiceTest
# expected: <RENDERED> but was: <REJECTED_REPLACEMENT>
# expected: <Price: $5.00> but was: <Price: EUR 5.00>
# expected: <2> but was: <1>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

バグコミットでは設定やコンパイルではなく、ドル記号を文字どおりに置換する契約だけが失敗します。完全な出力は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。

## 原因の要点

`String.replaceAll(regex, replacement)`は、第一引数だけでなく第二引数も置換構文として扱います。ドル記号はグループ参照に使われるため、置換値`"$5.00"`は「グループ5を参照する」表記と解釈されます。今回のパターンにはグループ5がないため、JDK 21では`IndexOutOfBoundsException: No group 5`となります。[1]

`Matcher.quoteReplacement`は、ドル記号とバックスラッシュが特別な意味を持たないliteral replacementを返します。[1] 外部入力を置換値として渡すとき、入力を正規表現にする必要がなければ、このメソッドでliteral化します。

## プロジェクト構成

```text
.
├── docs/
│   ├── debugging-record.md      # 観測・仮説・原因・修正・回帰保証
│   ├── novelty-report.md        # 既存Java記事との四軸比較
│   └── topic-brief.md           # 実装前に固定した契約と再現境界
├── evidence/
│   ├── 01-bug-service-test-output.txt
│   ├── 02-replacement-observation-output.txt
│   └── 03-fixed-full-test-output.txt
├── src/main/java/.../template/
│   ├── AmountTemplateService.java
│   └── RenderOutcome.java
└── src/test/java/.../template/
    ├── AmountTemplateServiceTest.java
    └── ReplacementStringObservationTest.java
```

詳細な調査手順は[デバッグ記録](docs/debugging-record.md)、既存コンテンツとの差分は[題材重複調査レポート](docs/novelty-report.md)を参照してください。

## スコープ

この教材は固定の`{{amount}}`を一回だけ置換する、単純な文字列テンプレートを対象にします。複数プレースホルダー、ユーザー提供の正規表現、HTMLエスケープ、国際化、通貨書式、テンプレートエンジンの選定は対象外です。置換値ではなく検索パターンを外部入力から作る場合には、別途`Pattern.quote`などを検討してください。

## References

[1] [Oracle: `Matcher` — `quoteReplacement`, `replaceAll`, and replacement strings](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Matcher.html)
