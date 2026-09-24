package tests.common.dataprovider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Подстановка текущих дат в тестовые данные по плейсхолдерам вида {now_yyyy-MM-dd}:
 * "{now_" + pattern из java.time.format.DateTimeFormatter + "}" заменяется текущей датой/временем.
 * AGENTS.md: класс и метод созданы по аналогии с рабочим проектом (там DateFormatter
 * инстанцируется в JSONReader: new DateFormatter().changeString(json)).
 */
public class DateFormatter {

    private static final Pattern NOW_PLACEHOLDER = Pattern.compile("\\{now_(.+?)}");

    public String changeString(String json) {
        Matcher matcher = NOW_PLACEHOLDER.matcher(json);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
