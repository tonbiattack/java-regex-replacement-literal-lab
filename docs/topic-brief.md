# 題材企画: `replaceAll`に金額の`$`を渡し、テンプレートレンダリングが失敗する

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | `String.replaceAll`や`Matcher`を使って、外部入力をテンプレートの置換値に渡す中級者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | `replaceAll`の第一引数は正規表現であるだけでなく、第二引数もグループ参照とエスケープを解釈する置換文字列である。直接のレンダリング結果、最後に成功した本文、成功件数を分けて観測し、プレースホルダー正規表現・入力値・置換文字列文法の仮説を比較できる。 |
| 実行基盤 | Maven、Java 21、JUnit Jupiter 5.11.4 |
| フレームワーク非依存性 | 原因は`java.lang.String.replaceAll`と`java.util.regex.Matcher.quoteReplacement`の標準ライブラリ契約である。HTTP、DI、DB、テンプレートエンジン、外部APIには依存しない。 |

## 学習する契約

> 既に`"Price: EUR 5.00"`を成功レンダリングした状態で、テンプレート`"Price: {{amount}}"`へ金額`"$5.00"`を渡した場合、`"Price: $5.00"`を成功として保存し、成功件数を二件にすべきだが、バグ状態では`REJECTED_REPLACEMENT`となり、旧本文と旧成功件数が残る。

### 対象の直接原因

`String.replaceAll(regex, replacement)`へ外部入力をそのまま`replacement`として渡している。`$`は置換文字列内でグループ参照に使われるため、金額の先頭にある`$5`が存在しないグループを指すものとして解釈され、`IllegalArgumentException`になる。

### 対象外

このラボは複数プレースホルダーの展開、ユーザー提供の正規表現、HTMLエスケープ、国際化、通貨の表示規則、テンプレートエンジンの選定、SQLやシェルのエスケープを扱わない。固定の`{{amount}}`プレースホルダーを、文字どおりの置換値で一回だけ差し替える狭い規則だけを扱う。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `AmountTemplateService#render(String, String)`、`lastRendered()`、`successfulRenderCount()`。 |
| 入力・初期状態 | `"Price: {{amount}}"`へ`"EUR 5.00"`を一度成功レンダリング後、同じテンプレートへ`"$5.00"`を渡す。 |
| Redの観測 | `RenderOutcome.RENDERED`を期待するが、バグ状態では`RenderOutcome.REJECTED_REPLACEMENT`となる。 |
| 最終観測 | `lastRendered()`が`"Price: $5.00"`となり、`successfulRenderCount()`が`2`であることを別々に検証する。 |
| 決定性 | 時刻、乱数、並行実行、`sleep`、外部I/Oを使わず、固定文字列とインメモリ状態だけを使う。 |
| 固定状態の検証コマンド | `mvn --batch-mode clean test` |
| バグ状態の確認コマンド | `git checkout <bug-commit>`後に`mvn --batch-mode test -Dtest=AmountTemplateServiceTest` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| A: プレースホルダーを表す正規表現が`{{amount}}`に一致していない | `EUR 5.00`のように`$`を含まない値を使い、同じテンプレートが成功することを確認する。 |
| B: 金額文字列の検証がドル記号を拒否している | サービスに金額専用の検証がなく、例外が`replaceAll`から発生することを直接観測する。 |
| C: `replaceAll`の置換文字列で`$`がグループ参照として解釈される | 生の`$5.00`で例外を観測し、`Matcher.quoteReplacement("$5.00")`で同じ文字列が成功することを比較する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | ドル記号を含む金額のテンプレート置換失敗を再現する | 対象テストが`RENDERED`期待・`REJECTED_REPLACEMENT`実際のアサーション差分で失敗する。 |
| 2 | テンプレート置換値を文字どおり扱う | 同じ検証が成功し、全体も成功する。 |
