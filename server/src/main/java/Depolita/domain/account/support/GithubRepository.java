package Depolita.domain.account.support;

import Depolita.domain.account.exception.InvalidRepositoryUrlException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record GithubRepository(String owner, String name) {
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https://github\\.com/([\\w-]+)/([\\w.-]+?)(\\.git)?/?$",
                    Pattern.CASE_INSENSITIVE);
    public static GithubRepository parse(String url) {
        if (url == null) {
            throw new InvalidRepositoryUrlException();
        }
        Matcher matcher = URL_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            throw new InvalidRepositoryUrlException();
        }
        String name = matcher.group(2);
        if (name.equals(".") || name.equals("..")) {
            throw new InvalidRepositoryUrlException();
        }
        return new GithubRepository(matcher.group(1).toLowerCase(), name);
    }
    public String cloneUrl() {
        return "https://github.com/" + owner + "/" + name + ".git";
    }
}
