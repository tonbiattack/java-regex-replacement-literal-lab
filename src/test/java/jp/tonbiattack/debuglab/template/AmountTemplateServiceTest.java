package jp.tonbiattack.debuglab.template;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AmountTemplateServiceTest {

    @Test
    void dollarAmount_isRenderedLiterallyAndRecordedAsTheLatestSuccessfulBody() {
        AmountTemplateService service = new AmountTemplateService();
        String template = "Price: {{amount}}";
        service.render(template, "EUR 5.00");

        RenderOutcome outcome = service.render(template, "$5.00");

        assertAll(
                () -> assertEquals(RenderOutcome.RENDERED, outcome,
                        "ドル記号を含む金額でもテンプレートを成功としてレンダリングする"),
                () -> assertEquals("Price: $5.00", service.lastRendered(),
                        "最後に成功した本文はドル記号を文字どおり含む"),
                () -> assertEquals(2, service.successfulRenderCount(),
                        "通常金額とドル金額の二回を成功として数える")
        );
    }

    @Test
    void amountWithoutReplacementMetaCharacters_remainsRendered() {
        AmountTemplateService service = new AmountTemplateService();

        RenderOutcome outcome = service.render("Price: {{amount}}", "EUR 5.00");

        assertAll(
                () -> assertEquals(RenderOutcome.RENDERED, outcome),
                () -> assertEquals("Price: EUR 5.00", service.lastRendered()),
                () -> assertEquals(1, service.successfulRenderCount())
        );
    }
}
