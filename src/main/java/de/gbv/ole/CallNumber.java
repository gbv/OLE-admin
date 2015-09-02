package de.gbv.ole;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class CallNumber {
    private static final Pattern pattern = Pattern.compile(
            "[^A-Z0-9]+|(?<=[A-Z])(?=[0-9])|(?<=[0-9])(?=[A-Z])");
    
    /**
     * Prevent Instantiation.
     */
    private CallNumber() {
    }
    
    public static String getShelvingOrder(String callNumber) {
        String [] chunk = pattern.split(callNumber.toUpperCase());
        StringBuffer shelvingOrder = new StringBuffer();
        for (String s : chunk) {
            if (shelvingOrder.length() > 0) {
                shelvingOrder.append(' ');
            }
            if (StringUtils.isNumeric(s)) {
                shelvingOrder.append(StringUtils.leftPad(s, 5, '0'));
            } else {
                shelvingOrder.append(StringUtils.rightPad(s, 5));
            }
        }
        return shelvingOrder.toString();
    }
}
