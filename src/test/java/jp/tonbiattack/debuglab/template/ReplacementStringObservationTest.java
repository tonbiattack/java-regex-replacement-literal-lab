package jp.tonbiattack.debuglab.template;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Matcher;

import org.junit.jupiter.api.Test;

class ReplacementStringObservationTest {

    @Test
    void quoteReplacementMakesDollarAmountLiteralInReplaceAll() {
        String template = "Price: {{amount}}";
        String amount = "$5.00";

        IndexOutOfBoundsException failure = assertThrows(
                IndexOutOfBoundsException.class,
                () -> template.replaceAll("\\{\\{amount\\}\\}", amount)
        );

        String rendered = template.replaceAll(
                "\\{\\{amount\\}\\}",
                Matcher.quoteReplacement(amount)
        );

        assertAll(
                () -> assertEquals("No group 5", failure.getMessage(),
                        "生の$5は存在しないグループ5として解釈される"),
                () -> assertEquals("Price: $5.00", rendered,
                        "quoteReplacement後の$は文字どおり置換される")
        );
    }
}
