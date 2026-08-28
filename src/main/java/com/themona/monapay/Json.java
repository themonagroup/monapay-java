package com.themona.monapay;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON codec for the SDK's map/list API; intentionally has no dependencies. */
final class Json {
    private Json() {}

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            quote(String.valueOf(value), out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isInfinite(number) || Double.isNaN(number)) {
                throw new IllegalArgumentException("JSON không hỗ trợ NaN/Infinity");
            }
            out.append(value);
        } else if (value instanceof Map<?, ?>) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?>) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) out.append(',');
                first = false;
                write(item, out);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) out.append(',');
                write(Array.get(value, i), out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Chỉ hỗ trợ JSON primitive, Map, Iterable và array: " + value.getClass());
        }
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (parser.index != source.length()) throw parser.error("ký tự thừa");
        return value;
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }

        private Object value() {
            space();
            if (index >= source.length()) throw error("thiếu giá trị");
            char c = source.charAt(index);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't') { literal("true"); return Boolean.TRUE; }
            if (c == 'f') { literal("false"); return Boolean.FALSE; }
            if (c == 'n') { literal("null"); return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw error("giá trị không hợp lệ");
        }

        private Map<String, Object> object() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            index++;
            space();
            if (take('}')) return result;
            while (true) {
                space();
                if (index >= source.length() || source.charAt(index) != '"') throw error("object key phải là string");
                String key = string();
                space();
                if (!take(':')) throw error("thiếu ':'");
                result.put(key, value());
                space();
                if (take('}')) return result;
                if (!take(',')) throw error("thiếu ',' hoặc '}'");
            }
        }

        private List<Object> array() {
            ArrayList<Object> result = new ArrayList<>();
            index++;
            space();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                space();
                if (take(']')) return result;
                if (!take(',')) throw error("thiếu ',' hoặc ']'");
            }
        }

        private String string() {
            index++;
            StringBuilder result = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return result.toString();
                if (c == '\\') {
                    if (index >= source.length()) throw error("escape bị thiếu");
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        case '/': result.append('/'); break;
                        case 'b': result.append('\b'); break;
                        case 'f': result.append('\f'); break;
                        case 'n': result.append('\n'); break;
                        case 'r': result.append('\r'); break;
                        case 't': result.append('\t'); break;
                        case 'u':
                            if (index + 4 > source.length()) throw error("unicode escape bị thiếu");
                            try { result.append((char) Integer.parseInt(source.substring(index, index + 4), 16)); }
                            catch (NumberFormatException e) { throw error("unicode escape không hợp lệ"); }
                            index += 4;
                            break;
                        default: throw error("escape không hợp lệ");
                    }
                } else {
                    if (c < 0x20) throw error("control character trong string");
                    result.append(c);
                }
            }
            throw error("string chưa đóng");
        }

        private Number number() {
            int start = index;
            if (take('-') && index >= source.length()) throw error("number không hợp lệ");
            if (take('0')) {
                // A leading zero may not be followed by another digit.
                if (index < source.length() && Character.isDigit(source.charAt(index))) throw error("number không hợp lệ");
            } else {
                digits();
            }
            boolean decimal = false;
            if (take('.')) { decimal = true; digits(); }
            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                digits();
            }
            String text = source.substring(start, index);
            try { return decimal ? Double.valueOf(text) : Long.valueOf(text); }
            catch (NumberFormatException error) { throw error("number không hợp lệ"); }
        }

        private void digits() {
            int start = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            if (start == index) throw error("thiếu chữ số");
        }

        private void literal(String expected) {
            if (!source.startsWith(expected, index)) throw error("literal không hợp lệ");
            index += expected.length();
        }

        private void space() {
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return;
                index++;
            }
        }

        private boolean take(char expected) {
            if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("JSON không hợp lệ tại vị trí " + index + ": " + message);
        }
    }
}
