package com.patient.io.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.HashMap;
import java.util.Map;

/**
 * 药品名拼音工具：全拼 + 首字母。
 * 多音字取第一个读音（药品名场景足够），字符级缓存避免重复转换开销。
 */
public final class PinyinUtil {

    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();
    private static final Map<Character, String[]> CACHE = new HashMap<>(1024);

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        // ü 先输出成 u:，再把冒号去掉，既区分 ü 与原生的 u，又符合用户实际输入习惯（luhuana 而非 lvhuana）
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_U_AND_COLON);
    }

    private PinyinUtil() {
    }

    /** 全拼，仅保留字母数字，如 注射用头孢曲松钠 -> zhusheyongtoubaoqusongna */
    public static String full(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() * 3);
        for (char ch : text.toCharArray()) {
            appendLetters(sb, readings(ch));
        }
        return sb.toString();
    }

    /** 首字母，如 注射用头孢曲松钠 -> zsytbqsn */
    public static String initials(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            String[] arr = readings(ch);
            if (arr != null) {
                if (!arr[0].isEmpty()) {
                    sb.append(arr[0].charAt(0));
                }
            } else if (isAsciiLetterOrDigit(ch)) {
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }

    private static void appendLetters(StringBuilder sb, String[] arr) {
        if (arr != null) {
            sb.append(arr[0].replace("u:", "u"));
        }
    }

    private static String[] readings(char ch) {
        if (ch < 128) {
            if (isAsciiLetterOrDigit(ch)) {
                return new String[]{String.valueOf(Character.toLowerCase(ch))};
            }
            return null;
        }
        String[] cached = CACHE.get(ch);
        if (cached != null) {
            return cached;
        }
        String[] result;
        try {
            result = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            result = null;
        }
        if (result == null || result.length == 0) {
            result = null;
        }
        CACHE.put(ch, result);
        return result;
    }

    private static boolean isAsciiLetterOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
    }
}
