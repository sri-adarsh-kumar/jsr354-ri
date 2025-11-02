/**
 * Copyright (c) 2012, 2019, Werner Keil and others by the @author tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.javamoney.moneta.format;

import static org.testng.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.Locale;

import javax.money.format.AmountFormatQueryBuilder;
import javax.money.format.MonetaryAmountFormat;
import javax.money.format.MonetaryFormats;

import org.javamoney.moneta.Money;
import org.testng.annotations.Test;

public class MonetaryFormatsParseBySymbolTest {
    public static final Locale INDIA = new Locale("en", "IN");

    /**
     * Test related to parsing currency symbols.
     * Verifies locale-aware symbol parsing (issue #423 fix).
     * INR symbol ₹ should correctly parse as INR, not EUR.
     *
     * @see <a href="https://github.com/JavaMoney/jsr354-ri/issues/274">Issue #274</a>
     * @see <a href="https://github.com/JavaMoney/jsr354-ri/issues/423">Issue #423</a>
     */
    @Test
    public void testParseCurrencySymbolINR1() {
        MonetaryAmountFormat format = MonetaryFormats.getAmountFormat(
                    AmountFormatQueryBuilder.of(Locale.GERMANY)
                        .set(CurrencyStyle.SYMBOL)
                        .build());
        Money money = Money.of(new BigDecimal("1234567.89"), "EUR");
        String expectedFormattedString = "1.234.567,89 €";
        assertEquals(expectedFormattedString, format.format(money));
        assertEquals(money, Money.parse(expectedFormattedString, format));

        // INR symbol ₹ is now correctly parsed as INR (issue #423 fix)
        money = Money.of(new BigDecimal("1234567.89"), "INR");
        expectedFormattedString = "1.234.567,89 ₹";
        assertEquals(expectedFormattedString, format.format(money));
        assertEquals(money, Money.parse(expectedFormattedString, format));
    }

    /**
     * Test related to parsing currency symbols.
     * Verifies locale-aware symbol parsing (issue #423 fix).
     * INR symbol ₹ should correctly parse as INR, not EUR.
     *
     * @see <a href="https://github.com/JavaMoney/jsr354-ri/issues/274">Issue #274</a>
     * @see <a href="https://github.com/JavaMoney/jsr354-ri/issues/423">Issue #423</a>
     */
    @Test
    public void testParseCurrencySymbolINR2() {
        MonetaryAmountFormat format = MonetaryFormats.getAmountFormat(
                    AmountFormatQueryBuilder.of(INDIA)
                        .set(CurrencyStyle.SYMBOL)
                        .build());
        // India locale uses Indian numbering system with lakhs/crores grouping
        Money money = Money.of(new BigDecimal("1234567.89"), "EUR");
        String formattedString = format.format(money);
        assertEquals(money, Money.parse(formattedString, format));

        // INR symbol ₹ is now correctly parsed as INR (issue #423 fix)
        money = Money.of(new BigDecimal("1234567.89"), "INR");
        formattedString = format.format(money);
        assertEquals(money, Money.parse(formattedString, format));
    }
}
