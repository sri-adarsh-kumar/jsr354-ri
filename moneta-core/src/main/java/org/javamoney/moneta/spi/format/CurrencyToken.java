/*
 * Copyright (c) 2012, 2020, Anatole Tresch, Werner Keil and others by the @author tag.
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
package org.javamoney.moneta.spi.format;

import org.javamoney.moneta.format.CurrencyStyle;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.Monetary;
import javax.money.format.AmountFormatContext;
import javax.money.format.MonetaryParseException;
import java.io.IOException;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINEST;
import static org.javamoney.moneta.format.CurrencyStyle.CODE;

/**
 * Implements a {@link FormatToken} that adds a localizable {@link String}, read
 * by key from a {@link ResourceBundle}.
 * <p>
 * Symbol parsing uses locale-aware resolution with the following precedence:
 * <ol>
 * <li>If an explicit currency is in the context, validate that the symbol matches.</li>
 * <li>Check if the symbol matches the locale's default currency.</li>
 * <li>Scan all available JDK currencies for matching symbols.</li>
 * <li>If exactly one match is found, use it; if multiple, raise ambiguity error; if none, raise unknown symbol error.</li>
 * </ol>
 *
 * @author Anatole Tresch
 */
final class CurrencyToken implements FormatToken {
    /**
     * The current conversion context.
     */
    private final AmountFormatContext context;
    /**
     * The style defining, how the currency should be localized.
     */
    private CurrencyStyle style = CODE;
    /**
     * The target locale.
     */
    private final Locale locale;

    /**
     * Creates a new {@link CurrencyToken}.
     *
     * @param style  The style defining, how the currency should be localized, not
     *               {@code null}.
     * @param context The target context, not {@code null}.
     */
    CurrencyToken(CurrencyStyle style, AmountFormatContext context) {
        this.context = requireNonNull(context);
        this.locale = requireNonNull(context.getLocale(), "Locale null");
        if (Objects.nonNull(style)) {
            this.style = style;
        }
    }

    /**
     * Explicitly configure the {@link CurrencyStyle} to be used.
     *
     * @param style the {@link CurrencyStyle}, not {@code null}.
     * @return this token instance, for chaining.
     */
    public CurrencyToken setCurrencyStyle(CurrencyStyle style) {
        this.style = requireNonNull(style, "CurrencyStyle null");
        return this;
    }

    /**
     * Access the {@link CurrencyStyle} used for formatting.
     *
     * @return the current {@link CurrencyStyle}, never {@code null}.
     */
    public CurrencyStyle getCurrencyStyle() {
        return this.style;
    }

    /**
     * Evaluate the formatted(localized) token.
     *
     * @param amount the {@link MonetaryAmount} containing the {@link CurrencyUnit}
     *               to be formatted.
     * @return the formatted currency.
     */
    private String getToken(MonetaryAmount amount) {
        switch (style) {
            case NUMERIC_CODE:
                return String.valueOf(amount.getCurrency().getNumericCode());
            case NAME:
                return getCurrencyName(amount.getCurrency());
            case SYMBOL:
                return getCurrencySymbol(amount.getCurrency());
            case CODE:
                return amount.getCurrency().getCurrencyCode();
            default:
                throw new UnsupportedOperationException("Unexpected style " + style);
        }
    }

    /**
     * This method tries to evaluate the localized display name for a
     * {@link CurrencyUnit}. It uses {@link Currency#getDisplayName(Locale)} if
     * the given currency code maps to a JDK {@link Currency} instance.
     * <p>
     * If not found {@code currency.getCurrencyCode()} is returned.
     *
     * @param currency The currency, not {@code null}
     * @return the formatted currency name.
     */
    private String getCurrencyName(CurrencyUnit currency) {
        Currency jdkCurrency = getCurrency(currency.getCurrencyCode());
        if (Objects.nonNull(jdkCurrency)) {
            return jdkCurrency.getDisplayName(locale);
        }
        return currency.getCurrencyCode();
    }

    /**
     * Method to safely access a {@link java.util.Currency}.
     * If no such currency exists, null is returned.
     *
     * @param currencyCode the currency code , not null.
     * @return the corresponding currency instance, or null.
     */
    private Currency getCurrency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * This method tries to evaluate the localized symbol name for a
     * {@link CurrencyUnit}. It uses {@link Currency#getSymbol(Locale)} if the
     * given currency code maps to a JDK {@link Currency} instance.
     * <p>
     * If not found {@code currency.getCurrencyCode()} is returned.
     *
     * @param currency The currency, not {@code null}
     * @return the formatted currency symbol.
     */
    private String getCurrencySymbol(CurrencyUnit currency) {
        Currency jdkCurrency = getCurrency(currency.getCurrencyCode());
        if (Objects.nonNull(jdkCurrency)) {
            return jdkCurrency.getSymbol(locale);
        }
        return currency.getCurrencyCode();
    }

    /**
     * Parses a currency from the given {@link ParseContext}. Depending on the
     * current {@link CurrencyStyle} it interprets the next non empty token,
     * either as
     * <ul>
     * <li>currency code
     * <li>currency symbol
     * </ul>
     * Parsing of localized currency names or numeric code is not supported.
     *
     * @throws MonetaryParseException on an error or if the {@link CurrencyStyle} is configured
     *         to non implemented currency name (NAME), or numeric codes (NUMERIC_CODE).
     */
    @Override
    public void parse(ParseContext context)
            throws MonetaryParseException {
        String token = context.lookupNextToken();
        while (Objects.nonNull(token)) {
            if (token.trim().isEmpty()) {
                context.consume(token);
                token = context.lookupNextToken();
                continue;
            }
            break;
        }
        if (token == null){
            throw new MonetaryParseException("Error parsing CurrencyUnit: no input.", "", -1);
        }
        try {
            CurrencyUnit cur;
            String[] providers = this.context.get("currencyProviderName", String.class)!=null?
                    new String[]{this.context.get("currencyProviderName", String.class)}:new String[0];
            switch (style) {
                case CODE:
                    if (!Monetary.isCurrencyAvailable(token, providers)) {
                        // Perhaps blank is missing between currency code and number...
                        String subCurrency = parseCurrencyCode(token);
                        cur = Monetary.getCurrency(subCurrency, providers);
                        context.consume(subCurrency);
                    } else {
                        cur = Monetary.getCurrency(token, providers);
                        context.consume(token);
                    }
                    break;
                case SYMBOL:
                    String symbol = extractLeadingSymbol(token);
                    String resolvedCode = resolveSymbol(symbol, providers);
                    cur = Monetary.getCurrency(resolvedCode, providers);
                    context.consume(symbol);
                    context.setParsedCurrency(cur);
                    break;
                case NAME:
                case NUMERIC_CODE:
                default:
                    throw new UnsupportedOperationException("Not yet implemented");
            }
            if (Objects.nonNull(cur)) {
                context.setParsedCurrency(cur);
            }else{
                CurrencyUnit fixed = this.context.get(CurrencyUnit.class);
                if(fixed!=null){
                    context.setParsedCurrency(fixed);
                }
            }
        } catch (MonetaryParseException e) {
            context.setError();
            context.setErrorMessage(e.getMessage());
            throw e;
        } catch (Exception e) {
            context.setError();
            context.setErrorMessage(e.getMessage());
            Logger.getLogger(getClass().getName()).log(FINEST, "Could not parse CurrencyUnit from \"" + token + "\"", e);
            throw new MonetaryParseException("Could not parse CurrencyUnit. " + e.getMessage(), token, -1);
        }
    }

    /**
     * Tries to split up a letter based first part, e.g. for evaluating a ISO currency code from an input as
     * 'CHF100.34'.
     * @param token the input token
     * @return the first letter based part, or the full token.
     */
    private String parseCurrencyCode(String token) {
        int letterIndex = 0;
        for (char ch : token.toCharArray()) {
            if (Character.isLetter(ch)) {
                letterIndex++;
            } else {
                return token.substring(0, letterIndex);
            }
        }
        return token;
    }

    /**
     * Prints the {@link CurrencyUnit} of the given {@link MonetaryAmount} to
     * the given {@link Appendable}.
     *
     * @throws IOException may be thrown by the {@link Appendable}
     */
    @Override
    public void print(Appendable appendable, MonetaryAmount amount)
            throws IOException {
        appendable.append(getToken(amount));
    }

    /**
     * Extracts the leading symbol segment from a token, stopping at the first digit,
     * sign, or locale-specific decimal/grouping separator.
     *
     * @param token the input token
     * @return the leading symbol portion
     */
    private String extractLeadingSymbol(String token) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
        char decimalSeparator = symbols.getDecimalSeparator();
        char groupingSeparator = symbols.getGroupingSeparator();

        int symbolEnd = 0;
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (Character.isDigit(ch) || ch == '+' || ch == '-' ||
                ch == decimalSeparator || ch == groupingSeparator) {
                break;
            }
            symbolEnd = i + 1;
        }

        return symbolEnd > 0 ? token.substring(0, symbolEnd) : token;
    }

    /**
     * Resolves a currency symbol to an ISO currency code using locale-aware precedence.
     *
     * @param symbol the currency symbol to resolve
     * @param providers the currency providers to use
     * @return the ISO currency code
     * @throws MonetaryParseException if the symbol cannot be resolved or is ambiguous
     */
    private String resolveSymbol(String symbol, String[] providers) {
        // Check explicit currency in context first
        CurrencyUnit explicitCurrency = this.context.get(CurrencyUnit.class);
        if (explicitCurrency != null) {
            String expectedSymbol = getCurrencySymbol(explicitCurrency);
            if (symbolMatches(symbol, expectedSymbol)) {
                return explicitCurrency.getCurrencyCode();
            } else {
                throw new MonetaryParseException(
                    "Expected symbol '" + expectedSymbol + "' for " +
                    explicitCurrency.getCurrencyCode() + " but found '" + symbol + "'.",
                    symbol, -1);
            }
        }

        // Check locale default currency
        try {
            Currency localeCurrency = Currency.getInstance(locale);
            if (localeCurrency != null && symbolMatches(symbol, localeCurrency.getSymbol(locale))) {
                return localeCurrency.getCurrencyCode();
            }
        } catch (IllegalArgumentException e) {
            // Locale may not have a default currency
        }

        // Scan all available currencies
        List<String> matches = findCurrenciesForSymbol(symbol);

        if (matches.isEmpty()) {
            throw new MonetaryParseException(
                "Cannot resolve currency symbol '" + symbol + "' for locale " + locale + ".",
                symbol, -1);
        } else if (matches.size() == 1) {
            return matches.get(0);
        } else {
            throw new MonetaryParseException(
                "'" + symbol + "' is ambiguous in locale " + locale +
                ". Possible currencies: " + String.join(", ", matches) + ".",
                symbol, -1);
        }
    }

    /**
     * Finds all currency codes whose symbols match the given symbol in the current locale.
     *
     * @param symbol the symbol to match
     * @return list of matching currency codes
     */
    private List<String> findCurrenciesForSymbol(String symbol) {
        List<String> matches = new ArrayList<>();
        for (Currency currency : Currency.getAvailableCurrencies()) {
            String currencySymbol = currency.getSymbol(locale);
            if (symbolMatches(symbol, currencySymbol)) {
                matches.add(currency.getCurrencyCode());
            }
        }
        return matches;
    }

    /**
     * Checks if two symbols match after normalization.
     * Strips whitespace and trailing punctuation, and allows bidirectional substring matches.
     * Handles both prefix ($US) and suffix (US$) forms.
     *
     * @param parsed the parsed symbol
     * @param candidate the candidate symbol to compare
     * @return true if the symbols match
     */
    private boolean symbolMatches(String parsed, String candidate) {
        String normalizedParsed = normalizeSymbol(parsed);
        String normalizedCandidate = normalizeSymbol(candidate);

        // Reject empty/missing symbols - a blank symbol cannot match anything
        if (normalizedParsed.isEmpty() || normalizedCandidate.isEmpty()) {
            return false;
        }

        // Exact match
        if (normalizedParsed.equals(normalizedCandidate)) {
            return true;
        }

        // Check if either ends with the other (for suffix forms like US$ matching $)
        // or starts with the other (for prefix forms like $US matching $)
        return normalizedParsed.endsWith(normalizedCandidate) ||
               normalizedCandidate.endsWith(normalizedParsed) ||
               normalizedParsed.startsWith(normalizedCandidate) ||
               normalizedCandidate.startsWith(normalizedParsed);
    }

    /**
     * Normalizes a currency symbol by removing whitespace and trailing punctuation.
     * Currency symbols (like $, €, ¥, £) are preserved.
     *
     * @param symbol the symbol to normalize
     * @return the normalized symbol
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }

        // Remove all Unicode whitespace
        String normalized = symbol.replaceAll("\\s+", "");

        // Remove trailing punctuation, but NOT currency symbols
        // Currency symbols are in the Currency Symbol category (Sc)
        while (!normalized.isEmpty()) {
            char lastChar = normalized.charAt(normalized.length() - 1);
            // Stop if it's a letter, digit, or currency symbol
            if (Character.isLetterOrDigit(lastChar) ||
                Character.getType(lastChar) == Character.CURRENCY_SYMBOL) {
                break;
            }
            // Remove trailing punctuation
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "CurrencyToken [locale=" + locale + ", style=" + style + ']';
    }

}
