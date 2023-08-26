package org.vicary.pattern;

import java.util.Arrays;

public class TwitterPattern {

    public static boolean checkURLValidation(String twitterURL) {
        return twitterURL.contains("twitter.com/");
    }

    public static String getURL(String text) {
        return Arrays.stream(text.split(" ")).findFirst().orElse("");
    }
}