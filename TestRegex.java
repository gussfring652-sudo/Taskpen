import java.util.regex.*;

public class TestRegex {
    public static void main(String[] args) {
        String text = "Comprar pan 10/10/26";
        Pattern p = Pattern.compile("(?i)\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");
        Matcher m = p.matcher(text);
        if (m.find()) {
            System.out.println("MATCHED: " + m.group(0));
            System.out.println("Day: " + m.group(1));
            System.out.println("Month: " + m.group(2));
            System.out.println("Year: " + m.group(3));
        } else {
            System.out.println("NO MATCH");
        }
    }
}
