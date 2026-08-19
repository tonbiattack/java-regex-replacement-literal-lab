# E005: `replaceAll`の置換値にある`$`がグループ参照として解釈される

## 目的

テンプレート`"Price: {{amount}}"`へ金額`"$5.00"`を埋め込む場合、`$`は金額の一部として文字どおり出力される必要があります。通常金額`"EUR 5.00"`を成功レンダリングした後にドル金額をレンダリングしたとき、`"Price: $5.00"`を最後の成功本文として保存し、成功件数を`2`にすることが契約です。

## 実行環境と再現境界

このラボはJava 21、Maven、JUnit Jupiter 5.11.4だけを使います。フレームワーク、HTTP、ファイル、データベース、外部テンプレートエンジンは使いません。公開境界は`AmountTemplateService#render(String, String)`であり、直接結果として`RenderOutcome`を、最終状態として`lastRendered()`と`successfulRenderCount()`を別々に読みます。

テストは最初に`EUR 5.00`を成功させ、後続の`$5.00`を実行します。このため、二回目のレンダリングが失敗したときに、単に結果コードが誤っているのではなく、最後の成功本文と成功件数が更新されていないことを確認できます。入力と状態は固定文字列だけなので決定的です。

## 最初に観測した事実

バグ状態はコミット[`d5bffc6`](../commit/d5bffc6)です。次のコマンドで、意図したアサーション差分を確認しました。

```bash
git checkout d5bffc6
mvn --batch-mode test -Dtest=AmountTemplateServiceTest
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 直接結果 | `RENDERED` | `REJECTED_REPLACEMENT` | `AmountTemplateServiceTest` |
| 最後の成功本文 | `Price: $5.00` | `Price: EUR 5.00` | `AmountTemplateService#lastRendered()` |
| 成功件数 | `2` | `1` | `AmountTemplateService#successfulRenderCount()` |
| 生の置換値 | `$5.00`を文字どおり置換 | `IndexOutOfBoundsException: No group 5` | `ReplacementStringObservationTest` |
| literal化した置換値 | `Price: $5.00` | `Price: $5.00` | `ReplacementStringObservationTest` |

```text
ドル記号を含む金額でもテンプレートを成功としてレンダリングする
==> expected: <RENDERED> but was: <REJECTED_REPLACEMENT>

最後に成功した本文はドル記号を文字どおり含む
==> expected: <Price: $5.00> but was: <Price: EUR 5.00>

通常金額とドル金額の二回を成功として数える
==> expected: <2> but was: <1>
```

完全な失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。直接の結果だけでなく、本文と成功件数を最終状態として分けて確認したため、例外だけを捕捉して状態を更新していた可能性は除外できます。

## 競合仮説と検証

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| プレースホルダーを表す正規表現が`{{amount}}`に一致していない | 同じテンプレートを`EUR 5.00`でレンダリングする対照テストを実行する | 成功するため棄却。 |
| 金額文字列の検証がドル記号を拒否している | サービスの実装に金額検証がないこと、および例外のスタックトレースが`String.replaceAll`にあることを確認する | 棄却。 |
| `replaceAll`の置換文字列で`$`がグループ参照として解釈される | 生の`$5.00`と`Matcher.quoteReplacement("$5.00")`を同じテンプレートへ渡して比較する | 生の値は`No group 5`、literal化した値は期待本文を返す。採用。 |

## 確定した原因

バグ状態のサービスは、外部入力の金額をそのまま第二引数へ渡していました。

```java
lastRendered = template.replaceAll("\\{\\{amount\\}\\}", amount);
```

`replaceAll`のreplacementは単なる文字列値ではありません。`$`はグループ参照に、`\`はエスケープに使われる置換構文です。[1] `"$5.00"`はグループ5への参照として解釈されますが、対象パターンにはグループがないため、JDK 21では`IndexOutOfBoundsException: No group 5`が発生します。

サービスはこの実行時例外を`REJECTED_REPLACEMENT`へ変換するため、本文の代入と成功件数の増加まで到達しません。例外変換は症状の表現であり、直接原因は外部入力をliteral replacementへ変換していないことです。

## 最小修正

修正コミットは[`d4820be`](../commit/d4820be)です。置換値へ`Matcher.quoteReplacement`を適用しました。

```java
lastRendered = template.replaceAll(
        "\\{\\{amount\\}\\}",
        Matcher.quoteReplacement(amount));
```

`Matcher.quoteReplacement`は、ドル記号とバックスラッシュが特別な意味を持たないliteral replacementを返します。[1] これにより、`$5.00`はグループ参照ではなく金額文字列として差し込まれます。

`replaceAll`を単純な`replace`へ置き換える修正もこの固定プレースホルダーには有効ですが、今回の題材は正規表現置換を使う既存の境界での置換値の解釈です。最小修正として、検索正規表現やAPIを変えず、置換値だけをliteral化しました。例外を成功として握りつぶす、テストの期待値を旧本文へ下げる修正は採用していません。

## 回帰保証

### 再発防止テスト

最初に失敗した`dollarAmount_isRenderedLiterallyAndRecordedAsTheLatestSuccessfulBody`はそのまま残しています。このテストは、直接の`RENDERED`、最新本文、成功件数を別々に検証します。

| テスト | 回帰として守る契約 |
| --- | --- |
| `dollarAmount_isRenderedLiterallyAndRecordedAsTheLatestSuccessfulBody` | ドル記号を含む金額を文字どおりに置換し、本文・成功件数を更新する。 |
| `amountWithoutReplacementMetaCharacters_remainsRendered` | `$`を含まない通常金額を従来どおり置換できる。 |
| `quoteReplacementMakesDollarAmountLiteralInReplaceAll` | 生の置換値のグループ参照エラーと、literal化後の成功の差を直接示す。 |

修正後の`mvn --batch-mode clean test`では、3テストがすべて成功しました。完全な出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。

## 再現手順

```bash
git checkout d5bffc6
mvn --batch-mode test -Dtest=AmountTemplateServiceTest
# expected: <RENDERED> but was: <REJECTED_REPLACEMENT>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

## スコープと注意点

この修正は、外部値を正規表現のreplacementへ渡す場合に有効です。外部入力から検索regexを作る問題は別であり、必要に応じて`Pattern.quote`を使うなど、検索側と置換側を区別して扱う必要があります。

また、文字どおりの置換はHTML、SQL、JavaScript、シェルなどの出力文脈に対する安全なエスケープを意味しません。出力先の構文に応じたエンコード・エスケープは別の責務です。本ラボはJava正規表現の置換構文だけを扱います。

## References

[1] [Oracle: `Matcher` — `quoteReplacement`, `replaceAll`, and replacement strings](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Matcher.html)
